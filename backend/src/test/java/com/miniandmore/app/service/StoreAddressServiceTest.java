package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.domain.StoreAddress;
import com.miniandmore.app.domain.enumeration.StoreAddressUsage;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.repository.StoreAddressRepository;
import com.miniandmore.app.service.dto.StoreAddressDTO;
import com.miniandmore.app.service.mapper.StoreAddressMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StoreAddressServiceTest {

    @Mock
    private StoreAddressRepository storeAddressRepository;

    @Mock
    private StoreAddressMapper storeAddressMapper;

    @Mock
    private SiteSettingRepository siteSettingRepository;

    private StoreAddressService service;

    @BeforeEach
    void setUp() {
        service = new StoreAddressService(storeAddressRepository, storeAddressMapper, siteSettingRepository);
    }

    @Test
    void save_normalizesFields_clearsOtherDefaults_andWritesInvoiceSiteSetting() {
        StoreAddressDTO incoming = dto();
        incoming.setId(99L);
        incoming.setFullName("  Mini Warehouse  ");
        incoming.setPhone("+91 98765 43210");
        incoming.setLine1("  12 Flower Lane  ");
        incoming.setPincode("560001");
        incoming.setUsage(StoreAddressUsage.INVOICE);
        incoming.setIsDefault(true);

        StoreAddress previousDefault = new StoreAddress().id(2L).usage(StoreAddressUsage.INVOICE).isDefault(true);
        StoreAddress entity = new StoreAddress();
        StoreAddress saved = sample().id(1L).usage(StoreAddressUsage.INVOICE).isDefault(true);
        StoreAddressDTO out = dto();
        out.setId(1L);

        when(storeAddressRepository.findAllByUsageAndIsDefaultTrue(StoreAddressUsage.INVOICE)).thenReturn(List.of(previousDefault));
        when(storeAddressMapper.toEntity(any(StoreAddressDTO.class))).thenReturn(entity);
        when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> {
            StoreAddress argument = invocation.getArgument(0);
            return argument == previousDefault ? argument : saved;
        });
        when(storeAddressMapper.toDto(any(StoreAddress.class))).thenReturn(out);
        SiteSetting setting = new SiteSetting().id(1L);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        StoreAddressDTO result = service.save(incoming);

        assertThat(result.getId()).isEqualTo(1L);
        ArgumentCaptor<StoreAddressDTO> captor = ArgumentCaptor.forClass(StoreAddressDTO.class);
        verify(storeAddressMapper).toEntity(captor.capture());
        StoreAddressDTO mapped = captor.getValue();
        assertThat(mapped.getId()).isNull();
        assertThat(mapped.getFullName()).isEqualTo("Mini Warehouse");
        assertThat(mapped.getPhone()).isEqualTo("9876543210");
        assertThat(mapped.getLine1()).isEqualTo("12 Flower Lane");
        assertThat(previousDefault.getIsDefault()).isFalse();
        assertThat(setting.getInvoiceSoldByAddress()).contains("Mini Warehouse");
        verify(siteSettingRepository).save(setting);
    }

    @Test
    void delete_whenMissing_throwsNotFound() {
        when(storeAddressRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(404L))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Address not found");
        verify(storeAddressRepository, never()).delete(any());
    }

    @Test
    void delete_default_appliesRemainingDefaultToSiteSettings() {
        StoreAddress deleted = sample().id(1L).usage(StoreAddressUsage.INVOICE).isDefault(true);
        StoreAddress remaining = sample().id(2L).fullName("Other office").usage(StoreAddressUsage.INVOICE).isDefault(true);
        when(storeAddressRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(storeAddressRepository.findFirstByUsageAndIsDefaultTrue(StoreAddressUsage.INVOICE)).thenReturn(Optional.of(remaining));
        SiteSetting setting = new SiteSetting().id(1L);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        service.delete(1L);

        verify(storeAddressRepository).delete(deleted);
        assertThat(setting.getInvoiceSoldByAddress()).contains("Other office");
        verify(siteSettingRepository).save(setting);
    }

    @Test
    void delete_lastDefault_clearsMatchingInvoiceSiteSetting() {
        StoreAddress deleted = sample().id(1L).usage(StoreAddressUsage.SHIPMENT).isDefault(true);
        when(storeAddressRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(storeAddressRepository.findFirstByUsageAndIsDefaultTrue(StoreAddressUsage.SHIPMENT)).thenReturn(Optional.empty());
        SiteSetting setting = new SiteSetting().id(1L).invoiceDispatchFromAddress("Mini Warehouse\n12 Flower Lane");
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        service.delete(1L);

        assertThat(setting.getInvoiceDispatchFromAddress()).isNull();
        verify(siteSettingRepository).save(setting);
    }

    @Test
    void update_changingUsage_refreshesPreviousUsageSiteSetting() {
        StoreAddressDTO incoming = dto();
        incoming.setId(1L);
        incoming.setUsage(StoreAddressUsage.SHIPMENT);
        incoming.setIsDefault(true);
        StoreAddress previous = sample().id(1L).usage(StoreAddressUsage.INVOICE).isDefault(true);
        StoreAddress remainingInvoice = sample().id(3L).fullName("Registered office").usage(StoreAddressUsage.INVOICE).isDefault(true);
        StoreAddress saved = sample().id(1L).usage(StoreAddressUsage.SHIPMENT).isDefault(true);
        StoreAddressDTO out = dto();
        out.setId(1L);
        out.setUsage(StoreAddressUsage.SHIPMENT);

        when(storeAddressRepository.findById(1L)).thenReturn(Optional.of(previous));
        when(storeAddressRepository.findAllByUsageAndIsDefaultTrue(StoreAddressUsage.SHIPMENT)).thenReturn(List.of());
        when(storeAddressMapper.toEntity(any(StoreAddressDTO.class))).thenReturn(saved);
        when(storeAddressRepository.save(any(StoreAddress.class))).thenReturn(saved);
        when(storeAddressMapper.toDto(saved)).thenReturn(out);
        when(storeAddressRepository.findFirstByUsageAndIsDefaultTrue(StoreAddressUsage.INVOICE)).thenReturn(
            Optional.of(remainingInvoice)
        );
        SiteSetting setting = new SiteSetting().id(1L);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        service.update(incoming);

        assertThat(setting.getInvoiceDispatchFromAddress()).contains("Mini Warehouse");
        assertThat(setting.getInvoiceSoldByAddress()).contains("Registered office");
    }

    @Test
    void dispatchAddressPayload_prefersStoredShipmentAddress() {
        StoreAddress stored = sample();
        ApplicationProperties.XpressBees warehouse = new ApplicationProperties.XpressBees();
        warehouse.setWarehouseName("Config warehouse");

        var payload = StoreAddressService.dispatchAddressPayload(Optional.of(stored), warehouse);

        assertThat(payload.get("full_name")).isEqualTo("Mini Warehouse");
        assertThat(payload.get("pincode")).isEqualTo("560001");
        assertThat(payload.get("line1")).isEqualTo("12 Flower Lane");
    }

    @Test
    void warehousePickup_fallsBackToXpressBeesConfig() {
        ApplicationProperties.XpressBees warehouse = new ApplicationProperties.XpressBees();
        warehouse.setWarehouseName("Config warehouse");
        warehouse.setWarehouseLine1("Config line");
        warehouse.setOriginPincode("560074");

        var pickup = StoreAddressService.warehousePickup(Optional.empty(), warehouse, "9999999999");

        assertThat(pickup.name()).isEqualTo("Config warehouse");
        assertThat(pickup.line1()).isEqualTo("Config line");
        assertThat(pickup.pincode()).isEqualTo("560074");
        assertThat(pickup.phone()).isEqualTo("9999999999");
    }

    @Test
    void warehousePickup_whenConfigMissing_usesFallbackNameAndPhone() {
        var pickup = StoreAddressService.warehousePickup(Optional.empty(), null, "9999999999");

        assertThat(pickup.name()).isEqualTo("Mini's n More");
        assertThat(pickup.phone()).isEqualTo("9999999999");
    }

    private static StoreAddress sample() {
        return new StoreAddress()
            .fullName("Mini Warehouse")
            .phone("9876543210")
            .line1("12 Flower Lane")
            .city("Bengaluru")
            .state("Karnataka")
            .pincode("560001")
            .country("India")
            .usage(StoreAddressUsage.SHIPMENT)
            .isDefault(true);
    }

    private static StoreAddressDTO dto() {
        StoreAddressDTO dto = new StoreAddressDTO();
        dto.setFullName("Mini Warehouse");
        dto.setPhone("9876543210");
        dto.setLine1("12 Flower Lane");
        dto.setCity("Bengaluru");
        dto.setState("Karnataka");
        dto.setPincode("560001");
        dto.setCountry("India");
        dto.setUsage(StoreAddressUsage.SHIPMENT);
        dto.setIsDefault(false);
        return dto;
    }
}
