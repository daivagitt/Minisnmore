package com.miniandmore.gateway.security;

public final class DataIntegrityErrors {

    private DataIntegrityErrors() {}

    public static boolean isDuplicateKey(Throwable error, String... constraints) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null || message.isBlank()) {
                continue;
            }
            if (message.contains("duplicate key value")) {
                if (constraints.length == 0) {
                    return true;
                }
                for (String constraint : constraints) {
                    if (message.contains(constraint)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isDuplicateUserId(Throwable error) {
        return isDuplicateKey(error, "jhi_user_pkey");
    }

    public static boolean isDuplicateUserEmail(Throwable error) {
        return isDuplicateKey(error, "ux_user_email");
    }

    public static boolean isDuplicateUserLogin(Throwable error) {
        return isDuplicateKey(error, "ux_user_login");
    }

    public static boolean isDuplicateUserIdentity(Throwable error) {
        return isDuplicateUserId(error) || isDuplicateUserEmail(error) || isDuplicateUserLogin(error);
    }
}
