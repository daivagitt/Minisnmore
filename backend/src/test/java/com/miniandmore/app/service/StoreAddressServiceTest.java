package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        incoming.setUsages(EnumSet.of(StoreAddressUsage.INVOICE));
        incoming.setIsDefault(true);

        StoreAddress previousDefault = new StoreAddress().id(2L).usages(EnumSet.of(StoreAddressUsage.INVOICE)).isDefault(true);
        StoreAddress entity = new StoreAddress();
        StoreAddress saved = sample().id(1L).usages(EnumSet.of(StoreAddressUsage.INVOICE)).isDefault(true);
        StoreAddressDTO out = dto();
        out.setId(1L);

        when(storeAddressRepository.findAllByUsagesContainingAndIsDefaultTrue(StoreAddressUsage.INVOICE)).thenReturn(
            List.of(previousDefault)
        );
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
        verify(storeAddressRepository).flush();
        assertThat(setting.getInvoiceSoldByAddress()).contains("Mini Warehouse");
        verify(siteSettingRepository).save(setting);
    }

    @Test
    void update_switchingDefault_clearsPreviousDefaultBeforeSave() {
        StoreAddressDTO incoming = dto();
        incoming.setId(1L);
        incoming.setUsages(EnumSet.of(StoreAddressUsage.SHIPMENT));
        incoming.setIsDefault(true);

        StoreAddress existing = sample().id(1L).usages(EnumSet.of(StoreAddressUsage.SHIPMENT)).isDefault(false);
        StoreAddress previousDefault = sample()
            .id(2L)
            .fullName("Old warehouse")
            .usages(EnumSet.of(StoreAddressUsage.SHIPMENT))
            .isDefault(true);
        StoreAddressDTO out = dto();
        out.setId(1L);
        out.setIsDefault(true);

        when(storeAddressRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(storeAddressRepository.findAllByUsagesContainingAndIsDefaultTrue(StoreAddressUsage.SHIPMENT)).thenReturn(
            List.of(previousDefault)
        );
        stubPartialUpdate();
        when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeAddressMapper.toDto(any(StoreAddress.class))).thenReturn(out);
        SiteSetting setting = new SiteSetting().id(1L);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        service.update(incoming);

        assertThat(previousDefault.getIsDefault()).isFalse();
        assertThat(existing.getIsDefault()).isTrue();
        verify(storeAddressMapper).partialUpdate(existing, incoming);
        verify(storeAddressRepository).flush();
        verify(storeAddressRepository, never()).findFirstByUsagesContainingAndIsDefaultTrueOrderByIdDesc(any());
        assertThat(setting.getInvoiceDispatchFromAddress()).contains("Mini Warehouse");
    }

    @Test
    void update_newDefault_demotesMultiUsagePeer_andRefreshesOrphanedUsageSiteSetting() {
        StoreAddressDTO incoming = dto();
        incoming.setId(1L);
        incoming.setUsages(EnumSet.of(StoreAddressUsage.SHIPMENT, StoreAddressUsage.INVOICE));
        incoming.setIsDefault(true);

        StoreAddress existing = sample().id(1L).usages(EnumSet.of(StoreAddressUsage.SHIPMENT)).isDefault(false);
        StoreAddress peer = sample()
            .id(2L)
            .fullName("Peer office")
            .usages(EnumSet.of(StoreAddressUsage.SHIPMENT, StoreAddressUsage.NORMAL))
            .isDefault(true);
        StoreAddress remainingNormal = sample()
            .id(3L)
            .fullName("Registered office")
            .usages(EnumSet.of(StoreAddressUsage.NORMAL))
            .isDefault(true);
        StoreAddressDTO out = dto();
        out.setId(1L);
        out.setUsages(incoming.getUsages());

        when(storeAddressRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(storeAddressRepository.findAllByUsagesContainingAndIsDefaultTrue(StoreAddressUsage.SHIPMENT)).thenReturn(
            List.of(peer)
        );
        when(storeAddressRepository.findAllByUsagesContainingAndIsDefaultTrue(StoreAddressUsage.INVOICE)).thenReturn(
            List.of(peer)
        );
        stubPartialUpdate();
        when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeAddressMapper.toDto(any(StoreAddress.class))).thenReturn(out);
        when(storeAddressRepository.findFirstByUsagesContainingAndIsDefaultTrueOrderByIdDesc(StoreAddressUsage.NORMAL))
            .thenReturn(Optional.of(remainingNormal));
        SiteSetting setting = new SiteSetting().id(1L);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        service.update(incoming);

        assertThat(peer.getIsDefault()).isFalse();
        verify(storeAddressRepository).flush();
        verify(storeAddressRepository).findFirstByUsagesContainingAndIsDefaultTrueOrderByIdDesc(StoreAddressUsage.NORMAL);
        assertThat(setting.getInvoiceRegisteredOfficeAddress()).contains("Registered office");
    }

    @Test
    void save_multiUsageDefault_writesAllMatchingSiteSettings() {
        StoreAddressDTO incoming = dto();
        incoming.setUsages(EnumSet.of(StoreAddressUsage.SHIPMENT, StoreAddressUsage.NORMAL, StoreAddressUsage.INVOICE));
        incoming.setIsDefault(true);

        StoreAddress entity = sample().usages(incoming.getUsages()).isDefault(true);
        StoreAddressDTO out = dto();
        out.setId(1L);
        out.setUsages(incoming.getUsages());

        when(storeAddressRepository.findAllByUsagesContainingAndIsDefaultTrue(any())).thenReturn(List.of());
        when(storeAddressMapper.toEntity(any(StoreAddressDTO.class))).thenReturn(entity);
        when(storeAddressRepository.save(entity)).thenReturn(entity);
        when(storeAddressMapper.toDto(entity)).thenReturn(out);
        SiteSetting setting = new SiteSetting().id(1L);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        service.save(incoming);

        assertThat(setting.getInvoiceDispatchFromAddress()).contains("Mini Warehouse");
        assertThat(setting.getInvoiceRegisteredOfficeAddress()).contains("Mini Warehouse");
        assertThat(setting.getInvoiceSoldByAddress()).contains("Mini Warehouse");
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
        StoreAddress deleted = sample().id(1L).usages(EnumSet.of(StoreAddressUsage.INVOICE)).isDefault(true);
        StoreAddress remaining = sample().id(2L).fullName("Other office").usages(EnumSet.of(StoreAddressUsage.INVOICE)).isDefault(true);
        when(storeAddressRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(storeAddressRepository.findFirstByUsagesContainingAndIsDefaultTrueOrderByIdDesc(StoreAddressUsage.INVOICE)).thenReturn(
            Optional.of(remaining)
        );
        SiteSetting setting = new SiteSetting().id(1L);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        service.delete(1L);

        verify(storeAddressRepository).delete(deleted);
        assertThat(setting.getInvoiceSoldByAddress()).contains("Other office");
        verify(siteSettingRepository).save(setting);
    }

    @Test
    void delete_lastDefault_clearsMatchingInvoiceSiteSetting() {
        StoreAddress deleted = sample().id(1L).usages(EnumSet.of(StoreAddressUsage.SHIPMENT)).isDefault(true);
        when(storeAddressRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(storeAddressRepository.findFirstByUsagesContainingAndIsDefaultTrueOrderByIdDesc(StoreAddressUsage.SHIPMENT)).thenReturn(
            Optional.empty()
        );
        SiteSetting setting = new SiteSetting().id(1L).invoiceDispatchFromAddress("Mini Warehouse\n12 Flower Lane");
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        service.delete(1L);

        assertThat(setting.getInvoiceDispatchFromAddress()).isNull();
        verify(siteSettingRepository).save(setting);
    }

    @Test
    void update_changingUsages_usesPartialUpdateWithoutReplacingCollectionReference() {
        StoreAddressDTO incoming = dto();
        incoming.setId(1L);
        incoming.setUsages(EnumSet.of(StoreAddressUsage.SHIPMENT, StoreAddressUsage.INVOICE));

        StoreAddress existing = sample().id(1L).usages(EnumSet.of(StoreAddressUsage.SHIPMENT)).isDefault(false);
        StoreAddressDTO out = dto();
        out.setId(1L);
        out.setUsages(incoming.getUsages());

        when(storeAddressRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubPartialUpdate();
        when(storeAddressRepository.save(existing)).thenReturn(existing);
        when(storeAddressMapper.toDto(existing)).thenReturn(out);

        service.update(incoming);

        verify(storeAddressMapper).partialUpdate(existing, incoming);
        verify(storeAddressMapper, never()).toEntity(any(StoreAddressDTO.class));
        verify(storeAddressMapper, never()).toEntity(anyList());
        assertThat(existing.getUsages()).containsExactlyInAnyOrder(
            StoreAddressUsage.SHIPMENT,
            StoreAddressUsage.INVOICE
        );
    }

    @Test
    void update_changingUsage_refreshesPreviousUsageSiteSetting() {
        StoreAddressDTO incoming = dto();
        incoming.setId(1L);
        incoming.setUsages(EnumSet.of(StoreAddressUsage.SHIPMENT));
        incoming.setIsDefault(true);
        StoreAddress previous = sample().id(1L).usages(EnumSet.of(StoreAddressUsage.INVOICE)).isDefault(true);
        StoreAddress remainingInvoice = sample()
            .id(3L)
            .fullName("Registered office")
            .usages(EnumSet.of(StoreAddressUsage.INVOICE))
            .isDefault(true);
        StoreAddressDTO out = dto();
        out.setId(1L);
        out.setUsages(EnumSet.of(StoreAddressUsage.SHIPMENT));

        when(storeAddressRepository.findById(1L)).thenReturn(Optional.of(previous));
        when(storeAddressRepository.findAllByUsagesContainingAndIsDefaultTrue(StoreAddressUsage.SHIPMENT)).thenReturn(List.of());
        stubPartialUpdate();
        when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeAddressMapper.toDto(any(StoreAddress.class))).thenReturn(out);
        when(storeAddressRepository.findFirstByUsagesContainingAndIsDefaultTrueOrderByIdDesc(StoreAddressUsage.INVOICE)).thenReturn(
            Optional.of(remainingInvoice)
        );
        SiteSetting setting = new SiteSetting().id(1L);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        service.update(incoming);

        verify(storeAddressMapper).partialUpdate(previous, incoming);
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

    private void stubPartialUpdate() {
        doAnswer(invocation -> {
            StoreAddress entity = invocation.getArgument(0);
            StoreAddressDTO source = invocation.getArgument(1);
            if (source.getFullName() != null) {
                entity.setFullName(source.getFullName());
            }
            if (source.getPhone() != null) {
                entity.setPhone(source.getPhone());
            }
            if (source.getLine1() != null) {
                entity.setLine1(source.getLine1());
            }
            if (source.getLine2() != null) {
                entity.setLine2(source.getLine2());
            }
            if (source.getCity() != null) {
                entity.setCity(source.getCity());
            }
            if (source.getState() != null) {
                entity.setState(source.getState());
            }
            if (source.getPincode() != null) {
                entity.setPincode(source.getPincode());
            }
            if (source.getCountry() != null) {
                entity.setCountry(source.getCountry());
            }
            if (source.getUsages() != null) {
                entity.setUsages(source.getUsages());
            }
            if (source.getIsDefault() != null) {
                entity.setIsDefault(source.getIsDefault());
            }
            if (source.getLabel() != null) {
                entity.setLabel(source.getLabel());
            }
            return null;
        })
            .when(storeAddressMapper)
            .partialUpdate(any(StoreAddress.class), any(StoreAddressDTO.class));
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
            .usages(Set.of(StoreAddressUsage.SHIPMENT))
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
        dto.setUsages(EnumSet.of(StoreAddressUsage.SHIPMENT));
        dto.setIsDefault(false);
        return dto;
    }
}
