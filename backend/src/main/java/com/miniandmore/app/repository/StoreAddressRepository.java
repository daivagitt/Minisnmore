package com.miniandmore.app.repository;

import com.miniandmore.app.domain.StoreAddress;
import com.miniandmore.app.domain.enumeration.StoreAddressUsage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreAddressRepository extends JpaRepository<StoreAddress, Long> {
    List<StoreAddress> findAllByOrderByIdDesc();

    List<StoreAddress> findAllByUsageAndIsDefaultTrue(StoreAddressUsage usage);

    Optional<StoreAddress> findFirstByUsageAndIsDefaultTrue(StoreAddressUsage usage);
}
