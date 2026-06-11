package com.miniandmore.app.repository;

import com.miniandmore.app.domain.SiteSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the SiteSetting entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long>, JpaSpecificationExecutor<SiteSetting> {
    Optional<SiteSetting> findFirstByOrderByIdAsc();
}
