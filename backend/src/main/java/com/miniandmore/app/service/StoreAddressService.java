package com.miniandmore.app.service;

import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.domain.StoreAddress;
import com.miniandmore.app.domain.enumeration.StoreAddressUsage;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.repository.StoreAddressRepository;
import com.miniandmore.app.service.dto.StoreAddressDTO;
import com.miniandmore.app.service.mapper.StoreAddressMapper;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StoreAddressService {

    private final StoreAddressRepository storeAddressRepository;
    private final StoreAddressMapper storeAddressMapper;
    private final SiteSettingRepository siteSettingRepository;

    public StoreAddressService(
        StoreAddressRepository storeAddressRepository,
        StoreAddressMapper storeAddressMapper,
        SiteSettingRepository siteSettingRepository
    ) {
        this.storeAddressRepository = storeAddressRepository;
        this.storeAddressMapper = storeAddressMapper;
        this.siteSettingRepository = siteSettingRepository;
    }

    @Transactional(readOnly = true)
    public List<StoreAddressDTO> findAllNewestFirst() {
        return storeAddressRepository.findAllByOrderByIdDesc().stream().map(storeAddressMapper::toDto).toList();
    }

    public StoreAddressDTO save(StoreAddressDTO dto) {
        dto.setId(null);
        return persist(dto, null);
    }

    public StoreAddressDTO update(StoreAddressDTO dto) {
        if (dto.getId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
        }
        StoreAddress previous = storeAddressRepository
            .findById(dto.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        return persist(dto, previous);
    }

    public void delete(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
        }
        StoreAddress existing = storeAddressRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        Set<StoreAddressUsage> usages = copyUsages(existing.getUsages());
        boolean wasDefault = Boolean.TRUE.equals(existing.getIsDefault());
        storeAddressRepository.delete(existing);
        if (wasDefault) {
            for (StoreAddressUsage usage : usages) {
                applyRemainingDefault(usage);
            }
        }
    }

    private StoreAddressDTO persist(StoreAddressDTO dto, StoreAddress previous) {
        normalize(dto);
        Set<StoreAddressUsage> previousUsages = previous == null ? Set.of() : copyUsages(previous.getUsages());
        boolean previouslyDefault = previous != null && Boolean.TRUE.equals(previous.getIsDefault());
        Set<StoreAddressUsage> orphanedPeerUsages = EnumSet.noneOf(StoreAddressUsage.class);
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            // One default address per usage type — clear peers and flush before writing the new default.
            orphanedPeerUsages.addAll(clearOtherDefaults(dto.getUsages(), dto.getId()));
        }
        StoreAddress entity;
        if (previous != null) {
            // partialUpdate mutates the managed usages collection in place — do not replace the Set reference.
            storeAddressMapper.partialUpdate(previous, dto);
            entity = previous;
        } else {
            entity = storeAddressMapper.toEntity(dto);
        }
        StoreAddress saved = storeAddressRepository.save(entity);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            applyDefaultToSiteSettings(saved);
        }
        Set<StoreAddressUsage> savedUsages = copyUsages(saved.getUsages());
        for (StoreAddressUsage usage : previousUsages) {
            boolean removed = !savedUsages.contains(usage);
            boolean lostDefault = previouslyDefault && (!Boolean.TRUE.equals(saved.getIsDefault()) || removed);
            if (removed || lostDefault) {
                applyRemainingDefault(usage);
            }
        }
        for (StoreAddressUsage usage : orphanedPeerUsages) {
            if (!savedUsages.contains(usage) || !Boolean.TRUE.equals(saved.getIsDefault())) {
                applyRemainingDefault(usage);
            }
        }
        return storeAddressMapper.toDto(saved);
    }

    /**
     * Clears default on any other address that includes one of {@code claimedUsages}.
     * Returns usages those peers still cover that are not in {@code claimedUsages}, so site
     * settings for those types can be refreshed after the new default is saved.
     */
    private Set<StoreAddressUsage> clearOtherDefaults(Set<StoreAddressUsage> claimedUsages, Long keepId) {
        Set<StoreAddressUsage> orphaned = EnumSet.noneOf(StoreAddressUsage.class);
        boolean cleared = false;
        Set<Long> seen = new java.util.HashSet<>();
        for (StoreAddressUsage usage : claimedUsages) {
            for (StoreAddress existing : storeAddressRepository.findAllByUsagesContainingAndIsDefaultTrue(usage)) {
                if (keepId != null && keepId.equals(existing.getId())) {
                    continue;
                }
                if (!seen.add(existing.getId())) {
                    continue;
                }
                for (StoreAddressUsage peerUsage : copyUsages(existing.getUsages())) {
                    if (!claimedUsages.contains(peerUsage)) {
                        orphaned.add(peerUsage);
                    }
                }
                existing.setIsDefault(false);
                storeAddressRepository.save(existing);
                cleared = true;
            }
        }
        if (cleared) {
            storeAddressRepository.flush();
        }
        return orphaned;
    }

    private void applyDefaultToSiteSettings(StoreAddress address) {
        siteSettingRepository
            .findFirstByOrderByIdAsc()
            .ifPresent(setting -> {
                String formatted = formatMultiline(address);
                Set<StoreAddressUsage> usages = copyUsages(address.getUsages());
                if (usages.contains(StoreAddressUsage.INVOICE)) {
                    setting.setInvoiceSoldByAddress(formatted);
                }
                if (usages.contains(StoreAddressUsage.SHIPMENT)) {
                    setting.setInvoiceDispatchFromAddress(formatted);
                }
                if (usages.contains(StoreAddressUsage.NORMAL)) {
                    setting.setInvoiceRegisteredOfficeAddress(formatted);
                }
                siteSettingRepository.save(setting);
            });
    }

    private void applyRemainingDefault(StoreAddressUsage usage) {
        storeAddressRepository
            .findFirstByUsagesContainingAndIsDefaultTrueOrderByIdDesc(usage)
            .ifPresentOrElse(this::applyDefaultToSiteSettings, () -> clearSiteSettingForUsage(usage));
    }

    private void clearSiteSettingForUsage(StoreAddressUsage usage) {
        siteSettingRepository
            .findFirstByOrderByIdAsc()
            .ifPresent(setting -> {
                if (usage == StoreAddressUsage.INVOICE) {
                    setting.setInvoiceSoldByAddress(null);
                } else if (usage == StoreAddressUsage.SHIPMENT) {
                    setting.setInvoiceDispatchFromAddress(null);
                } else if (usage == StoreAddressUsage.NORMAL) {
                    setting.setInvoiceRegisteredOfficeAddress(null);
                }
                siteSettingRepository.save(setting);
            });
    }

    static void normalize(StoreAddressDTO dto) {
        Set<StoreAddressUsage> usages = copyUsages(dto.getUsages());
        if (usages.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Choose at least one: shipment, normal address, or invoice"
            );
        }
        dto.setUsages(usages);
        dto.setLabel(trimToNull(dto.getLabel()));
        dto.setFullName(required(dto.getFullName(), "Name"));
        dto.setPhone(normalizePhone(dto.getPhone()));
        dto.setLine1(required(dto.getLine1(), "Address line 1"));
        dto.setLine2(trimToNull(dto.getLine2()));
        dto.setCity(required(dto.getCity(), "City"));
        dto.setState(required(dto.getState(), "State"));
        dto.setPincode(normalizePincode(dto.getPincode()));
        dto.setCountry(StringUtils.defaultIfBlank(trimToNull(dto.getCountry()), "India"));
        dto.setIsDefault(Boolean.TRUE.equals(dto.getIsDefault()));
        if (dto.getPhone().length() != 10 || dto.getPhone().charAt(0) < '6' || dto.getPhone().charAt(0) > '9') {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit Indian mobile number");
        }
    }

    public static Map<String, Object> dispatchAddressPayload(
        Optional<StoreAddress> stored,
        ApplicationProperties.XpressBees warehouse
    ) {
        return stored.map(StoreAddressService::toFulfillmentMap).orElseGet(() -> warehouseMap(warehouse));
    }

    public static Map<String, Object> toFulfillmentMap(StoreAddress address) {
        Map<String, Object> data = new LinkedHashMap<>();
        String name = StringUtils.defaultString(address.getFullName());
        data.put("name", name);
        data.put("full_name", name);
        data.put("line1", StringUtils.defaultString(address.getLine1()));
        data.put("line2", StringUtils.defaultString(address.getLine2()));
        data.put("city", StringUtils.defaultString(address.getCity()));
        data.put("state", StringUtils.defaultString(address.getState()));
        data.put("pincode", StringUtils.defaultString(address.getPincode()));
        data.put("phone", StringUtils.defaultString(address.getPhone()));
        data.put("country", StringUtils.defaultIfBlank(address.getCountry(), "India"));
        return data;
    }

    public static XpressBeesClient.ReversePickupAddress warehousePickup(
        Optional<StoreAddress> stored,
        ApplicationProperties.XpressBees xpressBees,
        String fallbackPhone
    ) {
        return stored
            .map(address ->
                new XpressBeesClient.ReversePickupAddress(
                    StringUtils.defaultIfBlank(address.getFullName(), "Mini's n More"),
                    StringUtils.defaultIfBlank(address.getPhone(), fallbackPhone),
                    null,
                    address.getLine1(),
                    address.getLine2(),
                    address.getCity(),
                    address.getState(),
                    address.getPincode()
                )
            )
            .orElseGet(() ->
                xpressBees == null
                    ? new XpressBeesClient.ReversePickupAddress(
                        "Mini's n More",
                        fallbackPhone,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                    : new XpressBeesClient.ReversePickupAddress(
                        StringUtils.defaultIfBlank(xpressBees.getWarehouseName(), "Mini's n More"),
                        StringUtils.defaultIfBlank(xpressBees.getWarehousePhone(), fallbackPhone),
                        null,
                        xpressBees.getWarehouseLine1(),
                        xpressBees.getWarehouseLine2(),
                        xpressBees.getWarehouseCity(),
                        xpressBees.getWarehouseState(),
                        xpressBees.getOriginPincode()
                    )
            );
    }

    static String formatMultiline(StoreAddress address) {
        List<String> lines = new ArrayList<>();
        addLine(lines, address.getFullName());
        addLine(lines, address.getLine1());
        addLine(lines, address.getLine2());
        String locality = StringUtils.trimToEmpty(address.getCity());
        if (StringUtils.isNotBlank(address.getState())) {
            locality = locality.isEmpty() ? address.getState() : locality + ", " + address.getState();
        }
        if (StringUtils.isNotBlank(address.getPincode())) {
            locality = locality.isEmpty() ? address.getPincode() : locality + " - " + address.getPincode();
        }
        addLine(lines, locality);
        if (StringUtils.isNotBlank(address.getPhone())) {
            lines.add("Phone: " + address.getPhone());
        }
        return String.join("\n", lines);
    }

    private static Map<String, Object> warehouseMap(ApplicationProperties.XpressBees warehouse) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (warehouse == null) {
            data.put("name", "");
            data.put("full_name", "");
            data.put("line1", "");
            data.put("line2", "");
            data.put("city", "");
            data.put("state", "");
            data.put("pincode", "");
            data.put("phone", "");
            data.put("country", "India");
            return data;
        }
        data.put("name", StringUtils.defaultString(warehouse.getWarehouseName()));
        data.put("full_name", StringUtils.defaultString(warehouse.getWarehouseName()));
        data.put("line1", StringUtils.defaultString(warehouse.getWarehouseLine1()));
        data.put("line2", StringUtils.defaultString(warehouse.getWarehouseLine2()));
        data.put("city", StringUtils.defaultString(warehouse.getWarehouseCity()));
        data.put("state", StringUtils.defaultString(warehouse.getWarehouseState()));
        data.put("pincode", StringUtils.defaultString(warehouse.getOriginPincode()));
        data.put("phone", StringUtils.defaultString(warehouse.getWarehousePhone()));
        data.put("country", "India");
        return data;
    }

    private static void addLine(List<String> lines, String value) {
        if (StringUtils.isNotBlank(value)) {
            lines.add(value.trim());
        }
    }

    private static Set<StoreAddressUsage> copyUsages(Set<StoreAddressUsage> usages) {
        if (usages == null || usages.isEmpty()) {
            return EnumSet.noneOf(StoreAddressUsage.class);
        }
        return EnumSet.copyOf(usages);
    }

    private static String required(String value, String label) {
        String trimmed = StringUtils.trimToEmpty(value);
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        return StringUtils.trimToNull(value);
    }

    private static String normalizePhone(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D+", "");
        if (digits.length() >= 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.length() > 10) {
            digits = digits.substring(0, 10);
        }
        return digits;
    }

    private static String normalizePincode(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D+", "");
        if (digits.length() > 6) {
            digits = digits.substring(0, 6);
        }
        if (!digits.matches("[1-9]\\d{5}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 6-digit pincode");
        }
        return digits;
    }
}
