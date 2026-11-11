package com.opencrm.ai.boundary;

import com.opencrm.ai.audit.entity.AiAuditLog;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
  void mainSourcesDeclareOnlyAiServiceEntities() throws IOException {
    Path aiSources = Path.of("src/main/java/com/opencrm/ai");
    List<Path> entitySources;
    try (Stream<Path> paths = Files.walk(aiSources)) {
      entitySources = paths
          .filter(path -> path.toString().endsWith(".java"))
          .filter(this::declaresEntity)
          .toList();
    }

    assertEquals(
        List.of("AgentTask.java", "AiAuditLog.java"),
        entitySources.stream()
            .map(path -> path.getFileName().toString())
            .sorted()
            .toList());
  }

  @Test
  void quoteServicePackagesAreNotImportedByAiService() throws IOException {
    assertNoImportsFrom("com.opencrm.quote", "quote-service");
  }

  @Test
  void crmServicePackagesAreNotImportedByAiService() throws IOException {
    assertNoImportsFrom("com.opencrm.crm", "crm-service");
  }

  @Test
  void approvalServicePackagesAreNotImportedByAiService() throws IOException {
    assertNoImportsFrom("com.opencrm.approval", "approval-routing");
  }

  @Test
  void commercialAggregateRepositoriesAreNotDeclaredInAiService() {
    assertFalse(classExists("com.opencrm.ai.domain.QuoteRepository"));
    assertFalse(classExists("com.opencrm.ai.domain.DealRepository"));
    assertFalse(classExists("com.opencrm.ai.domain.ApprovalRequestRepository"));
  }

  private boolean classExists(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException ex) {
      return false;
    }
  }

  private void assertNoImportsFrom(String forbiddenPackage, String serviceModule) throws IOException {
    Path servicePackage = Path.of("../" + serviceModule + "/src/main/java/" + forbiddenPackage.replace('.', '/'));
    assertTrue(Files.isDirectory(servicePackage),
        "precondition: " + servicePackage + " must exist so the import check is not vacuous");

    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
      for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
          String line = lines.get(i).trim();
          if (line.startsWith("import " + forbiddenPackage + ".")
              || line.startsWith("import static " + forbiddenPackage + ".")) {
            violations.add(file + ":" + (i + 1) + ": " + line);
          }
        }
      }
    }
    assertTrue(violations.isEmpty(),
        () -> "ai-service main sources must not import " + forbiddenPackage + ".*: " + violations);
  }

  private boolean declaresEntity(Path sourceFile) {
    try {
      String source = Files.readString(sourceFile);
      return source.contains("@Entity");
    } catch (IOException ex) {
      return false;
    }
  }
}
