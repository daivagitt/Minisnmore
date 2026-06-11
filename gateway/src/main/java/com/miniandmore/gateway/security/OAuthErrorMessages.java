package com.miniandmore.gateway.security;

final class OAuthErrorMessages {

    private OAuthErrorMessages() {}

    static String friendly(Throwable error) {
        if (error == null) {
            return "Social sign-in failed. Please try again.";
        }
        var message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "Social sign-in failed. Please try again.";
        }
        if (DataIntegrityErrors.isDuplicateUserIdentity(error)) {
            return "We could not create your account right now. Please try again in a moment.";
        }
        if (message.contains("value too long") || message.contains("character varying(50)")) {
            return "Your email address is too long for sign-in. Contact support or use a shorter email.";
        }
        if (message.contains("OAuth provider did not return an email")) {
            return "Your social account did not share an email address. Use email sign-up instead.";
        }
        if (message.contains("executeMany") || message.contains("SQL [INSERT")) {
            return "Account sign-in failed due to a server error. Please try again.";
        }
        if (message.contains("cannot be cast to class [C")) {
            return "Social sign-in failed. Please try again.";
        }
        return message;
    }
}
