package com.opencrm.crm.merge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class OrganizationReferenceGuardIntegrationTest {
    @Test void childWritesWaitForMergeAndRejectDeletedTarget() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            try (var setup = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var statement = setup.createStatement()) {
                statement.execute("create table organizations(id bigint primary key, is_deleted boolean not null)");
                for (String table : new String[]{"contacts", "leads", "deals"})
                    statement.execute("create table " + table + "(id bigint primary key, organization_id bigint, note text default '')");
                statement.execute("insert into organizations values (1, false), (2, false)");
                try (var resource = getClass().getResourceAsStream("/db/migration/V11__guard_organization_references.sql")) {
                    statement.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            for (String table : new String[]{"contacts", "leads", "deals"}) {
                try (var setup = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var st = setup.createStatement()) {
                    st.execute("update organizations set is_deleted=false where id=1");
                    st.execute("insert into " + table + "(id,organization_id) values (5,1)");
                }
                try (var merge = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var child = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var pool = Executors.newSingleThreadExecutor()) {
                    merge.setAutoCommit(false);
                    merge.createStatement().execute("select id from organizations where id=1 for update");
                    var future = pool.submit(() -> {
                        try { child.createStatement().execute("insert into " + table + " values (1, 1)"); return false; }
                        catch (java.sql.SQLException expected) { return "23514".equals(expected.getSQLState()); }
                    });
                    assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                    merge.createStatement().execute("update organizations set is_deleted=true where id=1");
                    merge.commit();
                    assertThat(future.get(5, TimeUnit.SECONDS)).isTrue();
                    child.createStatement().execute("update " + table + " set note='legacy update', organization_id=organization_id where id=5");
                    assertThatThrownBy(() -> child.createStatement().execute("insert into " + table + " values (2, 1)"))
                            .isInstanceOf(java.sql.SQLException.class);
                    child.createStatement().execute("insert into " + table + " values (3, 2)");
                    child.createStatement().execute("update " + table + " set note='active update' where id=3");
                    child.setAutoCommit(false);
                    child.createStatement().execute("insert into " + table + " values (4, 2)");
                    var waitingMerge = pool.submit(() -> {
                        try (var lock = merge.createStatement()) {
                            lock.execute("select id from organizations where id=2 for no key update");
                            try (var rows = lock.executeQuery("select count(*) from " + table + " where organization_id=2")) {
                                rows.next(); return rows.getLong(1);
                            }
                        }
                    });
                    assertThatThrownBy(() -> waitingMerge.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                    child.commit();
                    assertThat(waitingMerge.get(5, TimeUnit.SECONDS)).isEqualTo(2L);
                    merge.commit();
                }
            }
        }
    }
}
