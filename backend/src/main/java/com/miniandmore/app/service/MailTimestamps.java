package com.miniandmore.app.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.slf4j.Logger;

/**
 * Formats mail timestamps in Indian Standard Time regardless of the JVM/server zone.
 */
final class MailTimestamps {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy, h:mm a 'IST'", Locale.ENGLISH);
    private static final DateTimeFormatter RFC_2822 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private MailTimestamps() {}

    static String now() {
        return format(Instant.now());
    }

    static String format(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DISPLAY.format(instant.atZone(IST));
    }

    static String formatIfInstant(Object value) {
        Instant instant = asInstant(value);
        return instant == null ? null : format(instant);
    }

    static String rfc2822Now() {
        return RFC_2822.format(ZonedDateTime.now(IST));
    }

    static void stampSentDate(MimeMessage mimeMessage, Logger log) {
        try {
            mimeMessage.setHeader("Date", rfc2822Now());
        } catch (MessagingException e) {
            if (log != null) {
                log.debug("Could not stamp IST Date header", e);
            }
        }
    }

    static Instant asInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant();
        }
        if (value instanceof String text) {
            return parseInstant(text.trim());
        }
        return null;
    }

    private static Instant parseInstant(String text) {
        if (text.isEmpty() || !looksLikeInstant(text)) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static boolean looksLikeInstant(String text) {
        return text.indexOf('T') >= 0 || text.endsWith("Z") || text.contains("+") || (text.contains("-") && text.length() > 10);
    }
}
