package com.miniandmore.gateway.security;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fixed Phase 1 staff roles and hierarchy:
 * Super Admin → Admin → Sales / Operations / Customer Support.
 */
public final class StaffRoles {

    public static final List<String> ALL = List.of(
        AuthoritiesConstants.SUPER_ADMIN,
        AuthoritiesConstants.ADMIN,
        AuthoritiesConstants.SALES,
        AuthoritiesConstants.OPERATIONS,
        AuthoritiesConstants.CUSTOMER_SUPPORT
    );

    private StaffRoles() {}

    public static Set<String> names(User user) {
        if (user == null || user.getAuthorities() == null) {
            return Set.of();
        }
        return user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet());
    }

    public static boolean isStaff(Collection<String> authorities) {
        return authorities != null && authorities.stream().anyMatch(ALL::contains);
    }

    public static boolean isCmsAdmin(Collection<String> authorities) {
        return contains(authorities, AuthoritiesConstants.SUPER_ADMIN) || contains(authorities, AuthoritiesConstants.ADMIN);
    }

    public static String primaryRole(Collection<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return null;
        }
        if (contains(authorities, AuthoritiesConstants.SUPER_ADMIN)) {
            return AuthoritiesConstants.SUPER_ADMIN;
        }
        if (contains(authorities, AuthoritiesConstants.ADMIN)) {
            return AuthoritiesConstants.ADMIN;
        }
        if (contains(authorities, AuthoritiesConstants.SALES)) {
            return AuthoritiesConstants.SALES;
        }
        if (contains(authorities, AuthoritiesConstants.OPERATIONS)) {
            return AuthoritiesConstants.OPERATIONS;
        }
        if (contains(authorities, AuthoritiesConstants.CUSTOMER_SUPPORT)) {
            return AuthoritiesConstants.CUSTOMER_SUPPORT;
        }
        return null;
    }

    public static Set<String> canonicalAuthorities(String primaryRole) {
        if (AuthoritiesConstants.SUPER_ADMIN.equals(primaryRole)) {
            return Set.of(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN);
        }
        if (ALL.contains(primaryRole)) {
            return Set.of(primaryRole);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A staff role is required");
    }

    public static String displayName(String role) {
        if (AuthoritiesConstants.SUPER_ADMIN.equals(role)) {
            return "Super Admin";
        }
        if (AuthoritiesConstants.ADMIN.equals(role)) {
            return "Admin";
        }
        if (AuthoritiesConstants.SALES.equals(role)) {
            return "Sales";
        }
        if (AuthoritiesConstants.OPERATIONS.equals(role)) {
            return "Operations";
        }
        if (AuthoritiesConstants.CUSTOMER_SUPPORT.equals(role)) {
            return "Customer Support";
        }
        return role;
    }

    public static int rank(Collection<String> authorities) {
        String role = primaryRole(authorities);
        if (AuthoritiesConstants.SUPER_ADMIN.equals(role)) {
            return 3;
        }
        if (AuthoritiesConstants.ADMIN.equals(role)) {
            return 2;
        }
        if (role != null) {
            return 1;
        }
        return 0;
    }

    public static boolean canAssign(Collection<String> actorAuthorities, String targetRole) {
        if (!ALL.contains(targetRole)) {
            return false;
        }
        if (contains(actorAuthorities, AuthoritiesConstants.SUPER_ADMIN)) {
            return true;
        }
        if (contains(actorAuthorities, AuthoritiesConstants.ADMIN)) {
            return rank(Set.of(targetRole)) == 1;
        }
        return false;
    }

    public static boolean canManage(Collection<String> actorAuthorities, Collection<String> targetAuthorities) {
        if (contains(actorAuthorities, AuthoritiesConstants.SUPER_ADMIN)) {
            return isStaff(targetAuthorities) || targetAuthorities == null || targetAuthorities.isEmpty();
        }
        if (contains(actorAuthorities, AuthoritiesConstants.ADMIN)) {
            return rank(targetAuthorities) == 1;
        }
        return false;
    }

    public static void requireAssignable(Collection<String> actorAuthorities, Collection<String> requestedAuthorities) {
        String role = primaryRole(requestedAuthorities);
        if (role == null || !canAssign(actorAuthorities, role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot assign that role");
        }
    }

    public static void requireCanManage(Collection<String> actorAuthorities, Collection<String> targetAuthorities) {
        if (!canManage(actorAuthorities, targetAuthorities)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot manage that user");
        }
    }

    public static Set<Authority> toAuthorities(Collection<String> names) {
        Set<Authority> authorities = new LinkedHashSet<>();
        if (names == null) {
            return authorities;
        }
        for (String name : names) {
            Authority authority = new Authority();
            authority.setName(name);
            authorities.add(authority);
        }
        return authorities;
    }

    private static boolean contains(Collection<String> authorities, String value) {
        return authorities != null && authorities.contains(value);
    }
}
