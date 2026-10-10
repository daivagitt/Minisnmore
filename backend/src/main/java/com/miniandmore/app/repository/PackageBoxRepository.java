package com.miniandmore.app.repository;

import com.miniandmore.app.domain.PackageBox;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PackageBoxRepository extends JpaRepository<PackageBox, Long> {
    List<PackageBox> findAllByOrderByNameAsc();

    Optional<PackageBox> findFirstByNameIgnoreCase(String name);
}
