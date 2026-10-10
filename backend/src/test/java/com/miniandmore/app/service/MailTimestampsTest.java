package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class MailTimestampsTest {

    @Test
    void format_convertsUtcInstantToIstClockAndLabel() {
        Instant utcNoon = Instant.parse("2026-10-02T06:00:00Z");

        assertThat(MailTimestamps.format(utcNoon)).isEqualTo("Fri, 2 Oct 2026, 11:30 AM IST");
        assertThat(MailTimestamps.format(null)).isEmpty();
    }

    @Test
    void formatIfInstant_acceptsIsoUtcStringsFromJackson() {
        assertThat(MailTimestamps.formatIfInstant("2026-10-31T18:30:00Z")).isEqualTo("Sun, 1 Nov 2026, 12:00 AM IST");
        assertThat(MailTimestamps.formatIfInstant("not-a-date")).isNull();
        assertThat(MailTimestamps.formatIfInstant("Mini's n More")).isNull();
        assertThat(MailTimestamps.formatIfInstant(42)).isNull();
        assertThat(MailTimestamps.formatIfInstant(OffsetDateTime.parse("2026-10-02T06:00:00Z"))).isEqualTo(
            "Fri, 2 Oct 2026, 11:30 AM IST"
        );
        assertThat(MailTimestamps.formatIfInstant(ZonedDateTime.ofInstant(Instant.parse("2026-10-02T06:00:00Z"), ZoneOffset.UTC)))
            .isEqualTo("Fri, 2 Oct 2026, 11:30 AM IST");
    }

    @Test
    void rfc2822Now_usesIstOffset() {
        assertThat(MailTimestamps.rfc2822Now()).endsWith(" +0530");
        assertThat(MailTimestamps.now()).endsWith(" IST");
    }

    @Test
    void stampSentDate_setsIstDateHeaderOnMimeMessage() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        MailTimestamps.stampSentDate(message, null);
        assertThat(message.getHeader("Date", null)).endsWith("+0530");
    }
}
