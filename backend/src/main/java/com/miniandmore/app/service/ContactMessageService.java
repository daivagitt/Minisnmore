package com.miniandmore.app.service;

import com.miniandmore.app.domain.ContactMessage;
import com.miniandmore.app.repository.ContactMessageRepository;
import com.miniandmore.app.service.dto.ContactMessageDTO;
import com.miniandmore.app.service.mapper.ContactMessageMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ContactMessageService {

    private static final Duration SUBMIT_COOLDOWN = Duration.ofMinutes(2);
    private static final int MAX_SUBMISSIONS_PER_HOUR = 5;

    private final ContactMessageRepository contactMessageRepository;
    private final ContactMessageMapper contactMessageMapper;

    public ContactMessageService(ContactMessageRepository contactMessageRepository, ContactMessageMapper contactMessageMapper) {
        this.contactMessageRepository = contactMessageRepository;
        this.contactMessageMapper = contactMessageMapper;
    }

    public ContactMessageDTO submit(ContactMessageDTO dto) {
        String phone = normalizePhone(dto.getPhone());
        if (phone.length() != 10 || phone.charAt(0) < '6' || phone.charAt(0) > '9') {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit Indian mobile number");
        }
        ContactMessageDTO normalized = new ContactMessageDTO();
        normalized.setName(trimRequired(dto.getName(), "Name"));
        normalized.setEmail(trimRequired(dto.getEmail(), "Email").toLowerCase());
        normalized.setPhone(phone);
        normalized.setAddress(trimRequired(dto.getAddress(), "Address"));
        normalized.setComment(trimRequired(dto.getComment(), "Comment"));
        normalized.setCreatedDate(Instant.now());
        rejectIfAbusive(normalized.getEmail());
        ContactMessage saved = contactMessageRepository.save(contactMessageMapper.toEntity(normalized));
        return contactMessageMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ContactMessageDTO> findAllNewestFirst() {
        return contactMessageRepository.findAllByOrderByCreatedDateDesc().stream().map(contactMessageMapper::toDto).toList();
    }

    public void delete(Long id) {
        if (id == null || !contactMessageRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact message not found");
        }
        contactMessageRepository.deleteById(id);
    }

    private void rejectIfAbusive(String email) {
        Instant now = Instant.now();
        if (contactMessageRepository.existsByEmailIgnoreCaseAndCreatedDateAfter(email, now.minus(SUBMIT_COOLDOWN))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait a moment before sending another message.");
        }
        long recentCount = contactMessageRepository.countByEmailIgnoreCaseAndCreatedDateAfter(email, now.minus(Duration.ofHours(1)));
        if (recentCount >= MAX_SUBMISSIONS_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many messages from this email. Please try again later.");
        }
    }

    private static String trimRequired(String value, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required");
        }
        return trimmed;
    }

    private static String normalizePhone(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D+", "");
        if (digits.length() >= 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.length() > 10) {
            digits = digits.substring(0, 10);
        }
        return digits;
    }
}
