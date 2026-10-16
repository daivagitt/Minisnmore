package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class StaffRolesTest {

    @Test
    void names_nullUser_returnsEmptySet() {
        assertThat(StaffRoles.names(null)).isEmpty();
    }

    @Test
    void primaryRole_emptyAuthorities_returnsNull() {
        assertThat(StaffRoles.primaryRole(java.util.List.of())).isNull();
    }

    @Test
    void names_userWithNullAuthorities_returnsEmptySet() {
        User user = new User();
        user.setAuthorities(null);
        assertThat(StaffRoles.names(user)).isEmpty();
    }

    @Test
    void isStaff_nullAuthorities_returnsFalse() {
        assertThat(StaffRoles.isStaff(null)).isFalse();
    }

    @Test
    void isCmsAdmin_falseForNonAdminStaff() {
        assertThat(StaffRoles.isCmsAdmin(Set.of(AuthoritiesConstants.OPERATIONS))).isFalse();
    }

    @Test
    void primaryRole_customerSupport_returnsCustomerSupport() {
        assertThat(StaffRoles.primaryRole(Set.of(AuthoritiesConstants.CUSTOMER_SUPPORT)))
            .isEqualTo(AuthoritiesConstants.CUSTOMER_SUPPORT);
    }

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

    @Test
    void salesRoleIsStaffButNotAssignableBySalesActor() {
        assertThat(StaffRoles.isStaff(Set.of(AuthoritiesConstants.SALES))).isTrue();
        assertThat(StaffRoles.canAssign(Set.of(AuthoritiesConstants.SALES), AuthoritiesConstants.ADMIN)).isFalse();
    }

    @Test
    void primaryRole_picksHighestPrivilegeWhenMultipleProvided() {
        assertThat(StaffRoles.primaryRole(Set.of(AuthoritiesConstants.SALES, AuthoritiesConstants.ADMIN)))
            .isEqualTo(AuthoritiesConstants.ADMIN);
    }

    @Test
    void displayName_mapsKnownStaffRoles() {
        assertThat(StaffRoles.displayName(AuthoritiesConstants.OPERATIONS)).isEqualTo("Operations");
        assertThat(StaffRoles.displayName(AuthoritiesConstants.CUSTOMER_SUPPORT)).isEqualTo("Customer Support");
        assertThat(StaffRoles.displayName("custom-role")).isEqualTo("custom-role");
    }

    @Test
    void canonicalAuthorities_rejectsUnknownRole() {
        assertThatThrownBy(() -> StaffRoles.canonicalAuthorities("ROLE_UNKNOWN"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void requireCanManage_blocksAdminFromManagingPeerAdmin() {
        assertThatThrownBy(() ->
            StaffRoles.requireCanManage(Set.of(AuthoritiesConstants.ADMIN), Set.of(AuthoritiesConstants.ADMIN))
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void toAuthorities_nullNames_returnsEmptySet() {
        assertThat(StaffRoles.toAuthorities(null)).isEmpty();
    }

    @Test
    void rank_unknownRole_returnsZero() {
        assertThat(StaffRoles.rank(Set.of("ROLE_USER"))).isZero();
    }

    @Test
    void toAuthorities_buildsAuthorityEntities() {
        assertThat(StaffRoles.toAuthorities(Set.of(AuthoritiesConstants.SALES)))
            .extracting(Authority::getName)
            .containsExactly(AuthoritiesConstants.SALES);
        assertThat(StaffRoles.toAuthorities(null)).isEmpty();
    }

    @Test
    void namesAndRank_coverNullAndStaffHierarchy() {
        assertThat(StaffRoles.names(null)).isEmpty();
        com.miniandmore.gateway.domain.User user = new com.miniandmore.gateway.domain.User();
        user.setAuthorities(null);
        assertThat(StaffRoles.names(user)).isEmpty();

        assertThat(StaffRoles.rank(Set.of(AuthoritiesConstants.SUPER_ADMIN))).isEqualTo(3);
        assertThat(StaffRoles.rank(Set.of(AuthoritiesConstants.ADMIN))).isEqualTo(2);
        assertThat(StaffRoles.rank(Set.of(AuthoritiesConstants.OPERATIONS))).isEqualTo(1);
        assertThat(StaffRoles.rank(Set.of("ROLE_USER"))).isZero();
        assertThat(StaffRoles.primaryRole(Set.of())).isNull();
    }

    @Test
    void canManage_superAdminAllowsEmptyOrStaffTargets() {
        Set<String> superAdmin = Set.of(AuthoritiesConstants.SUPER_ADMIN);
        assertThat(StaffRoles.canManage(superAdmin, null)).isTrue();
        assertThat(StaffRoles.canManage(superAdmin, Set.of())).isTrue();
        assertThat(StaffRoles.displayName(AuthoritiesConstants.CUSTOMER_SUPPORT)).isEqualTo("Customer Support");
        assertThat(StaffRoles.canonicalAuthorities(AuthoritiesConstants.SALES)).containsExactly(AuthoritiesConstants.SALES);
    }
}
