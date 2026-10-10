package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.ContactMessage;
import com.miniandmore.app.service.dto.ContactMessageDTO;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContactMessageMapperFullTest {

    private final ContactMessageMapper mapper = new ContactMessageMapperImpl();

    @Test
    void roundTrip_mapsAllFields() {
        Instant created = Instant.parse("2026-03-01T10:15:00Z");
        ContactMessage entity = new ContactMessage()
            .id(7L)
            .name("Priya")
            .email("priya@example.com")
            .phone("9876543210")
            .address("12 Park Street")
            .comment("Need help with sizing")
            .createdDate(created);

        ContactMessageDTO dto = mapper.toDto(entity);
        ContactMessage roundTrip = mapper.toEntity(dto);
        ContactMessage copy = mapper.toEntity(dto);
        ContactMessageDTO patch = new ContactMessageDTO();
        patch.setComment("Updated comment");
        mapper.partialUpdate(copy, patch);

        assertThat(dto.getName()).isEqualTo("Priya");
        assertThat(dto.getEmail()).isEqualTo("priya@example.com");
        assertThat(dto.getPhone()).isEqualTo("9876543210");
        assertThat(dto.getAddress()).isEqualTo("12 Park Street");
        assertThat(dto.getComment()).isEqualTo("Need help with sizing");
        assertThat(dto.getCreatedDate()).isEqualTo(created);
        assertThat(roundTrip.getName()).isEqualTo("Priya");
        assertThat(copy.getComment()).isEqualTo("Updated comment");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
    }

    @Test
    void toEntityList_mapsEachEntry() {
        ContactMessage first = new ContactMessage().id(1L).name("A").email("a@x.com").phone("9876543210").address("Addr").comment("Hi").createdDate(Instant.now());
        ContactMessage second = new ContactMessage().id(2L).name("B").email("b@x.com").phone("9876543211").address("Addr2").comment("Bye").createdDate(Instant.now());

        List<ContactMessageDTO> dtos = mapper.toDto(List.of(first, second));

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getEmail()).isEqualTo("a@x.com");
        assertThat(dtos.get(1).getEmail()).isEqualTo("b@x.com");
    }

    @Test
    void partialUpdate_updatesSelectedFields() {
        ContactMessage entity = new ContactMessage()
            .id(3L)
            .name("X")
            .email("x@x.com")
            .phone("9876543210")
            .address("A")
            .comment("C")
            .createdDate(Instant.now());
        ContactMessageDTO patch = new ContactMessageDTO();
        patch.setName("Y");
        patch.setComment("Updated");
        mapper.partialUpdate(entity, patch);
        assertThat(entity.getName()).isEqualTo("Y");
        assertThat(entity.getComment()).isEqualTo("Updated");
        assertThat(entity.getEmail()).isEqualTo("x@x.com");
    }
}
