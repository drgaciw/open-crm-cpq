package com.opencrm.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BaseEntity Tests")
class BaseEntityTest {

    // Concrete subclass for testing the abstract BaseEntity
    private static class TestEntity extends BaseEntity {
        // No additional properties needed for these tests
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {
        @Test
        @DisplayName("should generate UUID on create if null")
        void shouldGenerateUuidOnCreateIfNull() {
            TestEntity entity = new TestEntity();
            assertNull(entity.getUuid(), "UUID should be null initially");

            entity.onCreate();

            assertNotNull(entity.getUuid(), "UUID should be generated");
            assertDoesNotThrow(() -> UUID.fromString(entity.getUuid()), "Generated UUID should be valid");
        }

        @Test
        @DisplayName("should not overwrite existing UUID on create")
        void shouldNotOverwriteExistingUuidOnCreate() {
            TestEntity entity = new TestEntity();
            String existingUuid = UUID.randomUUID().toString();
            entity.setUuid(existingUuid);

            entity.onCreate();

            assertEquals(existingUuid, entity.getUuid(), "Existing UUID should not be overwritten");
        }

        @Test
        @DisplayName("should set deleted flag on soft delete")
        void shouldSetDeletedFlagOnSoftDelete() {
            TestEntity entity = new TestEntity();
            assertFalse(entity.isDeleted(), "Entity should not be deleted initially");

            entity.softDelete();

            assertTrue(entity.isDeleted(), "Entity should be marked as deleted");
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {
        @Test
        @DisplayName("equals should return true for same instance")
        void equalsShouldReturnTrueForSameInstance() {
            TestEntity entity = new TestEntity();
            entity.setUuid(UUID.randomUUID().toString());

            assertEquals(entity, entity, "Entity should be equal to itself");
        }

        @Test
        @DisplayName("equals should return false for null")
        void equalsShouldReturnFalseForNull() {
            TestEntity entity = new TestEntity();
            entity.setUuid(UUID.randomUUID().toString());

            assertNotEquals(null, entity, "Entity should not be equal to null");
        }

        @Test
        @DisplayName("equals should return false for different class")
        void equalsShouldReturnFalseForDifferentClass() {
            TestEntity entity = new TestEntity();
            entity.setUuid(UUID.randomUUID().toString());

            assertNotEquals(entity, new Object(), "Entity should not be equal to object of different class");
        }

        @Test
        @DisplayName("equals should return true for different instances with same UUID")
        void equalsShouldReturnTrueForDifferentInstancesWithSameUuid() {
            String uuid = UUID.randomUUID().toString();
            TestEntity entity1 = new TestEntity();
            entity1.setUuid(uuid);

            TestEntity entity2 = new TestEntity();
            entity2.setUuid(uuid);

            assertEquals(entity1, entity2, "Entities with same UUID should be equal");
        }

        @Test
        @DisplayName("equals should return false for different instances with different UUIDs")
        void equalsShouldReturnFalseForDifferentInstancesWithDifferentUuids() {
            TestEntity entity1 = new TestEntity();
            entity1.setUuid(UUID.randomUUID().toString());

            TestEntity entity2 = new TestEntity();
            entity2.setUuid(UUID.randomUUID().toString());

            assertNotEquals(entity1, entity2, "Entities with different UUIDs should not be equal");
        }

        @Test
        @DisplayName("hashCode should be equal for entities with same UUID")
        void hashCodeShouldBeEqualForEntitiesWithSameUuid() {
            String uuid = UUID.randomUUID().toString();
            TestEntity entity1 = new TestEntity();
            entity1.setUuid(uuid);

            TestEntity entity2 = new TestEntity();
            entity2.setUuid(uuid);

            assertEquals(entity1.hashCode(), entity2.hashCode(), "Hash codes should match for same UUID");
        }
    }
}
