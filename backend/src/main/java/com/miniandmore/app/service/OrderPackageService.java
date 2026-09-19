package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderPackageService {

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final XpressBeesClient xpressBeesClient;
    private final ApplicationProperties applicationProperties;
    private final OrderEventService orderEventService;
    private final OrderCancellationService orderCancellationService;
    private final ObjectMapper objectMapper;

    public OrderPackageService(
        CustomerOrderRepository customerOrderRepository,
        OrderItemRepository orderItemRepository,
        XpressBeesClient xpressBeesClient,
        ApplicationProperties applicationProperties,
        OrderEventService orderEventService,
        OrderCancellationService orderCancellationService,
        ObjectMapper objectMapper
    ) {
        this.customerOrderRepository = customerOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.xpressBeesClient = xpressBeesClient;
        this.applicationProperties = applicationProperties;
        this.orderEventService = orderEventService;
        this.orderCancellationService = orderCancellationService;
        this.objectMapper = objectMapper;
    }

    public static int volumetricWeightGrams(int lengthCm, int breadthCm, int heightCm, int divisor) {
        int safeDivisor = Math.max(1, divisor);
        long volume = (long) Math.max(1, lengthCm) * Math.max(1, breadthCm) * Math.max(1, heightCm);
        return (int) Math.max(1L, Math.ceil(volume * 1000.0d / safeDivisor));
    }

    public static int chargeableWeightGrams(int actualGrams, int volumetricGrams) {
        return Math.max(Math.max(1, actualGrams), Math.max(1, volumetricGrams));
    }

    public boolean canCompletePackage(CustomerOrder order) {
        return isPackageEligible(order) && !hasPendingCancellation(order);
    }

    public int defaultPackageWeightGrams(CustomerOrder order) {
        int perLine = Math.max(1, applicationProperties.getXpressbees().getDefaultWeightGrams());
        return perLine * Math.max(1, totalQuantity(order));
    }

    public Map<String, Object> previewRates(String orderNumber, Integer weightGrams, int lengthCm, int breadthCm, int heightCm) {
        CustomerOrder order = requireOrder(orderNumber);
        requirePackageEligible(order);
        requireDimensions(lengthCm, breadthCm, heightCm);
        requireDestinationPincode(order);
        return buildRates(order, resolveActualWeight(order, weightGrams), lengthCm, breadthCm, heightCm);
    }

    public CustomerOrder completePackage(
        String orderNumber,
        Integer weightGrams,
        int lengthCm,
        int breadthCm,
        int heightCm,
        String courierId,
        String courierName
    ) {
        CustomerOrder order = requireOrder(orderNumber);
        requirePackageEligible(order);
        requireDimensions(lengthCm, breadthCm, heightCm);
        requireDestinationPincode(order);
        int actualWeight = resolveActualWeight(order, weightGrams);
        Map<String, Object> rates = buildRates(order, actualWeight, lengthCm, breadthCm, heightCm);
        String selectedId = StringUtils.trimToNull(courierId);
        String selectedName = StringUtils.trimToNull(courierName);
        if (selectedId == null || selectedName == null) {
            selectedId = stringValue(rates.get("preferred_courier_id"), selectedId);
            selectedName = stringValue(rates.get("preferred_courier_name"), selectedName);
        }
        selectedName = resolveCourierName(rates, selectedId, selectedName);
        requireResolvableCourier(rates, selectedId, courierId);
        order.setPackageWeightGrams(actualWeight);
        order.setPackageLengthCm(lengthCm);
        order.setPackageBreadthCm(breadthCm);
        order.setPackageHeightCm(heightCm);
        order.setPackageChargeableWeightGrams((Integer) rates.get("chargeable_weight_grams"));
        order.setPackageCourierId(selectedId);
        order.setPackageCourierName(selectedName);
        order.setPackedAt(Instant.now());
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            order.setStatus(OrderStatus.HAND_WRAPPED);
        }
        CustomerOrder saved = customerOrderRepository.save(order);
        orderEventService.syncLifecycleEvents(saved);
        return saved;
    }

    private Map<String, Object> buildRates(CustomerOrder order, int actualWeightGrams, int lengthCm, int breadthCm, int heightCm) {
        int divisor = Math.max(1, applicationProperties.getXpressbees().getVolumetricDivisor());
        int volumetric = volumetricWeightGrams(lengthCm, breadthCm, heightCm, divisor);
        int chargeable = chargeableWeightGrams(actualWeightGrams, volumetric);
        String preferredName = XpressBeesClient.preferredForwardCourierName(chargeable);
        List<XpressBeesClient.CourierOption> options = xpressBeesClient.listForwardCourierOptions(
            destinationPincode(order),
            chargeable
        );
        String preferredId = null;
        List<Map<String, Object>> couriers = new ArrayList<>();
        for (XpressBeesClient.CourierOption option : options) {
            if (option.preferred()) {
                preferredId = option.id();
                preferredName = option.name();
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", option.id());
            row.put("name", option.name());
            row.put("edd_days", option.eddDays());
            row.put("eta", option.eta());
            row.put("rate", option.rate());
            row.put("preferred", option.preferred());
            couriers.add(row);
        }
        if (preferredId == null && !options.isEmpty()) {
            preferredId = options.get(0).id();
            preferredName = options.get(0).name();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actual_weight_grams", actualWeightGrams);
        payload.put("volumetric_weight_grams", volumetric);
        payload.put("chargeable_weight_grams", chargeable);
        payload.put("volumetric_divisor", divisor);
        payload.put("preferred_courier_id", preferredId);
        payload.put("preferred_courier_name", preferredName);
        payload.put("couriers", couriers);
        return payload;
    }

    private int resolveActualWeight(CustomerOrder order, Integer weightGrams) {
        if (weightGrams != null && weightGrams > 0) {
            return weightGrams;
        }
        if (order.getPackageWeightGrams() != null && order.getPackageWeightGrams() > 0) {
            return order.getPackageWeightGrams();
        }
        return defaultPackageWeightGrams(order);
    }

    private int totalQuantity(CustomerOrder order) {
        if (order.getId() == null) {
            return 1;
        }
        int total = 0;
        for (OrderItem item : orderItemRepository.findAllByOrderId(order.getId())) {
            total += Math.max(1, item.getQty() == null ? 1 : item.getQty());
        }
        return Math.max(1, total);
    }

    private String destinationPincode(CustomerOrder order) {
        JsonNode address = parseShippingAddress(order.getShippingAddressJson());
        for (String field : List.of("pincode", "postal_code", "zip")) {
            String value = address.path(field).asText("").trim();
            if (value.matches("^[1-9]\\d{5}$")) {
                return value;
            }
        }
        return null;
    }

    private JsonNode parseShippingAddress(String shippingAddressJson) {
        if (StringUtils.isBlank(shippingAddressJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(shippingAddressJson);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private CustomerOrder requireOrder(String orderNumber) {
        return customerOrderRepository
            .findOneByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void requirePackageEligible(CustomerOrder order) {
        if (!isPackageEligible(order)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, packageIneligibleReason(order));
        }
        if (hasPendingCancellation(order)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Packaging is on hold while a cancellation request is pending"
            );
        }
    }

    private boolean hasPendingCancellation(CustomerOrder order) {
        return order != null && order.getId() != null && orderCancellationService.hasPendingRequest(order.getId());
    }

    private static boolean isPackageEligible(CustomerOrder order) {
        if (order == null) {
            return false;
        }
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.HAND_WRAPPED) {
            return false;
        }
        if (StringUtils.isNotBlank(order.getTrackingNumber()) || StringUtils.isNotBlank(order.getShipmentDate())) {
            return false;
        }
        return isCod(order) || order.getPaymentStatus() == PaymentStatus.PAID;
    }

    private static String packageIneligibleReason(CustomerOrder order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return "Cancelled orders cannot be packed";
        }
        if (order.getStatus() == OrderStatus.FULFILLED) {
            return "Delivered orders cannot be packed";
        }
        if (StringUtils.isNotBlank(order.getTrackingNumber()) || StringUtils.isNotBlank(order.getShipmentDate())) {
            return "Shipment already booked";
        }
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.HAND_WRAPPED) {
            return "Order is not ready to pack";
        }
        return "Order must be paid before packing";
    }

    private static void requireDimensions(int lengthCm, int breadthCm, int heightCm) {
        if (lengthCm < 1 || breadthCm < 1 || heightCm < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package length, breadth and height must be at least 1 cm");
        }
    }

    private void requireDestinationPincode(CustomerOrder order) {
        if (destinationPincode(order) == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Order shipping address must include a valid 6-digit pincode before packing"
            );
        }
    }

    private static boolean isCod(CustomerOrder order) {
        return "cod".equalsIgnoreCase(StringUtils.trimToEmpty(order.getPaymentMethod()));
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    @SuppressWarnings("unchecked")
    private static void requireResolvableCourier(Map<String, Object> rates, String selectedId, String requestedCourierId) {
        List<Map<String, Object>> couriers = courierRows(rates);
        if (couriers.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "No ExpressBees courier options are available for this pincode and package weight"
            );
        }
        String idToValidate = StringUtils.isNotBlank(requestedCourierId) ? requestedCourierId.trim() : selectedId;
        if (StringUtils.isBlank(idToValidate)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not resolve an ExpressBees courier for this package");
        }
        boolean found = couriers.stream().anyMatch(row -> idToValidate.equals(stringValue(row.get("id"), null)));
        if (!found) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected courier is not serviceable for this shipment");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> courierRows(Map<String, Object> rates) {
        Object couriers = rates.get("couriers");
        if (!(couriers instanceof List<?> rows)) {
            return List.of();
        }
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                Map<String, Object> entry = new LinkedHashMap<>();
                map.forEach((key, value) -> entry.put(String.valueOf(key), value));
                parsed.add(entry);
            }
        }
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private static String resolveCourierName(Map<String, Object> rates, String courierId, String fallback) {
        if (StringUtils.isBlank(courierId)) {
            return fallback;
        }
        Object couriers = rates.get("couriers");
        if (!(couriers instanceof List<?> rows)) {
            return fallback;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            if (courierId.equals(stringValue(map.get("id"), null))) {
                return stringValue(map.get("name"), fallback);
            }
        }
        return fallback;
    }
}
