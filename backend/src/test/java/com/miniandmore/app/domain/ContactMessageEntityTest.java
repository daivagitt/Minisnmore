package com.miniandmore.app.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContactMessageEntityTest {

    @Test
    void equalsAndHashCode_useIdWhenPresent() {
        ContactMessage a = new ContactMessage().id(1L).email("a@example.com");
        ContactMessage sameId = new ContactMessage().id(1L).email("other@example.com");
        ContactMessage different = new ContactMessage().id(2L).email("a@example.com");
        ContactMessage noId = new ContactMessage().email("a@example.com");

        assertThat(a).isEqualTo(sameId);
        assertThat(a).isNotEqualTo(different);
        assertThat(a).isNotEqualTo(noId);
        assertThat(a).isNotEqualTo("not-a-message");
        assertThat(a.hashCode()).isEqualTo(sameId.hashCode());
        assertThat(noId.hashCode()).isEqualTo(new ContactMessage().hashCode());
    }

    @Test
    void fluentSetters_andToString() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        ContactMessage message = new ContactMessage()
            .id(5L)
            .name("Ada")
            .email("ada@example.com")
            .phone("9876543210")
            .address("Line 1")
            .comment("Hello")
            .createdDate(created);

        assertThat(message.getName()).isEqualTo("Ada");
        assertThat(message.getCreatedDate()).isEqualTo(created);
        assertThat(message.toString()).contains("ada@example.com");
    }
}
