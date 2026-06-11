package com.miniandmore.app.repository;

import com.miniandmore.app.domain.HomeSection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the HomeSection entity.
 */
@SuppressWarnings("unused")
@Repository
public interface HomeSectionRepository extends JpaRepository<HomeSection, Long>, JpaSpecificationExecutor<HomeSection> {
    List<HomeSection> findAllByEnabledTrueOrderBySortOrderAsc();

    List<HomeSection> findAllByOrderBySortOrderAsc();

    Optional<HomeSection> findOneBySectionKey(String sectionKey);
}
