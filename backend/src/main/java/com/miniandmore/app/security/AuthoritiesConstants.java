package com.miniandmore.app.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    public static final String ADMIN = "ROLE_ADMIN";

    public static final String SALES = "ROLE_SALES";

    public static final String OPERATIONS = "ROLE_OPERATIONS";

    public static final String CUSTOMER_SUPPORT = "ROLE_CUSTOMER_SUPPORT";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    private AuthoritiesConstants() {}
}
