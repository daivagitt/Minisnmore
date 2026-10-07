package com.miniandmore.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContactMessageDTOTest {

    @Test
    void testGettersAndSetters() {
        ContactMessageDTO dto = new ContactMessageDTO();
        Instant now = Instant.now();

        dto.setId(10L);
        dto.setName("John Doe");
        dto.setEmail("john@example.com");
        dto.setPhone("1234567890");
        dto.setAddress("123 Street");
        dto.setComment("Inquiry comment");
        dto.setCreatedDate(now);

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getName()).isEqualTo("John Doe");
        assertThat(dto.getEmail()).isEqualTo("john@example.com");
        assertThat(dto.getPhone()).isEqualTo("1234567890");
        assertThat(dto.getAddress()).isEqualTo("123 Street");
        assertThat(dto.getComment()).isEqualTo("Inquiry comment");
        assertThat(dto.getCreatedDate()).isEqualTo(now);
    }

    @Test
    void testEqualsAndHashCode() {
        ContactMessageDTO dto1 = new ContactMessageDTO();
        dto1.setId(1L);

        ContactMessageDTO dto2 = new ContactMessageDTO();
        dto2.setId(1L);

        ContactMessageDTO dto3 = new ContactMessageDTO();
        dto3.setId(2L);

        ContactMessageDTO empty1 = new ContactMessageDTO();
        ContactMessageDTO empty2 = new ContactMessageDTO();

        assertThat(dto1).isEqualTo(dto1);
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        assertThat(dto1).isNotEqualTo(dto3);
        assertThat(dto1).isNotEqualTo(null);
        assertThat(dto1).isNotEqualTo("other object");
        assertThat(empty1).isNotEqualTo(empty2);
        assertThat(dto1).isNotEqualTo(empty1);
    }

    @Test
    void testToString() {
        ContactMessageDTO dto = new ContactMessageDTO();
        dto.setId(5L);
        dto.setEmail("alice@example.com");

        assertThat(dto.toString()).contains("ContactMessageDTO").contains("id=5").contains("email='alice@example.com'");
    }
}
