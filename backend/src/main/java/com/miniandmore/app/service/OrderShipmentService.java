package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PackageType;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderShipmentService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderShipmentService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final Duration EXPRESS_BEES_TRACKING_TTL = Duration.ofMinutes(5);
    private final Map<Long, Instant> expressBeesTrackingLastPull = new ConcurrentHashMap<>();

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final XpressBeesClient xpressBeesClient;
    private final ApplicationProperties applicationProperties;
    private final OrderEventService orderEventService;
    private final OrderCancellationService orderCancellationService;
    private final ObjectMapper objectMapper;

    public OrderShipmentService(
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

    /**
     * True when Admin booked ExpressBees or a manual AWB. Those orders keep local carrier
     * status; Zoho shipment/fulfillment updates must not overwrite them.
     */
    public static boolean hasLocalForwardShipment(CustomerOrder order) {
        if (order == null || order.getShipmentCancelledAt() != null) {
            return false;
        }
        return order.getShipmentLogisticsMode() != null && StringUtils.isNotBlank(order.getTrackingNumber());
    }

    public static boolean isShipmentEligible(CustomerOrder order) {
        if (order == null) {
            return false;
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FULFILLED) {
            return false;
        }
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.HAND_WRAPPED) {
            return false;
        }
        if (StringUtils.isNotBlank(order.getTrackingNumber()) || StringUtils.isNotBlank(order.getShipmentDate())) {
            return false;
        }
        if (!isCod(order) && order.getPaymentStatus() != PaymentStatus.PAID) {
            return false;
        }
        return true;
    }

    public boolean canBook(CustomerOrder order) {
        return isShipmentEligible(order) && isPackageCompleted(order) && !orderCancellationService.hasPendingRequest(order.getId());
    }

    public boolean canBookWithOverride(CustomerOrder order) {
        return isShipmentEligible(order) && isPackageCompleted(order) && orderCancellationService.hasPendingRequest(order.getId());
    }

    public boolean canBookExpressBees(CustomerOrder order) {
        return canBook(order);
    }

    public boolean canBookExpressBeesWithOverride(CustomerOrder order) {
        return canBookWithOverride(order);
    }

    @SuppressWarnings("java:S107")
    public CustomerOrder bookShipment(
        String orderNumber,
        ReturnLogisticsMode mode,
        String carrierName,
        String awb,
        String trackingUrl,
        String labelUrl,
        boolean overrideFulfillmentHold,
        String overrideReason
    ) {
        return bookShipment(
            orderNumber,
            mode,
            carrierName,
            awb,
            trackingUrl,
            labelUrl,
            overrideFulfillmentHold,
            overrideReason,
            null,
            null,
            null
        );
    }

    public CustomerOrder bookShipment(
        String orderNumber,
        ReturnLogisticsMode mode,
        String carrierName,
        String awb,
        String trackingUrl,
        String labelUrl,
        boolean overrideFulfillmentHold,
        String overrideReason,
        String courierId,
        String courierName
    ) {
        return bookShipment(
            orderNumber,
            mode,
            carrierName,
            awb,
            trackingUrl,
            labelUrl,
            overrideFulfillmentHold,
            overrideReason,
            courierId,
            courierName,
            null
        );
    }

    public CustomerOrder bookShipment(
        String orderNumber,
        ReturnLogisticsMode mode,
        String carrierName,
        String awb,
        String trackingUrl,
        String labelUrl,
        boolean overrideFulfillmentHold,
        String overrideReason,
        String courierId,
        String courierName,
        String packageType
    ) {
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        assertCanBook(order, overrideFulfillmentHold, overrideReason);
        PackageType.parse(packageType).ifPresent(order::setPackageType);
        if (mode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logistics mode is required");
        }
        if (mode == ReturnLogisticsMode.SELF_SHIP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use manual mode for non-ExpressBees carriers");
        }
        if (mode == ReturnLogisticsMode.XPRESSBEES) {
            applyCourierSelection(order, courierId, courierName);
            return bookExpressBees(order, awb);
        }
        if (StringUtils.isBlank(awb)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AWB or tracking reference is required");
        }
        applyShipment(
            order,
            ReturnLogisticsMode.MANUAL,
            StringUtils.defaultIfBlank(carrierName, "Manual"),
            awb,
            trackingUrl,
            labelUrl,
            null
        );
        return persist(order);
    }

    private CustomerOrder bookExpressBees(CustomerOrder order, String preAssignedAwb) {
        if (!xpressBeesClient.isForwardShipmentConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ExpressBees forward shipment is not configured. Set XPRESSBEES credentials, business account, warehouse address and forward manifest path, or book using manual mode."
            );
        }
        try {
            XpressBeesClient.ForwardShipmentResult result = xpressBeesClient.createForwardShipment(
                buildForwardShipmentRequest(order, preAssignedAwb)
            );
            String tracking = StringUtils.defaultIfBlank(xpressBeesClient.buildTrackingUrl(result.awb()), result.trackingUrl());
            applyShipment(
                order,
                ReturnLogisticsMode.XPRESSBEES,
                "XpressBees",
                result.awb(),
                tracking,
                result.labelUrl(),
                result.providerRequestId()
            );
            return persist(order);
        } catch (XpressBeesClient.XpressBeesApiException ex) {
            LOG.warn("ExpressBees forward shipment failed for {}: {}", order.getOrderNumber(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    public CustomerOrder cancelShipment(
        String orderNumber,
        ReturnLogisticsMode mode,
        String reason,
        String cancelledBy
    ) {
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        String normalizedReason = StringUtils.normalizeSpace(StringUtils.trimToEmpty(reason));
        if (normalizedReason.length() < 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipment cancellation reason is required");
        }
        if (StringUtils.isBlank(order.getTrackingNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has no booked shipment");
        }
        if (order.getStatus() == OrderStatus.FULFILLED || order.getShipmentDeliveredAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A delivered shipment cannot be cancelled");
        }
        if (mode == null || mode == ReturnLogisticsMode.SELF_SHIP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use ExpressBees or manual cancellation mode");
        }
        if (mode == ReturnLogisticsMode.XPRESSBEES) {
            if (order.getShipmentLogisticsMode() != ReturnLogisticsMode.XPRESSBEES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This shipment was not booked through ExpressBees");
            }
            try {
                xpressBeesClient.cancelForwardShipment(order.getTrackingNumber(), order.getOrderNumber());
            } catch (XpressBeesClient.XpressBeesApiException ex) {
                LOG.warn("ExpressBees shipment cancellation failed for {}: {}", orderNumber, ex.getMessage());
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    ex.getMessage() + ". Confirm cancellation with the carrier, then use manual cancellation mode."
                );
            }
        }
        order.setShipmentCancelledAt(Instant.now());
        order.setShipmentCancelledBy(StringUtils.left(StringUtils.defaultIfBlank(cancelledBy, "system"), 50));
        order.setShipmentCancellationReason(StringUtils.left(normalizedReason, 500));
        order.setShipmentCarrierStatus("cancelled");
        order.setTrackingNumber(null);
        order.setTrackingCarrier(null);
        order.setTrackingUrl(null);
        order.setShipmentDate(null);
        order.setShipmentLabelUrl(null);
        order.setShipmentProviderRequestId(null);
        order.setShipmentLogisticsMode(null);
        return persist(order);
    }

    private void assertCanBook(CustomerOrder order, boolean overrideFulfillmentHold, String overrideReason) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancelled orders cannot be booked for shipment");
        }
        if (order.getStatus() == OrderStatus.FULFILLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivered orders cannot be booked for shipment");
        }
        if (StringUtils.isNotBlank(order.getTrackingNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A shipment is already booked for this order");
        }
        if (!isShipmentEligible(order)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Order must be confirmed or hand-wrapped, and paid (unless COD), before booking a shipment"
            );
        }
        if (orderCancellationService.hasPendingRequest(order.getId())) {
            if (!overrideFulfillmentHold) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Shipment booking is blocked while a cancellation request is pending. Reject the cancellation request or provide an audited override."
                );
            }
            String reason = StringUtils.normalizeSpace(StringUtils.trimToEmpty(overrideReason));
            if (reason.length() < 5) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Override reason is required when booking shipment during a cancellation hold"
                );
            }
            LOG.warn(
                "Admin overrode cancellation fulfillment hold for {}: {}",
                order.getOrderNumber(),
                StringUtils.left(reason, 500)
            );
        }
        assertPackageCompleted(order);
    }

    private void applyShipment(
        CustomerOrder order,
        ReturnLogisticsMode mode,
        String carrierName,
        String awb,
        String trackingUrl,
        String labelUrl,
        String providerRequestId
    ) {
        order.setShipmentLogisticsMode(mode);
        order.setTrackingCarrier(StringUtils.left(StringUtils.trimToNull(carrierName), 120));
        order.setTrackingNumber(StringUtils.left(StringUtils.trimToNull(awb), 120));
        order.setTrackingUrl(StringUtils.left(StringUtils.trimToNull(trackingUrl), 500));
        order.setShipmentLabelUrl(StringUtils.left(StringUtils.trimToNull(labelUrl), 500));
        order.setShipmentProviderRequestId(StringUtils.left(StringUtils.trimToNull(providerRequestId), 120));
        order.setShipmentCarrierStatus("booked");
        order.setShipmentCancelledAt(null);
        order.setShipmentCancelledBy(null);
        order.setShipmentCancellationReason(null);
        if (mode != ReturnLogisticsMode.XPRESSBEES && StringUtils.isBlank(order.getShipmentDate())) {
            order.setShipmentDate(LocalDate.now(INDIA).toString());
        }
    }

    public CustomerOrder applyProviderStatus(CustomerOrder order, String providerStatus) {
        return applyProviderStatus(order, providerStatus, null);
    }

    CustomerOrder applyProviderStatus(CustomerOrder order, String providerStatus, Instant providerDeliveredAt) {
        if (order.getStatus() == OrderStatus.CANCELLED || order.getShipmentCancelledAt() != null) {
            return order;
        }
        String incoming = XpressBeesShipmentStatus.normalize(providerStatus);
        String current = XpressBeesShipmentStatus.normalize(order.getShipmentCarrierStatus());
        String incomingMilestone = Optional.ofNullable(XpressBeesShipmentStatus.milestone(incoming)).orElse(incoming);
        String currentMilestone = Optional.ofNullable(XpressBeesShipmentStatus.milestone(current)).orElse(current);
        String normalized = XpressBeesShipmentStatus.preferForwardMilestone(currentMilestone, incomingMilestone);
        order.setShipmentCarrierStatus(normalized);
        boolean leftWarehouse =
            XpressBeesShipmentStatus.isPickedUpOrInTransit(normalized) ||
            XpressBeesShipmentStatus.isOutForDelivery(normalized) ||
            XpressBeesShipmentStatus.isUndelivered(normalized);
        if (leftWarehouse && StringUtils.isBlank(order.getShipmentDate())) {
            order.setShipmentDate(LocalDate.now(INDIA).toString());
        }
        if (XpressBeesShipmentStatus.isDelivered(normalized) && canMarkDelivered(order)) {
            OrderDeliverySupport.markDelivered(order, providerDeliveredAt);
            if (order.getStatus() != OrderStatus.CANCELLED) {
                order.setStatus(OrderStatus.FULFILLED);
            }
        }
        return persist(order);
    }

    /**
     * Carrier scans are not always monotonic. Delegates to {@link XpressBeesShipmentStatus#preferForwardMilestone}.
     */
    static String preferCarrierStatus(String current, String incoming) {
        String incomingMilestone = Optional.ofNullable(XpressBeesShipmentStatus.milestone(incoming)).orElse(XpressBeesShipmentStatus.normalize(incoming));
        String currentMilestone = Optional.ofNullable(XpressBeesShipmentStatus.milestone(current)).orElse(XpressBeesShipmentStatus.normalize(current));
        return XpressBeesShipmentStatus.preferForwardMilestone(currentMilestone, incomingMilestone);
    }

    public boolean isExpressBeesTrackingFresh(CustomerOrder order) {
        if (order == null || order.getId() == null) {
            return false;
        }
        Instant lastPull = expressBeesTrackingLastPull.get(order.getId());
        return lastPull != null && lastPull.isAfter(Instant.now().minus(EXPRESS_BEES_TRACKING_TTL));
    }

    public CustomerOrder refreshExpressBeesTracking(CustomerOrder order) {
        if (
            order == null ||
            order.getShipmentLogisticsMode() != ReturnLogisticsMode.XPRESSBEES ||
            StringUtils.isBlank(order.getTrackingNumber()) ||
            order.getShipmentCancelledAt() != null ||
            order.getStatus() == OrderStatus.CANCELLED ||
            order.getStatus() == OrderStatus.FULFILLED ||
            isExpressBeesTrackingFresh(order)
        ) {
            return order;
        }
        expressBeesTrackingLastPull.put(order.getId(), Instant.now());
        try {
            return xpressBeesClient
                .fetchForwardTracking(order.getTrackingNumber())
                .map(result -> {
                    if (XpressBeesShipmentStatus.sameForwardMilestone(result.status(), order.getShipmentCarrierStatus())) {
                        return order;
                    }
                    return applyProviderStatus(order, result.status());
                })
                .orElse(order);
        } catch (Exception ex) {
            LOG.debug("ExpressBees tracking refresh skipped for {}: {}", order.getOrderNumber(), ex.getMessage());
            return order;
        }
    }

    private static boolean canMarkDelivered(CustomerOrder order) {
        return order.getStatus() != OrderStatus.CANCELLED && order.getShipmentCancelledAt() == null;
    }

    static boolean isPackageCompleted(CustomerOrder order) {
        return (
            order != null &&
            order.getPackedAt() != null &&
            order.getPackageLengthCm() != null &&
            order.getPackageBreadthCm() != null &&
            order.getPackageHeightCm() != null &&
            order.getPackageLengthCm() >= 1 &&
            order.getPackageBreadthCm() >= 1 &&
            order.getPackageHeightCm() >= 1
        );
    }

    private static void applyCourierSelection(CustomerOrder order, String courierId, String courierName) {
        String selectedId = StringUtils.trimToNull(courierId);
        if (selectedId == null) {
            return;
        }
        order.setPackageCourierId(StringUtils.left(selectedId, 40));
        String selectedName = StringUtils.trimToNull(courierName);
        if (selectedName != null) {
            order.setPackageCourierName(StringUtils.left(selectedName, 120));
        }
    }

    private static void assertPackageCompleted(CustomerOrder order) {
        if (!isPackageCompleted(order)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Create a package before booking a shipment"
            );
        }
    }

    private CustomerOrder persist(CustomerOrder order) {
        CustomerOrder saved = customerOrderRepository.save(order);
        orderEventService.syncLifecycleEvents(saved);
        return saved;
    }

    private XpressBeesClient.ForwardShipmentRequest buildForwardShipmentRequest(CustomerOrder order, String preAssignedAwb) {
        JsonNode shipping = parseShippingAddress(order.getShippingAddressJson());
        ApplicationProperties.XpressBees xpressBees = applicationProperties.getXpressbees();
        XpressBeesClient.ReversePickupAddress shipFrom = new XpressBeesClient.ReversePickupAddress(
            StringUtils.defaultIfBlank(xpressBees.getWarehouseName(), "Mini's n More"),
            StringUtils.defaultIfBlank(xpressBees.getWarehousePhone(), firstAddressField(shipping, "phone", "mobile")),
            null,
            xpressBees.getWarehouseLine1(),
            xpressBees.getWarehouseLine2(),
            xpressBees.getWarehouseCity(),
            xpressBees.getWarehouseState(),
            xpressBees.getOriginPincode()
        );
        XpressBeesClient.ReversePickupAddress deliverTo = new XpressBeesClient.ReversePickupAddress(
            firstAddressField(shipping, "full_name", "name"),
            firstAddressField(shipping, "phone", "mobile"),
            firstAddressField(shipping, "email"),
            firstAddressField(shipping, "line1", "address_line1", "address"),
            firstAddressField(shipping, "line2", "address_line2"),
            firstAddressField(shipping, "city"),
            firstAddressField(shipping, "state"),
            firstAddressField(shipping, "pincode", "postal_code", "zip")
        );
        return new XpressBeesClient.ForwardShipmentRequest(
            order.getOrderNumber(),
            order.getOrderNumber(),
            shipFrom,
            deliverTo,
            buildShipmentLines(order),
            StringUtils.trimToNull(preAssignedAwb),
            isCod(order) ? "cod" : "prepaid",
            order.getTotal(),
            isCod(order) ? order.getTotal() : BigDecimal.ZERO,
            order.getPackageLengthCm(),
            order.getPackageBreadthCm(),
            order.getPackageHeightCm(),
            order.getPackageWeightGrams() != null && order.getPackageWeightGrams() > 0
                ? order.getPackageWeightGrams()
                : order.getPackageChargeableWeightGrams(),
            StringUtils.trimToNull(order.getPackageCourierId())
        );
    }

    private List<XpressBeesClient.ReversePickupLine> buildShipmentLines(CustomerOrder order) {
        List<OrderItem> items = order.getId() == null ? List.of() : orderItemRepository.findAllByOrderId(order.getId());
        List<XpressBeesClient.ReversePickupLine> lines = new ArrayList<>();
        int defaultWeight = Math.max(1, applicationProperties.getXpressbees().getDefaultWeightGrams());
        for (OrderItem item : items) {
            lines.add(
                new XpressBeesClient.ReversePickupLine(
                    StringUtils.defaultIfBlank(item.getProductName(), "Order item"),
                    StringUtils.defaultIfBlank(item.getVariantSku(), StringUtils.defaultIfBlank(item.getProductSlug(), order.getOrderNumber())),
                    Math.max(1, item.getQty() == null ? 1 : item.getQty()),
                    item.getPrice(),
                    defaultWeight
                )
            );
        }
        if (lines.isEmpty()) {
            lines.add(
                new XpressBeesClient.ReversePickupLine(
                    "Order " + order.getOrderNumber(),
                    order.getOrderNumber(),
                    1,
                    order.getTotal() == null ? BigDecimal.ZERO : order.getTotal(),
                    defaultWeight
                )
            );
        }
        return lines;
    }

    private JsonNode parseShippingAddress(String shippingAddressJson) {
        if (StringUtils.isBlank(shippingAddressJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(shippingAddressJson);
        } catch (Exception ex) {
            LOG.debug("Could not parse shipping address JSON for forward shipment: {}", ex.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private static String firstAddressField(JsonNode address, String... fields) {
        if (address == null || address.isMissingNode()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = address.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText("").trim();
                if (StringUtils.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private static boolean isCod(CustomerOrder order) {
        return "cod".equalsIgnoreCase(StringUtils.trimToEmpty(order.getPaymentMethod()));
    }
}
