package com.miniandmore.app.repository;

import com.miniandmore.app.domain.PageVisibility;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the PageVisibility entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PageVisibilityRepository extends JpaRepository<PageVisibility, Long>, JpaSpecificationExecutor<PageVisibility> {
    List<PageVisibility> findAllByOrderByPageKeyAsc();

    Optional<PageVisibility> findOneByPageKey(String pageKey);

    long countByVisibleTrue();
}
