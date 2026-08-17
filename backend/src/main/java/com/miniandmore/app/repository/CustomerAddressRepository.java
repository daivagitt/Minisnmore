package com.miniandmore.app.repository;

import com.miniandmore.app.domain.CustomerAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
    List<CustomerAddress> findAllByUserLoginOrderByIsDefaultDescIdAsc(String userLogin);

    Optional<CustomerAddress> findOneByIdAndUserLogin(Long id, String userLogin);

    long countByUserLogin(String userLogin);
}
