package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.Coupon;
import com.miniandmore.app.domain.enumeration.DiscountType;
import com.miniandmore.app.repository.CouponRepository;
import com.miniandmore.app.service.dto.CouponDTO;
import com.miniandmore.app.service.mapper.CouponMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final Long ID = 1L;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponMapper couponMapper;

    @Mock
    private AdminConfigChangeMailService adminConfigChangeMailService;

    @InjectMocks
    private CouponService couponService;

    @Test
    void save_clampsBogoPercentOff() {
        CouponDTO input = new CouponDTO();
        input.setDiscountType(DiscountType.BOGO);
        input.setDiscountValue(BigDecimal.valueOf(150));
        Coupon entity = new Coupon().id(ID);
        Coupon saved = new Coupon().id(ID);
        CouponDTO expected = new CouponDTO();
        expected.setId(ID);

        when(couponMapper.toEntity(input)).thenReturn(entity);
        when(couponRepository.save(entity)).thenReturn(saved);
        when(couponMapper.toDto(saved)).thenReturn(expected);

        couponService.save(input);

        assertThat(input.getDiscountValue()).isEqualByComparingTo("100");
        verify(adminConfigChangeMailService).notifyCouponChangedAfterCommit(null, expected);
    }

    @Test
    void save_normalizesMissingBogoQuantities() {
        CouponDTO input = new CouponDTO();
        Coupon entity = new Coupon().id(ID);
        Coupon saved = new Coupon().id(ID);
        CouponDTO expected = new CouponDTO();
        expected.setId(ID);

        when(couponMapper.toEntity(input)).thenReturn(entity);
        when(couponRepository.save(entity)).thenReturn(saved);
        when(couponMapper.toDto(saved)).thenReturn(expected);

        couponService.save(input);

        assertThat(input.getBogoBuyQty()).isEqualTo(1);
        assertThat(input.getBogoGetQty()).isEqualTo(1);
        verify(couponMapper).toEntity(input);
        verify(couponRepository).save(entity);
        verify(couponMapper).toDto(saved);
    }

    @Test
    void update() {
        CouponDTO input = new CouponDTO();
        input.setId(ID);
        Coupon entity = new Coupon().id(ID);
        Coupon saved = new Coupon().id(ID);
        CouponDTO expected = new CouponDTO();
        expected.setId(ID);

        when(couponMapper.toEntity(input)).thenReturn(entity);
        when(couponRepository.save(entity)).thenReturn(saved);
        when(couponMapper.toDto(saved)).thenReturn(expected);

        CouponDTO result = couponService.update(input);

        assertThat(result).isSameAs(expected);
        verify(couponMapper).toEntity(input);
        verify(couponRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        CouponDTO input = new CouponDTO();
        input.setId(ID);
        Coupon existing = new Coupon().id(ID);
        CouponDTO expected = new CouponDTO();
        expected.setId(ID);

        when(couponRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(couponRepository.save(existing)).thenReturn(existing);
        when(couponMapper.toDto(existing)).thenReturn(expected);

        Optional<CouponDTO> result = couponService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(couponMapper).partialUpdate(existing, input);
        verify(couponRepository).save(existing);
    }

    @Test
    void partialUpdate_normalizesBogoQuantities() {
        CouponDTO input = new CouponDTO();
        input.setId(ID);
        input.setBogoBuyQty(0);
        input.setBogoGetQty(99);
        Coupon existing = new Coupon().id(ID);
        CouponDTO expected = new CouponDTO();
        expected.setId(ID);

        when(couponRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(couponRepository.save(existing)).thenReturn(existing);
        when(couponMapper.toDto(existing)).thenReturn(expected);

        couponService.partialUpdate(input);

        assertThat(input.getBogoBuyQty()).isEqualTo(1);
        assertThat(input.getBogoGetQty()).isEqualTo(10);
        verify(couponMapper).partialUpdate(existing, input);
    }

    @Test
    void partialUpdate_notFound() {
        CouponDTO input = new CouponDTO();
        input.setId(ID);
        when(couponRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<CouponDTO> result = couponService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        Coupon entity = new Coupon().id(ID);
        CouponDTO dto = new CouponDTO();
        dto.setId(ID);
        when(couponRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(couponMapper.toDto(entity)).thenReturn(dto);

        Optional<CouponDTO> result = couponService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(couponRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<CouponDTO> result = couponService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        Coupon entity = new Coupon().id(ID);
        CouponDTO previous = new CouponDTO();
        previous.setId(ID);
        previous.setCode("GONE");
        when(couponRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(couponMapper.toDto(entity)).thenReturn(previous);

        couponService.delete(ID);

        verify(couponRepository).deleteById(ID);
        verify(adminConfigChangeMailService).notifyCouponChangedAfterCommit(previous, null);
    }

}
