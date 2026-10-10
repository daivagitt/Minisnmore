package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.ContactMessage;
import com.miniandmore.app.repository.ContactMessageRepository;
import com.miniandmore.app.service.dto.ContactMessageDTO;
import com.miniandmore.app.service.mapper.ContactMessageMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ContactMessageServiceTest {

    @Mock
    private ContactMessageRepository contactMessageRepository;

    @Mock
    private ContactMessageMapper contactMessageMapper;

    private ContactMessageService service;

    @BeforeEach
    void setUp() {
        service = new ContactMessageService(contactMessageRepository, contactMessageMapper);
    }

    @Test
    void submit_trimsFields_normalizesPhone_andPersists() {
        ContactMessageDTO incoming = new ContactMessageDTO();
        incoming.setId(99L);
        incoming.setName("  Ada  ");
        incoming.setEmail("  Care@Example.com ");
        incoming.setPhone("+91 98765 43210");
        incoming.setAddress("  12 Flower Lane  ");
        incoming.setComment("  Please call me  ");

        ContactMessage entity = new ContactMessage();
        ContactMessage saved = new ContactMessage().id(1L);
        ContactMessageDTO out = new ContactMessageDTO();
        out.setId(1L);
        when(contactMessageRepository.existsByEmailIgnoreCaseAndCreatedDateAfter(any(), any())).thenReturn(false);
        when(contactMessageRepository.countByEmailIgnoreCaseAndCreatedDateAfter(any(), any())).thenReturn(0L);
        when(contactMessageMapper.toEntity(any(ContactMessageDTO.class))).thenReturn(entity);
        when(contactMessageRepository.save(entity)).thenReturn(saved);
        when(contactMessageMapper.toDto(saved)).thenReturn(out);

        ContactMessageDTO result = service.submit(incoming);

        assertThat(incoming.getId()).isEqualTo(99L);
        assertThat(incoming.getName()).isEqualTo("  Ada  ");
        assertThat(result.getId()).isEqualTo(1L);
        ArgumentCaptor<ContactMessageDTO> captor = ArgumentCaptor.forClass(ContactMessageDTO.class);
        verify(contactMessageMapper).toEntity(captor.capture());
        ContactMessageDTO mapped = captor.getValue();
        assertThat(mapped.getId()).isNull();
        assertThat(mapped.getName()).isEqualTo("Ada");
        assertThat(mapped.getEmail()).isEqualTo("care@example.com");
        assertThat(mapped.getPhone()).isEqualTo("9876543210");
        assertThat(mapped.getAddress()).isEqualTo("12 Flower Lane");
        assertThat(mapped.getComment()).isEqualTo("Please call me");
        assertThat(mapped.getCreatedDate()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void delete_whenMissing_throwsNotFound() {
        when(contactMessageRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(404L))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Contact message not found");

        verify(contactMessageRepository, never()).deleteById(404L);
    }

    @Test
    void submit_rejectsRecentDuplicateEmail() {
        when(contactMessageRepository.existsByEmailIgnoreCaseAndCreatedDateAfter(eq("ada@example.com"), any())).thenReturn(true);

        ContactMessageDTO incoming = new ContactMessageDTO();
        incoming.setName("Ada");
        incoming.setEmail("ada@example.com");
        incoming.setPhone("9876543210");
        incoming.setAddress("12 Flower Lane");
        incoming.setComment("Hello again");

        assertThatThrownBy(() -> service.submit(incoming))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("wait a moment");

        verify(contactMessageRepository, never()).save(any(ContactMessage.class));
    }

    @Test
    void submit_rejectsShortPhone() {
        ContactMessageDTO incoming = new ContactMessageDTO();
        incoming.setName("Ada");
        incoming.setEmail("ada@example.com");
        incoming.setPhone("12345");
        incoming.setAddress("12 Flower Lane");
        incoming.setComment("Hello");

        assertThatThrownBy(() -> service.submit(incoming))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("10-digit Indian mobile");
    }

    @Test
    void findAllNewestFirst_returnsMappedList() {
        ContactMessage entity = new ContactMessage().id(5L);
        ContactMessageDTO dto = new ContactMessageDTO();
        dto.setId(5L);
        when(contactMessageRepository.findAllByOrderByCreatedDateDesc()).thenReturn(List.of(entity));
        when(contactMessageMapper.toDto(entity)).thenReturn(dto);

        List<ContactMessageDTO> result = service.findAllNewestFirst();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(5L);
    }

    @Test
    void delete_whenValid_deletesRecord() {
        when(contactMessageRepository.existsById(10L)).thenReturn(true);

        service.delete(10L);

        verify(contactMessageRepository).deleteById(10L);
    }

    @Test
    void delete_whenNullId_throwsNotFound() {
        assertThatThrownBy(() -> service.delete(null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Contact message not found");
    }

    @Test
    void submit_validatesRequiredFields() {
        ContactMessageDTO dto = new ContactMessageDTO();
        dto.setPhone("9876543210");

        // Missing Name
        assertThatThrownBy(() -> service.submit(dto))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Name is required");

        // Missing Email
        dto.setName("Ada");
        assertThatThrownBy(() -> service.submit(dto))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Email is required");

        // Missing Address
        dto.setEmail("ada@example.com");
        assertThatThrownBy(() -> service.submit(dto))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Address is required");

        // Missing Comment
        dto.setAddress("Street");
        assertThatThrownBy(() -> service.submit(dto))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Comment is required");
    }

    @Test
    void submit_rejectsTooManyPerHour() {
        when(contactMessageRepository.existsByEmailIgnoreCaseAndCreatedDateAfter(eq("ada@example.com"), any())).thenReturn(false);
        when(contactMessageRepository.countByEmailIgnoreCaseAndCreatedDateAfter(eq("ada@example.com"), any())).thenReturn(5L);

        ContactMessageDTO incoming = new ContactMessageDTO();
        incoming.setName("Ada");
        incoming.setEmail("ada@example.com");
        incoming.setPhone("9876543210");
        incoming.setAddress("12 Flower Lane");
        incoming.setComment("Hello");

        assertThatThrownBy(() -> service.submit(incoming))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Too many messages");
    }

    @Test
    void submit_rejectsInvalidPhoneStarts() {
        ContactMessageDTO incoming = new ContactMessageDTO();
        incoming.setName("Ada");
        incoming.setEmail("ada@example.com");
        incoming.setPhone("5876543210"); // starts with 5
        incoming.setAddress("12 Flower Lane");
        incoming.setComment("Hello");

        assertThatThrownBy(() -> service.submit(incoming))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("10-digit Indian mobile");

        // Null phone
        incoming.setPhone(null);
        assertThatThrownBy(() -> service.submit(incoming))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("10-digit Indian mobile");
    }
}
