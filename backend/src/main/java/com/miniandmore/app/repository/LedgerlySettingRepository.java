package com.miniandmore.app.repository;

import com.miniandmore.app.domain.LedgerlySetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerlySettingRepository extends JpaRepository<LedgerlySetting, Long> {
    Optional<LedgerlySetting> findFirstByOrderByIdAsc();
}
