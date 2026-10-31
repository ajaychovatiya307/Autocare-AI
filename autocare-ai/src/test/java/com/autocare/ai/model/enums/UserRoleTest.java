package com.autocare.ai.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleTest {

    @Test
    void hasExactlyThreeRolesInExpectedOrder() {
        assertThat(UserRole.values()).containsExactly(UserRole.OWNER, UserRole.ADVISOR, UserRole.ADMIN);
    }

    @Test
    void valueOfResolvesEachRoleByName() {
        assertThat(UserRole.valueOf("OWNER")).isEqualTo(UserRole.OWNER);
        assertThat(UserRole.valueOf("ADVISOR")).isEqualTo(UserRole.ADVISOR);
        assertThat(UserRole.valueOf("ADMIN")).isEqualTo(UserRole.ADMIN);
    }
}
