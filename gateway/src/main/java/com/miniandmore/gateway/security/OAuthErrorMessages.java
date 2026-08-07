package com.miniandmore.gateway.security;

final class OAuthErrorMessages {

    private OAuthErrorMessages() {}

    static String friendly(Throwable error) {
        if (error == null) {
            return "Google or social sign-in failed. Please try again, or create an account with email.";
        }

        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null || message.isBlank()) {
                continue;
            }
            String mapped = mapMessage(message);
            if (mapped != null) {
                return mapped;
            }
        }

        return "Social sign-in failed. Please try again, or create an account with email.";
    }

    private static String mapMessage(String message) {
        String lower = message.toLowerCase();

        if (
            lower.contains("duplicate key") ||
            lower.contains("ux_user_email") ||
            lower.contains("ux_user_login") ||
            lower.contains("jhi_user_pkey")
        ) {
            return "An account with this email already exists. Sign in with email and password, or try Continue with Google again.";
        }
        if (lower.contains("unique result") || lower.contains("incorrectresultsize") || lower.contains("non unique")) {
            return "We found more than one matching account for this email. Contact support so we can fix it.";
        }
        if (message.contains("OAuth provider did not return an email") || lower.contains("did not return an email")) {
            return "Your social account did not share an email address. Allow email access, or create an account with email instead.";
        }
        if (message.contains("value too long") || message.contains("character varying(50)")) {
            return "Your email address is too long for sign-in. Use a shorter email or contact support.";
        }
        if (lower.contains("created_by") || lower.contains("null value") || lower.contains("not-null")) {
            return "We could not create your account right now. Please try again in a moment.";
        }
        if (
            lower.contains("executemany") ||
            lower.contains("sql [") ||
            lower.contains("data access") ||
            lower.contains("jdbc") ||
            lower.contains("r2dbc") ||
            lower.contains("psql") ||
            lower.contains("org.springframework") ||
            lower.contains("java.") ||
            lower.contains("cannot be cast") ||
            lower.contains("stacktrace")
        ) {
            return "Account sign-in failed due to a server error. Please try again in a moment.";
        }
        // Already a short human message — keep it.
        if (message.length() <= 180 && !looksTechnical(message)) {
            return message;
        }
        return null;
    }

    private static boolean looksTechnical(String message) {
        String lower = message.toLowerCase();
        return lower.contains("exception") ||
            lower.contains("at com.") ||
            lower.contains("at org.") ||
            lower.contains("sqlstate") ||
            message.contains("\n") ||
            message.contains("\t");
    }
}
