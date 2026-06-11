package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.Coupon;
import com.miniandmore.app.service.dto.CouponDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Coupon} and its DTO {@link CouponDTO}.
 */
@Mapper(componentModel = "spring")
public interface CouponMapper extends EntityMapper<CouponDTO, Coupon> {}
