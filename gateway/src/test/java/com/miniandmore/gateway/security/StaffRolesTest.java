package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class StaffRolesTest {

    @Test
    void adminCannotAssignAdminOrSuperAdmin() {
        Set<String> admin = Set.of(AuthoritiesConstants.ADMIN);
        assertThat(StaffRoles.canAssign(admin, AuthoritiesConstants.SALES)).isTrue();
        assertThat(StaffRoles.canAssign(admin, AuthoritiesConstants.ADMIN)).isFalse();
        assertThat(StaffRoles.canAssign(admin, AuthoritiesConstants.SUPER_ADMIN)).isFalse();
        assertThatThrownBy(() -> StaffRoles.requireAssignable(admin, Set.of(AuthoritiesConstants.ADMIN)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void superAdminCanAssignAnyStaffRole() {
        Set<String> superAdmin = Set.of(AuthoritiesConstants.SUPER_ADMIN);
        assertThat(StaffRoles.canAssign(superAdmin, AuthoritiesConstants.ADMIN)).isTrue();
        assertThat(StaffRoles.canManage(superAdmin, Set.of(AuthoritiesConstants.ADMIN))).isTrue();
    }

    @Test
    void adminCanManageOnlyLowerRoles() {
        Set<String> admin = Set.of(AuthoritiesConstants.ADMIN);
        assertThat(StaffRoles.canManage(admin, Set.of(AuthoritiesConstants.SALES))).isTrue();
        assertThat(StaffRoles.canManage(admin, Set.of(AuthoritiesConstants.ADMIN))).isFalse();
    }

    @Test
    void superAdminCanonicalIncludesAdmin() {
        assertThat(StaffRoles.canonicalAuthorities(AuthoritiesConstants.SUPER_ADMIN))
            .containsExactlyInAnyOrder(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN);
    }
}
