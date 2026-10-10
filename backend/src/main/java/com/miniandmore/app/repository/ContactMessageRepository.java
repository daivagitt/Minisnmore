package com.miniandmore.app.repository;

import com.miniandmore.app.domain.ContactMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    List<ContactMessage> findAllByOrderByCreatedDateDesc();

    long countByEmailIgnoreCaseAndCreatedDateAfter(String email, Instant createdDate);

    boolean existsByEmailIgnoreCaseAndCreatedDateAfter(String email, Instant createdDate);
}
