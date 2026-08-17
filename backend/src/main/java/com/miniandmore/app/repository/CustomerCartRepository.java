package com.miniandmore.app.repository;

import com.miniandmore.app.domain.CustomerCart;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerCartRepository extends JpaRepository<CustomerCart, Long> {
    Optional<CustomerCart> findOneByUserLogin(String userLogin);
}
