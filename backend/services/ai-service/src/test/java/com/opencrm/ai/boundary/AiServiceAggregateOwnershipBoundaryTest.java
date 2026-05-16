package com.opencrm.ai.boundary;

import com.opencrm.ai.audit.entity.AiAuditLog;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceAggregateOwnershipBoundaryTest {

  @Test
  void auditLogEntityMapsToAiSchemaOnly() {
    Table table = AiAuditLog.class.getAnnotation(Table.class);
    assertNotNull(table);
    assertEquals("agent_audit_log", table.name());
    assertEquals("ai", table.schema());
  }

  @Test
  void mainSourcesDeclareOnlyAiAuditLogEntity() throws IOException {
    Path aiSources = Path.of("src/main/java/com/opencrm/ai");
    List<Path> entitySources;
    try (Stream<Path> paths = Files.walk(aiSources)) {
      entitySources = paths
          .filter(path -> path.toString().endsWith(".java"))
          .filter(this::declaresEntity)
          .toList();
    }

    assertEquals(1, entitySources.size());
    assertTrue(entitySources.get(0).toString().replace('\\', '/').endsWith("AiAuditLog.java"));
  }

  @Test
  void commercialAggregateRepositoriesAreNotDeclaredInAiService() {
    assertFalse(classExists("com.opencrm.ai.domain.QuoteRepository"));
    assertFalse(classExists("com.opencrm.ai.domain.DealRepository"));
    assertFalse(classExists("com.opencrm.ai.domain.ApprovalRequestRepository"));
  }

  private boolean declaresEntity(Path sourceFile) {
    try {
      String source = Files.readString(sourceFile);
      return source.contains("@Entity");
    } catch (IOException ex) {
      return false;
    }
  }

  private boolean classExists(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException ex) {
      return false;
    }
  }
}
