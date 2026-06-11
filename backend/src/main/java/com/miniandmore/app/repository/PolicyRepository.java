package com.miniandmore.app.repository;

import com.miniandmore.app.domain.Policy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Policy entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PolicyRepository extends JpaRepository<Policy, Long>, JpaSpecificationExecutor<Policy> {
    List<Policy> findAllByOrderBySlugAsc();

    Optional<Policy> findOneBySlug(String slug);
}
