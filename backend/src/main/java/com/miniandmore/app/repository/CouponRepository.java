package com.miniandmore.app.repository;

import com.miniandmore.app.domain.Coupon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Coupon entity.
 */
@SuppressWarnings("unused")
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long>, JpaSpecificationExecutor<Coupon> {
    List<Coupon> findAllByOrderByIdDesc();

    Optional<Coupon> findOneByCodeIgnoreCase(String code);
}
