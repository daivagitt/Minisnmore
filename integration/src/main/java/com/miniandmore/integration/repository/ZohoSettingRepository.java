package com.miniandmore.integration.repository;

import com.miniandmore.integration.domain.ZohoSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ZohoSetting entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ZohoSettingRepository extends JpaRepository<ZohoSetting, Long>, JpaSpecificationExecutor<ZohoSetting> {
    Optional<ZohoSetting> findFirstByOrderByIdAsc();

    Optional<ZohoSetting> findFirstByConnectedTrueOrderByIdDesc();

    Optional<ZohoSetting> findFirstByOrderByIdDesc();

    /** Prefer the connected row; otherwise the most recently created row (not lowest id / seed data). */
    default Optional<ZohoSetting> findActiveOrLatest() {
        return findFirstByConnectedTrueOrderByIdDesc().or(this::findFirstByOrderByIdDesc);
    }
}
