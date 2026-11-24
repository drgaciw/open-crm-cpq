package com.opencrm.authorization.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAssignmentMapperRegistrationTest {

    @Test
    void registersExactlyOneRoleAssignmentMapperThroughComponentScanning() {
        try (var context = new AnnotationConfigApplicationContext(RoleAssignmentMapper.class.getPackageName())) {
            assertThat(context.getBeansOfType(RoleAssignmentMapper.class)).hasSize(1);
        }
    }
}
