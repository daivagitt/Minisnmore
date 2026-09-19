package com.miniandmore.app.service;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.client.ZohoBooksIntegrationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.OrderReturnItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.ManualRefundMethod;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnDisposition;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.OrderReturnItemRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderReturnService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderReturnService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final int RETURN_WINDOW_DAYS = 3;

    /** Feature switch: returns & refunds. Flip to false to disable; no other code needs to change. */
    private static final boolean RETURNS_REFUNDS_ENABLED = true;
    private static final String RETURNS_DISABLED_MESSAGE = "Returns & refunds are currently unavailable. Please contact support.";

    private static final EnumSet<OrderReturnStatus> TERMINAL_RETURN_STATUSES = EnumSet.of(
        OrderReturnStatus.REJECTED,
        OrderReturnStatus.REFUNDED,
        OrderReturnStatus.COMPLETED,
        OrderReturnStatus.CANCELLED
    );

    /** Anything not terminal is "open" — computed so new intermediate statuses default to open, not the reverse. */
    private static final Set<OrderReturnStatus> OPEN_RETURN_STATUSES = EnumSet.complementOf(TERMINAL_RETURN_STATUSES);

    private final OrderReturnRepository orderReturnRepository;
    private final OrderReturnItemRepository orderReturnItemRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final RazorpayRefundService razorpayRefundService;
    private final LedgerlyIntegrationClient ledgerlyIntegrationClient;
    private final ZohoBooksIntegrationClient zohoBooksIntegrationClient;
    private final ProductVariantSupport variantSupport;
    private final OrderMailService orderMailService;
    private final OrderSmsService orderSmsService;
    private final ApplicationProperties applicationProperties;
    private final XpressBeesClient xpressBeesClient;
    private final ObjectMapper objectMapper;

    public OrderReturnService(
        OrderReturnRepository orderReturnRepository,
        OrderReturnItemRepository orderReturnItemRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderItemRepository orderItemRepository,
        ProductRepository productRepository,
        RazorpayRefundService razorpayRefundService,
        LedgerlyIntegrationClient ledgerlyIntegrationClient,
        ZohoBooksIntegrationClient zohoBooksIntegrationClient,
        ProductVariantSupport variantSupport,
        OrderMailService orderMailService,
        OrderSmsService orderSmsService,
        ApplicationProperties applicationProperties,
        XpressBeesClient xpressBeesClient,
        ObjectMapper objectMapper
    ) {
        this.orderReturnRepository = orderReturnRepository;
        this.orderReturnItemRepository = orderReturnItemRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.razorpayRefundService = razorpayRefundService;
        this.ledgerlyIntegrationClient = ledgerlyIntegrationClient;
        this.zohoBooksIntegrationClient = zohoBooksIntegrationClient;
        this.variantSupport = variantSupport;
        this.orderMailService = orderMailService;
        this.orderSmsService = orderSmsService;
        this.applicationProperties = applicationProperties;
        this.xpressBeesClient = xpressBeesClient;
        this.objectMapper = objectMapper;
    }

    public List<OrderReturn> listForUser(String login) {
        return orderReturnRepository.findAllByUserLoginOrderByRequestedAtDesc(login);
    }

    public List<OrderReturn> listForAdmin(OrderReturnStatus statusFilter) {
        if (statusFilter == null) {
            return orderReturnRepository.findAllByStatusInOrderByRequestedAtDesc(List.of(OrderReturnStatus.values()));
        }
        return orderReturnRepository.findAllByStatusInOrderByRequestedAtDesc(List.of(statusFilter));
    }

    @Transactional(readOnly = true)
    public List<OrderReturnItem> returnLineBreakdown(OrderReturn orderReturn) {
        return orderReturn == null || orderReturn.getId() == null
            ? List.of()
            : orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId());
    }

    // ---------------------------------------------------------------------
    // Request creation (Return / Exchange)
    // ---------------------------------------------------------------------

    public OrderReturn requestReturn(String userLogin, String orderNumber, String reason) {
        return requestReturn(userLogin, orderNumber, reason, List.of());
    }

    public OrderReturn requestReturn(String userLogin, String orderNumber, String reason, List<ReturnLineSelection> requestedLines) {
        assertFeatureEnabled();
        String normalizedReason = normalizeReason(reason, "return");
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, userLogin)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        assertReturnEligible(order);
        if (orderReturnRepository.existsByOrderIdAndStatusIn(order.getId(), OPEN_RETURN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A return or exchange request is already open for this order");
        }
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber(generateReturnNumber(order.getOrderNumber()));
        orderReturn.setStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setRequestType(OrderRequestType.RETURN);
        orderReturn.setReason(normalizedReason);
        orderReturn.setRequestedAt(Instant.now());
        orderReturn.setOrder(order);
        orderReturn.setUserLogin(userLogin);
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        createReturnLineSnapshots(saved, order, requestedLines);
        queueReturnEmail(order, saved);
        return saved;
    }

    private void createReturnLineSnapshots(OrderReturn orderReturn, CustomerOrder order, List<ReturnLineSelection> requestedLines) {
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
        if (orderItems.isEmpty()) {
            orderReturn.setRefundAmount(money(order.getTotal()));
            return;
        }
        Map<Long, Integer> requestedByItem = new LinkedHashMap<>();
        if (requestedLines == null || requestedLines.isEmpty()) {
            orderItems.forEach(item -> requestedByItem.put(item.getId(), Math.max(1, Optional.ofNullable(item.getQty()).orElse(1))));
        } else {
            for (ReturnLineSelection line : requestedLines) {
                if (line == null || line.orderItemId() == null || line.quantity() == null || line.quantity() < 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each return line needs an order item and positive quantity");
                }
                if (requestedByItem.putIfAbsent(line.orderItemId(), line.quantity()) != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A return line was selected more than once");
                }
            }
        }

        BigDecimal subtotal = positiveOrFallback(order.getSubtotal(), orderItems
            .stream()
            .map(item -> money(item.getPrice()).multiply(BigDecimal.valueOf(Optional.ofNullable(item.getQty()).orElse(0))))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal discount = money(order.getDiscount());
        BigDecimal totalRefund = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> selected : requestedByItem.entrySet()) {
            OrderItem item = orderItems
                .stream()
                .filter(candidate -> candidate.getId().equals(selected.getKey()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected return item is not part of this order"));
            int orderedQty = Math.max(1, Optional.ofNullable(item.getQty()).orElse(1));
            if (selected.getValue() > orderedQty) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return quantity exceeds the ordered quantity");
            }
            BigDecimal gross = money(item.getPrice()).multiply(BigDecimal.valueOf(selected.getValue())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal discountAllocation = subtotal.signum() == 0
                ? BigDecimal.ZERO
                : discount.multiply(gross).divide(subtotal, 2, RoundingMode.HALF_UP).min(gross);
            BigDecimal refund = gross.subtract(discountAllocation).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            OrderReturnItem returnItem = new OrderReturnItem();
            returnItem.setOrderReturn(orderReturn);
            returnItem.setOrderItem(item);
            returnItem.setRequestedQty(selected.getValue());
            returnItem.setUnitAmount(money(item.getPrice()));
            returnItem.setGrossAmount(gross);
            returnItem.setDiscountAllocation(discountAllocation);
            returnItem.setRefundAmount(refund);
            orderReturnItemRepository.save(returnItem);
            totalRefund = totalRefund.add(refund);
        }
        orderReturn.setRefundAmount(totalRefund.setScale(2, RoundingMode.HALF_UP));
    }

    public OrderReturn requestExchange(
        String userLogin,
        String orderNumber,
        Long orderItemId,
        String reason,
        String replacementSize,
        String replacementColor
    ) {
        assertFeatureEnabled();
        String normalizedReason = normalizeReason(reason, "exchange");
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, userLogin)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        assertReturnEligible(order);
        OrderItem item = orderItemRepository
            .findByIdAndOrderId(orderItemId, order.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found"));
        Product product = resolveProduct(item.getProductId());
        if (product == null || !variantSupport.isVariants(product)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This item does not support exchanges");
        }
        ProductVariantSupport.VariantRow replacement = variantSupport
            .resolveSellable(product, null, replacementSize, replacementColor)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected size/colour is currently unavailable for exchange")
            );
        if (orderReturnRepository.existsByOrderIdAndRequestTypeAndStatusIn(order.getId(), OrderRequestType.RETURN, OPEN_RETURN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An open return already exists for this order");
        }
        if (orderReturnRepository.existsByOrderIdAndOrderItemIdAndStatusIn(order.getId(), item.getId(), OPEN_RETURN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An exchange request is already open for this item");
        }
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber(generateReturnNumber(order.getOrderNumber()));
        orderReturn.setStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setReason(normalizedReason);
        orderReturn.setRequestedAt(Instant.now());
        orderReturn.setOrder(order);
        orderReturn.setUserLogin(userLogin);
        orderReturn.setOrderItem(item);
        orderReturn.setReplacementVariantSku(replacement.sku());
        orderReturn.setReplacementSize(replacementSize);
        orderReturn.setReplacementColor(replacementColor);
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(order, saved);
        return saved;
    }

    public boolean isReplacementSellable(String userLogin, Long orderItemId, String size, String color) {
        OrderItem item = orderItemRepository.findById(orderItemId).orElse(null);
        if (item == null || item.getOrder() == null || !userLogin.equals(item.getOrder().getUserLogin())) {
            return false;
        }
        Product product = resolveProduct(item.getProductId());
        if (product == null || !variantSupport.isVariants(product)) {
            return false;
        }
        return variantSupport.resolveSellable(product, null, size, color).isPresent();
    }

    public OrderReturn cancelByCustomer(String userLogin, String returnNumber) {
        OrderReturn orderReturn = loadOwnedReturn(userLogin, returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending return requests can be cancelled");
        }
        orderReturn.setStatus(OrderReturnStatus.CANCELLED);
        orderReturn.setResolvedAt(Instant.now());
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    // ---------------------------------------------------------------------
    // Admin review
    // ---------------------------------------------------------------------

    public OrderReturn approve(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return is not awaiting approval");
        }
        if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
            reserveExchangeReplacement(orderReturn);
        }
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        orderReturn.setAdminNotes(trimNotes(adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    private void reserveExchangeReplacement(OrderReturn orderReturn) {
        OrderItem item = orderReturn.getOrderItem();
        Product product = resolveProductForUpdate(item.getProductId()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Original product could not be resolved")
        );
        ProductVariantSupport.VariantRow replacement = variantSupport
            .resolveSellable(product, orderReturn.getReplacementVariantSku(), orderReturn.getReplacementSize(), orderReturn.getReplacementColor())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Replacement is no longer available"));
        int qty = Math.max(1, Optional.ofNullable(item.getQty()).orElse(1));
        if (!variantSupport.decrementVariantStock(product, replacement.sku(), replacement.size(), replacement.color(), qty)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Replacement is no longer in stock");
        }
        productRepository.save(product);
        BigDecimal originalGross = money(item.getPrice()).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal orderSubtotal = positiveOrFallback(orderReturn.getOrder().getSubtotal(), originalGross);
        BigDecimal allocatedDiscount = money(orderReturn.getOrder().getDiscount())
            .multiply(originalGross)
            .divide(orderSubtotal, 2, RoundingMode.HALF_UP)
            .min(originalGross);
        BigDecimal originalAmount = originalGross.subtract(allocatedDiscount).max(BigDecimal.ZERO);
        BigDecimal replacementAmount = money(variantSupport.variantSellingPrice(replacement))
            .multiply(BigDecimal.valueOf(qty))
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal difference = replacementAmount.subtract(originalAmount).setScale(2, RoundingMode.HALF_UP);
        orderReturn.setOriginalItemAmount(originalAmount);
        orderReturn.setReplacementItemAmount(replacementAmount);
        orderReturn.setPriceDifference(difference);
        orderReturn.setSettlementStatus(difference.signum() == 0 ? ExchangeSettlementStatus.NOT_REQUIRED : ExchangeSettlementStatus.PENDING);
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setReplacementReservationReleasedAt(null);
    }

    public OrderReturn reject(String returnNumber, String rejectionReason, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return is not awaiting approval");
        }
        String reason = validateRejectionReason(rejectionReason);
        orderReturn.setStatus(OrderReturnStatus.REJECTED);
        orderReturn.setRejectionReason(reason);
        orderReturn.setAdminNotes(trimNotes(adminNotes));
        orderReturn.setResolvedAt(Instant.now());
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    /** Rejects a request regardless of whether it's still awaiting approval or already inspected. */
    public OrderReturn rejectAtCurrentStage(String returnNumber, String rejectionReason, String adminNotes) {
        OrderReturn orderReturn = loadReturn(returnNumber);
        return switch (orderReturn.getStatus()) {
            case REQUESTED -> reject(returnNumber, rejectionReason, adminNotes);
            case INSPECTED -> rejectAfterInspection(returnNumber, rejectionReason, adminNotes);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return cannot be rejected from its current status");
        };
    }

    public OrderReturn rejectAfterInspection(String returnNumber, String rejectionReason, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.INSPECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return must be inspected before it can be rejected");
        }
        String reason = validateRejectionReason(rejectionReason);
        orderReturn.setStatus(OrderReturnStatus.REJECTED);
        orderReturn.setRejectionReason(reason);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        orderReturn.setResolvedAt(Instant.now());
        releaseExchangeReservation(orderReturn);
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn bookPickup(
        String returnNumber,
        ReturnLogisticsMode mode,
        String carrierName,
        String reverseAwb,
        String trackingUrl,
        String pickupWindow,
        String adminNotes
    ) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return must be approved before booking pickup");
        }
        if (mode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logistics mode is required");
        }
        if (mode == ReturnLogisticsMode.XPRESSBEES) {
            return bookExpressBeesPickup(orderReturn, reverseAwb, adminNotes);
        }
        if (StringUtils.isBlank(reverseAwb)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reverse AWB or tracking reference is required");
        }
        orderReturn.setLogisticsMode(mode);
        orderReturn.setCarrierName(StringUtils.left(StringUtils.trimToNull(carrierName), 80));
        orderReturn.setReverseAwb(StringUtils.left(StringUtils.trimToNull(reverseAwb), 80));
        orderReturn.setTrackingUrl(StringUtils.left(StringUtils.trimToNull(trackingUrl), 500));
        orderReturn.setPickupWindow(StringUtils.left(StringUtils.trimToNull(pickupWindow), 200));
        orderReturn.setPickupFailureReason(null);
        orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn ensurePickupScheduled(String returnNumber, String adminNotes) {
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() == OrderReturnStatus.PICKUP_SCHEDULED) {
            return orderReturn;
        }
        if (orderReturn.getStatus() != OrderReturnStatus.APPROVED && orderReturn.getStatus() != OrderReturnStatus.PICKUP_FAILED) {
            return orderReturn;
        }
        orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
        orderReturn.setPickupFailureReason(null);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    private OrderReturn bookExpressBeesPickup(OrderReturn orderReturn, String preAssignedAwb, String adminNotes) {
        if (!xpressBeesClient.isReversePickupConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ExpressBees reverse pickup is not configured. Set XPRESSBEES credentials, business account, warehouse address and reverse manifest path, or book pickup using manual/self-ship mode."
            );
        }
        try {
            XpressBeesClient.ReversePickupResult result = xpressBeesClient.createReversePickup(buildReversePickupRequest(orderReturn, preAssignedAwb));
            orderReturn.setLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
            orderReturn.setCarrierName("XpressBees");
            orderReturn.setReverseAwb(StringUtils.left(result.awb(), 80));
            String tracking = StringUtils.defaultIfBlank(result.trackingUrl(), xpressBeesClient.buildTrackingUrl(result.awb()));
            orderReturn.setTrackingUrl(StringUtils.left(StringUtils.trimToNull(tracking), 500));
            orderReturn.setPickupWindow(StringUtils.left(StringUtils.trimToNull(result.pickupWindow()), 200));
            orderReturn.setProviderRequestId(StringUtils.left(StringUtils.trimToNull(result.providerRequestId()), 120));
            orderReturn.setPickupFailureReason(null);
            orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
            orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
            OrderReturn saved = orderReturnRepository.save(orderReturn);
            queueReturnEmail(saved.getOrder(), saved);
            return saved;
        } catch (XpressBeesClient.XpressBeesApiException ex) {
            LOG.warn("ExpressBees reverse pickup failed for {}: {}", orderReturn.getReturnNumber(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    private XpressBeesClient.ReversePickupRequest buildReversePickupRequest(OrderReturn orderReturn, String preAssignedAwb) {
        CustomerOrder order = orderReturn.getOrder();
        JsonNode shipping = parseShippingAddress(order.getShippingAddressJson());
        ApplicationProperties.XpressBees xpressBees = applicationProperties.getXpressbees();
        XpressBeesClient.ReversePickupAddress pickupFrom = new XpressBeesClient.ReversePickupAddress(
            firstAddressField(shipping, "full_name", "name"),
            firstAddressField(shipping, "phone", "mobile"),
            firstAddressField(shipping, "email"),
            firstAddressField(shipping, "line1", "address_line1", "address"),
            firstAddressField(shipping, "line2", "address_line2"),
            firstAddressField(shipping, "city"),
            firstAddressField(shipping, "state"),
            firstAddressField(shipping, "pincode", "postal_code", "zip")
        );
        XpressBeesClient.ReversePickupAddress deliverTo = new XpressBeesClient.ReversePickupAddress(
            StringUtils.defaultIfBlank(xpressBees.getWarehouseName(), "Mini's n More"),
            StringUtils.defaultIfBlank(xpressBees.getWarehousePhone(), pickupFrom.phone()),
            null,
            xpressBees.getWarehouseLine1(),
            xpressBees.getWarehouseLine2(),
            xpressBees.getWarehouseCity(),
            xpressBees.getWarehouseState(),
            xpressBees.getOriginPincode()
        );
        List<XpressBeesClient.ReversePickupLine> lines = buildReversePickupLines(orderReturn, order);
        return new XpressBeesClient.ReversePickupRequest(
            orderReturn.getReturnNumber(),
            order.getOrderNumber(),
            pickupFrom,
            deliverTo,
            lines,
            StringUtils.trimToNull(preAssignedAwb)
        );
    }

    private List<XpressBeesClient.ReversePickupLine> buildReversePickupLines(OrderReturn orderReturn, CustomerOrder order) {
        List<XpressBeesClient.ReversePickupLine> lines = new ArrayList<>();
        if (orderReturn.getRequestType() == OrderRequestType.RETURN && orderReturn.getId() != null) {
            for (OrderReturnItem returnItem : orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId())) {
                OrderItem selected = returnItem.getOrderItem();
                lines.add(
                    new XpressBeesClient.ReversePickupLine(
                        StringUtils.defaultIfBlank(selected.getProductName(), "Return item"),
                        StringUtils.defaultIfBlank(selected.getVariantSku(), selected.getProductSlug()),
                        returnItem.getRequestedQty(),
                        returnItem.getUnitAmount(),
                        Math.max(1, applicationProperties.getXpressbees().getDefaultWeightGrams())
                    )
                );
            }
            if (!lines.isEmpty()) {
                return lines;
            }
        }
        OrderItem item = orderReturn.getOrderItem();
        if (item != null) {
            lines.add(
                new XpressBeesClient.ReversePickupLine(
                    StringUtils.defaultIfBlank(item.getProductName(), "Return item"),
                    StringUtils.defaultIfBlank(item.getVariantSku(), item.getProductSlug()),
                    Math.max(1, item.getQty() == null ? 1 : item.getQty()),
                    item.getPrice(),
                    Math.max(1, applicationProperties.getXpressbees().getDefaultWeightGrams())
                )
            );
            return lines;
        }
        for (OrderItem orderItem : order.getItemses()) {
            lines.add(
                new XpressBeesClient.ReversePickupLine(
                    StringUtils.defaultIfBlank(orderItem.getProductName(), "Return item"),
                    StringUtils.defaultIfBlank(orderItem.getVariantSku(), orderItem.getProductSlug()),
                    Math.max(1, orderItem.getQty() == null ? 1 : orderItem.getQty()),
                    orderItem.getPrice(),
                    Math.max(1, applicationProperties.getXpressbees().getDefaultWeightGrams())
                )
            );
        }
        if (lines.isEmpty()) {
            lines.add(
                new XpressBeesClient.ReversePickupLine(
                    "Return item",
                    orderReturn.getReturnNumber(),
                    1,
                    order.getTotal(),
                    Math.max(1, applicationProperties.getXpressbees().getDefaultWeightGrams())
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
            LOG.debug("Could not parse shipping address JSON for reverse pickup: {}", ex.getMessage());
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

    public OrderReturn markPickupFailed(String returnNumber, String failureReason, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (
            orderReturn.getStatus() != OrderReturnStatus.APPROVED &&
            orderReturn.getStatus() != OrderReturnStatus.PICKUP_SCHEDULED &&
            orderReturn.getStatus() != OrderReturnStatus.PICKUP_REQUESTED
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pickup must be scheduled before marking a failure");
        }
        orderReturn.setStatus(OrderReturnStatus.PICKUP_FAILED);
        orderReturn.setPickupFailureReason(StringUtils.left(StringUtils.normalizeSpace(StringUtils.trimToEmpty(failureReason)), 500));
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        queueAdminPickupFailedEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn reschedulePickup(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.PICKUP_FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return is not in pickup failed state");
        }
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        orderReturn.setPickupFailureReason(null);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        return orderReturnRepository.save(orderReturn);
    }

    public OrderReturn closePickupFailed(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.PICKUP_FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only a failed pickup can be closed");
        }
        releaseExchangeReservation(orderReturn);
        orderReturn.setStatus(OrderReturnStatus.CANCELLED);
        orderReturn.setResolvedAt(Instant.now());
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn markPickupRequested(String returnNumber, String adminNotes) {
        OrderReturn saved = transition(
            returnNumber,
            OrderReturnStatus.APPROVED,
            OrderReturnStatus.PICKUP_REQUESTED,
            adminNotes,
            "Return must be approved before scheduling pickup"
        );
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn markPickedUp(String returnNumber, String adminNotes) {
        return transition(
            returnNumber,
            EnumSet.of(OrderReturnStatus.APPROVED, OrderReturnStatus.PICKUP_REQUESTED, OrderReturnStatus.PICKUP_SCHEDULED),
            OrderReturnStatus.PICKED_UP,
            adminNotes,
            "Pickup must be scheduled before marking picked up"
        );
    }

    public OrderReturn markReceived(String returnNumber, String adminNotes) {
        OrderReturn saved = transition(
            returnNumber,
            EnumSet.of(
                OrderReturnStatus.APPROVED,
                OrderReturnStatus.PICKUP_REQUESTED,
                OrderReturnStatus.PICKUP_SCHEDULED,
                OrderReturnStatus.PICKED_UP
            ),
            OrderReturnStatus.RECEIVED,
            adminNotes,
            "Return must be approved (or already picked up) before marking received"
        );
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn markInspected(String returnNumber, String adminNotes) {
        return transition(
            returnNumber,
            OrderReturnStatus.RECEIVED,
            OrderReturnStatus.INSPECTED,
            adminNotes,
            "Return must be received before inspection"
        );
    }

    public OrderReturn acceptAfterInspection(String returnNumber, ReturnDisposition disposition, String adminNotes) {
        return acceptAfterInspection(returnNumber, disposition, List.of(), adminNotes);
    }

    public OrderReturn acceptAfterInspection(
        String returnNumber,
        ReturnDisposition disposition,
        List<InspectionLineDecision> lineDecisions,
        String adminNotes
    ) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.INSPECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return must be inspected before accepting");
        }
        if (disposition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Disposition is required when accepting a return");
        }
        orderReturn.setDisposition(disposition);
        if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
            if (disposition == ReturnDisposition.RESTOCKABLE) {
                restoreItemStock(orderReturn.getOrderItem());
            }
        } else {
            applyReturnLineInspection(orderReturn, disposition, lineDecisions);
        }
        orderReturn.setStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        return orderReturnRepository.save(orderReturn);
    }

    private void applyReturnLineInspection(
        OrderReturn orderReturn,
        ReturnDisposition defaultDisposition,
        List<InspectionLineDecision> decisions
    ) {
        List<OrderReturnItem> lines = orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId());
        if (lines.isEmpty()) {
            if (defaultDisposition == ReturnDisposition.RESTOCKABLE) {
                restoreInventoryForOrder(orderReturn.getOrder());
            }
            return;
        }
        Map<Long, InspectionLineDecision> decisionsById = new LinkedHashMap<>();
        if (decisions != null) {
            decisions.forEach(decision -> {
                if (decision != null && decision.returnItemId() != null) {
                    decisionsById.put(decision.returnItemId(), decision);
                }
            });
        }
        BigDecimal acceptedRefund = BigDecimal.ZERO;
        for (OrderReturnItem line : lines) {
            InspectionLineDecision decision = decisionsById.get(line.getId());
            int acceptedQty = decision == null || decision.acceptedQuantity() == null
                ? line.getRequestedQty()
                : decision.acceptedQuantity();
            if (acceptedQty < 0 || acceptedQty > line.getRequestedQty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accepted quantity must be within the requested quantity");
            }
            ReturnDisposition lineDisposition = decision != null && decision.disposition() != null
                ? decision.disposition()
                : defaultDisposition;
            if (acceptedQty > 0 && lineDisposition == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Disposition is required for every accepted line");
            }
            line.setReceivedQty(line.getRequestedQty());
            line.setAcceptedQty(acceptedQty);
            line.setDisposition(acceptedQty == 0 ? null : lineDisposition);
            BigDecimal lineRefund = line.getRefundAmount()
                .multiply(BigDecimal.valueOf(acceptedQty))
                .divide(BigDecimal.valueOf(line.getRequestedQty()), 2, RoundingMode.HALF_UP);
            line.setRefundAmount(lineRefund);
            if (acceptedQty > 0 && lineDisposition == ReturnDisposition.RESTOCKABLE) {
                restoreItemStock(line.getOrderItem(), acceptedQty);
            }
            acceptedRefund = acceptedRefund.add(lineRefund);
        }
        if (acceptedRefund.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one returned unit must be accepted");
        }
        orderReturnItemRepository.saveAll(lines);
        orderReturn.setRefundAmount(acceptedRefund.setScale(2, RoundingMode.HALF_UP));
    }

    // ---------------------------------------------------------------------
    // Exchange-only fulfilment
    // ---------------------------------------------------------------------

    public OrderReturn settlePriceDifference(
        String returnNumber,
        ExchangeSettlementStatus settlementStatus,
        BigDecimal amount,
        String method,
        String reference,
        String settledBy,
        String adminNotes
    ) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        assertIsExchange(orderReturn);
        if (orderReturn.getStatus() != OrderReturnStatus.APPROVED && orderReturn.getStatus() != OrderReturnStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price difference can only be settled for an approved exchange");
        }
        BigDecimal difference = money(orderReturn.getPriceDifference());
        ExchangeSettlementStatus expected = difference.signum() > 0
            ? ExchangeSettlementStatus.COLLECTED
            : difference.signum() < 0 ? ExchangeSettlementStatus.REFUNDED : ExchangeSettlementStatus.NOT_REQUIRED;
        if (settlementStatus == null || (settlementStatus != expected && settlementStatus != ExchangeSettlementStatus.WAIVED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement action does not match the exchange price difference");
        }
        BigDecimal expectedAmount = difference.abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal recordedAmount = settlementStatus == ExchangeSettlementStatus.WAIVED ? BigDecimal.ZERO : money(amount);
        if (settlementStatus != ExchangeSettlementStatus.WAIVED && recordedAmount.compareTo(expectedAmount) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement amount must equal the price difference");
        }
        if (settlementStatus != ExchangeSettlementStatus.WAIVED && StringUtils.isAnyBlank(method, reference)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement method and transaction reference are required");
        }
        orderReturn.setSettlementStatus(settlementStatus);
        orderReturn.setSettlementAmount(recordedAmount);
        orderReturn.setSettlementMethod(StringUtils.left(StringUtils.trimToNull(method), 40));
        orderReturn.setSettlementReference(StringUtils.left(StringUtils.trimToNull(reference), 160));
        orderReturn.setSettledAt(Instant.now());
        orderReturn.setSettledBy(StringUtils.left(StringUtils.defaultIfBlank(settledBy, "system"), 50));
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        return orderReturnRepository.save(orderReturn);
    }

    public OrderReturn allocateReplacementItem(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        assertIsExchange(orderReturn);
        if (orderReturn.getStatus() != OrderReturnStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exchange must be accepted before allocating the replacement");
        }
        // Compatibility for exchanges accepted before approval-time reservations were introduced.
        if (orderReturn.getReplacementReservedAt() == null) {
            OrderItem item = orderReturn.getOrderItem();
            Product product = resolveProductForUpdate(item.getProductId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Original product could not be resolved")
            );
            int qty = Optional.ofNullable(item.getQty()).orElse(1);
            if (
                !variantSupport.decrementVariantStock(
                    product,
                    orderReturn.getReplacementVariantSku(),
                    orderReturn.getReplacementSize(),
                    orderReturn.getReplacementColor(),
                    qty
                )
            ) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Replacement is no longer in stock; convert this exchange to a refund instead."
                );
            }
            productRepository.save(product);
            orderReturn.setReplacementReservedAt(Instant.now());
        } else if (orderReturn.getReplacementReservationReleasedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Replacement stock reservation was released");
        }
        if (orderReturn.getSettlementStatus() == ExchangeSettlementStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Settle the exchange price difference before allocation");
        }
        orderReturn.setStatus(OrderReturnStatus.ITEM_ALLOCATED);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        return orderReturnRepository.save(orderReturn);
    }

    private void releaseExchangeReservation(OrderReturn orderReturn) {
        if (
            orderReturn.getRequestType() != OrderRequestType.EXCHANGE ||
            orderReturn.getReplacementReservedAt() == null ||
            orderReturn.getReplacementReservationReleasedAt() != null
        ) {
            return;
        }
        OrderItem item = orderReturn.getOrderItem();
        Product product = item == null ? null : resolveProductForUpdate(item.getProductId()).orElse(null);
        if (product != null) {
            int qty = Math.max(1, Optional.ofNullable(item.getQty()).orElse(1));
            boolean restored = variantSupport.incrementVariantStock(
                product,
                orderReturn.getReplacementVariantSku(),
                orderReturn.getReplacementSize(),
                orderReturn.getReplacementColor(),
                qty
            );
            if (!restored) {
                product.setStock(Optional.ofNullable(product.getStock()).orElse(0) + qty);
            }
            productRepository.save(product);
        }
        orderReturn.setReplacementReservationReleasedAt(Instant.now());
    }

    public OrderReturn convertExchangeToRefund(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        assertIsExchange(orderReturn);
        if (orderReturn.getStatus() != OrderReturnStatus.ACCEPTED && orderReturn.getStatus() != OrderReturnStatus.FAILED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Exchange must be accepted (or retried after a failed refund) before converting to a refund"
            );
        }
        releaseExchangeReservation(orderReturn);
        OrderItem item = orderReturn.getOrderItem();
        BigDecimal itemAmount = orderReturn.getOriginalItemAmount() == null
            ? item.getPrice().multiply(BigDecimal.valueOf(Optional.ofNullable(item.getQty()).orElse(1)))
            : orderReturn.getOriginalItemAmount();
        if (orderReturn.getSettlementStatus() == ExchangeSettlementStatus.COLLECTED) {
            itemAmount = itemAmount.add(money(orderReturn.getSettlementAmount()));
        }
        return executeRefund(orderReturn, itemAmount, adminNotes);
    }

    public OrderReturn markRepacking(String returnNumber, String adminNotes) {
        return transition(
            returnNumber,
            OrderReturnStatus.ITEM_ALLOCATED,
            OrderReturnStatus.REPACKING,
            adminNotes,
            "Replacement must be allocated before repacking"
        );
    }

    public OrderReturn markReadyToShip(String returnNumber, String adminNotes) {
        return transition(
            returnNumber,
            OrderReturnStatus.REPACKING,
            OrderReturnStatus.READY_TO_SHIP,
            adminNotes,
            "Item must be repacked before it is ready to ship"
        );
    }

    public OrderReturn bookOutbound(
        String returnNumber,
        ReturnLogisticsMode mode,
        String carrierName,
        String outboundAwb,
        String trackingUrl,
        String labelUrl,
        String adminNotes
    ) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        assertIsExchange(orderReturn);
        if (orderReturn.getStatus() != OrderReturnStatus.READY_TO_SHIP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exchange replacement must be ready to ship before booking outbound");
        }
        if (orderReturn.getSettlementStatus() == ExchangeSettlementStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Settle the exchange price difference before booking outbound");
        }
        if (mode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logistics mode is required");
        }
        if (mode == ReturnLogisticsMode.XPRESSBEES) {
            return bookExpressBeesOutbound(orderReturn, outboundAwb, adminNotes);
        }
        if (StringUtils.isBlank(outboundAwb)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Outbound AWB or tracking reference is required");
        }
        orderReturn.setOutboundLogisticsMode(mode);
        orderReturn.setOutboundCarrierName(StringUtils.left(StringUtils.trimToNull(carrierName), 80));
        orderReturn.setOutboundAwb(StringUtils.left(StringUtils.trimToNull(outboundAwb), 80));
        orderReturn.setOutboundTrackingUrl(StringUtils.left(StringUtils.trimToNull(trackingUrl), 500));
        orderReturn.setOutboundLabelUrl(StringUtils.left(StringUtils.trimToNull(labelUrl), 500));
        orderReturn.setStatus(OrderReturnStatus.SHIPPED);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        return orderReturnRepository.save(orderReturn);
    }

    private OrderReturn bookExpressBeesOutbound(OrderReturn orderReturn, String preAssignedAwb, String adminNotes) {
        if (!xpressBeesClient.isForwardShipmentConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ExpressBees forward shipment is not configured. Set XPRESSBEES credentials, business account, warehouse address and forward manifest path, or book outbound using manual mode."
            );
        }
        try {
            XpressBeesClient.ForwardShipmentResult result = xpressBeesClient.createForwardShipment(
                buildForwardShipmentRequest(orderReturn, preAssignedAwb)
            );
            orderReturn.setOutboundLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
            orderReturn.setOutboundCarrierName("XpressBees");
            orderReturn.setOutboundAwb(StringUtils.left(result.awb(), 80));
            String tracking = StringUtils.defaultIfBlank(result.trackingUrl(), xpressBeesClient.buildTrackingUrl(result.awb()));
            orderReturn.setOutboundTrackingUrl(StringUtils.left(StringUtils.trimToNull(tracking), 500));
            orderReturn.setOutboundLabelUrl(StringUtils.left(StringUtils.trimToNull(result.labelUrl()), 500));
            orderReturn.setOutboundProviderRequestId(StringUtils.left(StringUtils.trimToNull(result.providerRequestId()), 120));
            orderReturn.setStatus(OrderReturnStatus.SHIPPED);
            orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
            return orderReturnRepository.save(orderReturn);
        } catch (XpressBeesClient.XpressBeesApiException ex) {
            LOG.warn("ExpressBees forward shipment failed for {}: {}", orderReturn.getReturnNumber(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    private XpressBeesClient.ForwardShipmentRequest buildForwardShipmentRequest(OrderReturn orderReturn, String preAssignedAwb) {
        CustomerOrder order = orderReturn.getOrder();
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
        List<XpressBeesClient.ReversePickupLine> lines = buildReplacementShipmentLines(orderReturn);
        return new XpressBeesClient.ForwardShipmentRequest(
            orderReturn.getReturnNumber() + "-OUT",
            order.getOrderNumber(),
            shipFrom,
            deliverTo,
            lines,
            StringUtils.trimToNull(preAssignedAwb)
        );
    }

    private List<XpressBeesClient.ReversePickupLine> buildReplacementShipmentLines(OrderReturn orderReturn) {
        OrderItem item = orderReturn.getOrderItem();
        String productName = item != null ? StringUtils.defaultIfBlank(item.getProductName(), "Replacement item") : "Replacement item";
        String sku = StringUtils.defaultIfBlank(
            orderReturn.getReplacementVariantSku(),
            item != null ? StringUtils.defaultIfBlank(item.getVariantSku(), item.getProductSlug()) : orderReturn.getReturnNumber()
        );
        int qty = item != null ? Math.max(1, item.getQty() == null ? 1 : item.getQty()) : 1;
        BigDecimal price = item != null ? item.getPrice() : orderReturn.getOrder().getTotal();
        return List.of(
            new XpressBeesClient.ReversePickupLine(
                productName,
                sku,
                qty,
                price,
                Math.max(1, applicationProperties.getXpressbees().getDefaultWeightGrams())
            )
        );
    }

    public OrderReturn markShipped(String returnNumber, String adminNotes) {
        return transition(
            returnNumber,
            OrderReturnStatus.READY_TO_SHIP,
            OrderReturnStatus.SHIPPED,
            adminNotes,
            "Item must be ready to ship before marking shipped"
        );
    }

    public OrderReturn markDelivered(String returnNumber, String adminNotes) {
        return transition(
            returnNumber,
            OrderReturnStatus.SHIPPED,
            OrderReturnStatus.DELIVERED,
            adminNotes,
            "Item must be shipped before marking delivered"
        );
    }

    public OrderReturn completeExchange(String returnNumber, String adminNotes) {
        return transition(
            returnNumber,
            OrderReturnStatus.DELIVERED,
            OrderReturnStatus.COMPLETED,
            adminNotes,
            "Item must be delivered before completing the exchange"
        );
    }

    // ---------------------------------------------------------------------
    // Refund (Return: whole order; Exchange: converted item-level refund)
    // ---------------------------------------------------------------------

    public OrderReturn processRefund(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use convert-to-refund for exchange requests");
        }
        if (orderReturn.getStatus() != OrderReturnStatus.ACCEPTED && orderReturn.getStatus() != OrderReturnStatus.FAILED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Return must be accepted (or retry after a failed refund) before processing refund"
            );
        }
        BigDecimal refundAmount = orderReturnItemRepository
            .findAllByOrderReturnIdOrderById(orderReturn.getId())
            .stream()
            .map(OrderReturnItem::getRefundAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refundAmount.signum() <= 0) {
            refundAmount = money(orderReturn.getRefundAmount());
        }
        if (refundAmount.signum() <= 0 && orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId()).isEmpty()) {
            refundAmount = money(orderReturn.getOrder().getTotal());
        }
        if (refundAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return has no refundable accepted lines");
        }
        return executeRefund(orderReturn, refundAmount.setScale(2, RoundingMode.HALF_UP), adminNotes);
    }

    public OrderReturn recordManualCodRefund(
        String returnNumber,
        ManualRefundMethod method,
        String reference,
        String adminNotes,
        String recordedBy
    ) {
        assertFeatureEnabled();
        if (method == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund method is required");
        }
        OrderReturn orderReturn = loadReturn(returnNumber);
        CustomerOrder order = orderReturn.getOrder();
        if (!isCollectedCod(order)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manual COD refund recording applies only to collected COD orders");
        }
        if (orderReturn.getManualRefundRecordedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COD refund already recorded for this case");
        }
        if (orderReturn.getStatus() != OrderReturnStatus.ACCEPTED && orderReturn.getStatus() != OrderReturnStatus.FAILED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Return must be accepted (or retry after a failed refund) before recording COD refund"
            );
        }
        BigDecimal refundAmount = resolveRefundAmount(orderReturn);
        orderReturn.setManualRefundMethod(method);
        orderReturn.setManualRefundReference(validateManualRefundReference(reference));
        orderReturn.setManualRefundRecordedAt(Instant.now());
        orderReturn.setManualRefundRecordedBy(StringUtils.left(StringUtils.trimToEmpty(recordedBy), 50));
        return finalizeRefund(orderReturn, refundAmount, adminNotes);
    }

    private OrderReturn executeRefund(OrderReturn orderReturn, BigDecimal refundAmount, String adminNotes) {
        CustomerOrder order = orderReturn.getOrder();
        if (isUncollectedCod(order)) {
            return finalizeRefund(orderReturn, refundAmount, adminNotes);
        }
        if (isCollectedCod(order)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Collected COD refund must be recorded with refund method and payment reference"
            );
        }
        String existingRefundId = StringUtils.trimToNull(orderReturn.getRazorpayRefundId());
        try {
            if (existingRefundId == null) {
                String refundId = razorpayRefundService.refundPayment(order, refundAmount);
                orderReturn.setRazorpayRefundId(refundId);
            }
            return finalizeRefund(orderReturn, refundAmount, adminNotes);
        } catch (RuntimeException e) {
            if (existingRefundId == null) {
                orderReturn.setStatus(OrderReturnStatus.FAILED);
                orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), "Refund failed: " + StringUtils.left(e.getMessage(), 500)));
                OrderReturn failedSaved = orderReturnRepository.save(orderReturn);
                queueReturnEmail(order, failedSaved);
            }
            throw e;
        }
    }

    private OrderReturn finalizeRefund(OrderReturn orderReturn, BigDecimal refundAmount, String adminNotes) {
        CustomerOrder order = orderReturn.getOrder();
        orderReturn.setRefundAmount(refundAmount);
        if (orderReturn.getRequestType() == OrderRequestType.RETURN && order.getPaymentStatus() != PaymentStatus.REFUNDED) {
            PaymentStatus nextPaymentStatus = isUncollectedCod(order)
                ? order.getPaymentStatus()
                : refundAmount.compareTo(money(order.getTotal())) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
            if (nextPaymentStatus != order.getPaymentStatus()) {
                order.setPaymentStatus(nextPaymentStatus);
                customerOrderRepository.save(order);
            }
        }
        if (applicationProperties.getLedgerly().isSyncReturns() && StringUtils.isBlank(orderReturn.getLedgerlyCreditNoteId())) {
                try {
                    Optional<String> creditNoteId = ledgerlyIntegrationClient.cancelOrder(
                        order.getOrderNumber(),
                        orderReturn.getReturnNumber(),
                        StringUtils.firstNonBlank(orderReturn.getRazorpayRefundId(), orderReturn.getManualRefundReference())
                    );
                    if (creditNoteId.isPresent()) {
                        orderReturn.setLedgerlyCreditNoteId(creditNoteId.orElseThrow());
                    } else if (shouldStubDemoCreditNote(order, null)) {
                        orderReturn.setLedgerlyCreditNoteId("cn_demo_" + order.getOrderNumber());
                        LOG.info(
                            "Ledgerly credit note stubbed for demo return {} (order {}, no Ledgerly credit note id)",
                            orderReturn.getReturnNumber(),
                            order.getOrderNumber()
                        );
                    }
                } catch (RuntimeException e) {
                    if (shouldStubDemoCreditNote(order, e)) {
                        orderReturn.setLedgerlyCreditNoteId("cn_demo_" + order.getOrderNumber());
                        LOG.info(
                            "Ledgerly credit note stubbed for demo return {} (order {})",
                            orderReturn.getReturnNumber(),
                            order.getOrderNumber()
                        );
                    } else {
                        LOG.warn("Ledgerly order cancel failed for return {}: {}", orderReturn.getReturnNumber(), e.getMessage());
                        orderReturn.setAdminNotes(
                            mergeNotes(orderReturn.getAdminNotes(), "Ledgerly order cancel failed: " + StringUtils.left(e.getMessage(), 200))
                        );
                    }
                }
            }
            if (applicationProperties.getZoho().isSyncReturns() && StringUtils.isBlank(orderReturn.getZohoCreditNoteId())) {
                try {
                    Optional<String> zohoCreditNoteId = zohoBooksIntegrationClient.createCreditNote(
                        order.getId(),
                        orderReturn.getReturnNumber()
                    );
                    if (zohoCreditNoteId.isPresent()) {
                        orderReturn.setZohoCreditNoteId(zohoCreditNoteId.orElseThrow());
                    } else if (shouldStubDemoCreditNote(order, null)) {
                        orderReturn.setZohoCreditNoteId("cn_zoho_demo_" + order.getOrderNumber());
                        LOG.info(
                            "Zoho credit note stubbed for demo return {} (order {}, no Zoho credit note id)",
                            orderReturn.getReturnNumber(),
                            order.getOrderNumber()
                        );
                    }
                } catch (RuntimeException e) {
                    if (shouldStubDemoCreditNote(order, e)) {
                        orderReturn.setZohoCreditNoteId("cn_zoho_demo_" + order.getOrderNumber());
                        LOG.info(
                            "Zoho credit note stubbed for demo return {} (order {})",
                            orderReturn.getReturnNumber(),
                            order.getOrderNumber()
                        );
                    } else {
                        LOG.warn("Zoho credit note failed for return {}: {}", orderReturn.getReturnNumber(), e.getMessage());
                        orderReturn.setAdminNotes(
                            mergeNotes(orderReturn.getAdminNotes(), "Zoho credit note failed: " + StringUtils.left(e.getMessage(), 200))
                        );
                    }
                }
            }
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setResolvedAt(Instant.now());
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(order, saved);
        return saved;
    }

    private BigDecimal resolveRefundAmount(OrderReturn orderReturn) {
        BigDecimal refundAmount = orderReturnItemRepository
            .findAllByOrderReturnIdOrderById(orderReturn.getId())
            .stream()
            .map(OrderReturnItem::getRefundAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refundAmount.signum() <= 0) {
            refundAmount = money(orderReturn.getRefundAmount());
        }
        if (refundAmount.signum() <= 0 && orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId()).isEmpty()) {
            refundAmount = money(orderReturn.getOrder().getTotal());
        }
        if (refundAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return has no refundable accepted lines");
        }
        return refundAmount.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean isCollectedCod(CustomerOrder order) {
        return order != null && "cod".equalsIgnoreCase(order.getPaymentMethod()) && order.getPaymentStatus() == PaymentStatus.PAID;
    }

    private static boolean isUncollectedCod(CustomerOrder order) {
        return order != null && "cod".equalsIgnoreCase(order.getPaymentMethod()) && order.getPaymentStatus() != PaymentStatus.PAID;
    }

    private static String validateManualRefundReference(String reference) {
        String normalized = StringUtils.normalizeSpace(StringUtils.trimToEmpty(reference));
        if (normalized.length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund reference must be at least 4 characters");
        }
        return StringUtils.left(normalized, 160);
    }

    // ---------------------------------------------------------------------
    // Eligibility / config
    // ---------------------------------------------------------------------

    public Map<String, Object> eligibilityForUser(String login, String orderNumber) {
        if (!RETURNS_REFUNDS_ENABLED) {
            var disabled = new LinkedHashMap<String, Object>();
            disabled.put("order_number", orderNumber);
            disabled.put("eligible", false);
            disabled.put("message", RETURNS_DISABLED_MESSAGE);
            disabled.put("enabled", false);
            disabled.put("window_days", RETURN_WINDOW_DAYS);
            return disabled;
        }
        return customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, login)
            .map(order -> {
                var result = new LinkedHashMap<String, Object>();
                result.put("order_number", orderNumber);
                result.put("enabled", true);
                result.put("window_days", RETURN_WINDOW_DAYS);
                try {
                    assertReturnEligible(order);
                    result.put("eligible", true);
                } catch (ResponseStatusException e) {
                    result.put("eligible", false);
                    result.put("message", e.getReason());
                }
                return result;
            })
            .orElseGet(() -> {
                var result = new LinkedHashMap<String, Object>();
                result.put("order_number", orderNumber);
                result.put("eligible", false);
                result.put("message", "Order not found");
                result.put("enabled", true);
                result.put("window_days", RETURN_WINDOW_DAYS);
                return result;
            });
    }

    public Map<String, Object> publicConfig() {
        var result = new LinkedHashMap<String, Object>();
        result.put("enabled", RETURNS_REFUNDS_ENABLED);
        result.put("window_days", RETURN_WINDOW_DAYS);
        return result;
    }

    private void assertFeatureEnabled() {
        if (!RETURNS_REFUNDS_ENABLED) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, RETURNS_DISABLED_MESSAGE);
        }
    }

    private void assertIsExchange(OrderReturn orderReturn) {
        if (orderReturn.getRequestType() != OrderRequestType.EXCHANGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not an exchange request");
        }
    }

    public void assertReturnEligible(CustomerOrder order) {
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only paid orders are eligible for return");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancelled orders cannot be returned");
        }
        Instant deliveryAnchor = resolveDeliveryAnchor(order);
        if (deliveryAnchor == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "This order is not marked as delivered yet. You can request a return after delivery."
            );
        }
        Instant windowEnd = deliveryAnchor.plus(RETURN_WINDOW_DAYS, ChronoUnit.DAYS);
        if (Instant.now().isAfter(windowEnd)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Return window has expired (" + RETURN_WINDOW_DAYS + " days from delivery)"
            );
        }
    }

    Instant resolveDeliveryAnchor(CustomerOrder order) {
        LocalDate shipment = parseShipmentDate(order.getShipmentDate());
        if (shipment != null) {
            return shipment.atStartOfDay(INDIA).toInstant();
        }
        if (order.getStatus() == OrderStatus.FULFILLED) {
            if (order.getLedgerlyTrackingLastPulledAt() != null) {
                return order.getLedgerlyTrackingLastPulledAt();
            }
            if (order.getLedgerlyLastSyncedAt() != null) {
                return order.getLedgerlyLastSyncedAt();
            }
        }
        String trackingStatus = OrderTrackingStatusResolver.resolve(order);
        if ("delivered".equalsIgnoreCase(trackingStatus) && order.getLedgerlyTrackingLastPulledAt() != null) {
            return order.getLedgerlyTrackingLastPulledAt();
        }
        if (StringUtils.isNotBlank(order.getTrackingNumber())) {
            LocalDate trackedShipment = parseShipmentDate(order.getShipmentDate());
            if (trackedShipment != null) {
                return trackedShipment.atStartOfDay(INDIA).toInstant();
            }
        }
        if (StringUtils.isNotBlank(order.getTrackingNumber()) && order.getLedgerlyTrackingLastPulledAt() != null) {
            return order.getLedgerlyTrackingLastPulledAt();
        }
        return null;
    }

    private void queueReturnEmail(CustomerOrder order, OrderReturn orderReturn) {
        if (order == null || orderReturn == null) {
            return;
        }
        AfterCommit.run(() -> {
            try {
                orderMailService.sendReturnUpdate(order, orderReturn);
                orderSmsService.sendReturnUpdate(order, orderReturn);
            } catch (RuntimeException ex) {
                LOG.warn("Return email/SMS failed for {}", orderReturn.getReturnNumber(), ex);
            }
        });
    }

    private void queueAdminPickupFailedEmail(CustomerOrder order, OrderReturn orderReturn) {
        if (order == null || orderReturn == null) {
            return;
        }
        AfterCommit.run(() -> {
            try {
                orderMailService.sendPickupFailedAdminNotice(order, orderReturn);
            } catch (RuntimeException ex) {
                LOG.warn("Admin pickup-failed email failed for {}", orderReturn.getReturnNumber(), ex);
            }
        });
    }

    // ---------------------------------------------------------------------
    // Inventory helpers
    // ---------------------------------------------------------------------

    private void restoreItemStock(OrderItem item) {
        restoreItemStock(item, item == null ? 0 : Optional.ofNullable(item.getQty()).orElse(0));
    }

    private void restoreItemStock(OrderItem item, int quantity) {
        if (item == null || StringUtils.isBlank(item.getProductId())) {
            return;
        }
        try {
            long productId = Long.parseLong(item.getProductId());
            productRepository
                .findOneForInventoryUpdate(productId)
                .ifPresent(product -> {
                    int qty = quantity;
                    if (qty <= 0) {
                        return;
                    }
                    if (StringUtils.isNotBlank(item.getVariantSku()) && variantSupport.isVariants(product)) {
                        if (!variantSupport.incrementVariantStock(
                            product,
                            item.getVariantSku(),
                            item.getItemSize(),
                            item.getColor(),
                            qty
                        )) {
                            product.setStock(Optional.ofNullable(product.getStock()).orElse(0) + qty);
                        }
                    } else {
                        product.setStock(Optional.ofNullable(product.getStock()).orElse(0) + qty);
                    }
                    productRepository.save(product);
                });
        } catch (NumberFormatException ignored) {
            // skip invalid product id
        }
    }

    private void restoreInventoryForOrder(CustomerOrder order) {
        orderItemRepository.findAllByOrderId(order.getId()).forEach(this::restoreItemStock);
    }

    /**
     * Reverses a previously-restored item's stock (used when a return/exchange is rejected after
     * inspection, undoing the credit given at approval). Best-effort: if the stock has already been
     * resold in the meantime, clamps at zero and returns false so the caller can note the discrepancy.
     */
    private boolean reverseItemStock(OrderItem item) {
        if (item == null || StringUtils.isBlank(item.getProductId())) {
            return true;
        }
        try {
            long productId = Long.parseLong(item.getProductId());
            Optional<Product> productOpt = productRepository.findOneForInventoryUpdate(productId);
            if (productOpt.isEmpty()) {
                return true;
            }
            Product product = productOpt.orElseThrow();
            int qty = Optional.ofNullable(item.getQty()).orElse(0);
            if (qty <= 0) {
                return true;
            }
            boolean ok;
            if (StringUtils.isNotBlank(item.getVariantSku()) && variantSupport.isVariants(product)) {
                ok = variantSupport.decrementVariantStock(product, item.getVariantSku(), item.getItemSize(), item.getColor(), qty);
            } else {
                int current = Optional.ofNullable(product.getStock()).orElse(0);
                ok = current >= qty;
                product.setStock(Math.max(0, current - qty));
            }
            productRepository.save(product);
            return ok;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private boolean reverseInventoryForOrder(CustomerOrder order) {
        boolean allOk = true;
        for (OrderItem item : orderItemRepository.findAllByOrderId(order.getId())) {
            if (!reverseItemStock(item)) {
                allOk = false;
            }
        }
        return allOk;
    }

    private Product resolveProduct(String productIdStr) {
        if (StringUtils.isBlank(productIdStr)) {
            return null;
        }
        try {
            return productRepository.findById(Long.parseLong(productIdStr)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Optional<Product> resolveProductForUpdate(String productIdStr) {
        if (StringUtils.isBlank(productIdStr)) {
            return Optional.empty();
        }
        try {
            return productRepository.findOneForInventoryUpdate(Long.parseLong(productIdStr));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------------
    // Generic transition helper
    // ---------------------------------------------------------------------

    private OrderReturn transition(
        String returnNumber,
        OrderReturnStatus from,
        OrderReturnStatus to,
        String adminNotes,
        String errorMessage
    ) {
        return transition(returnNumber, EnumSet.of(from), to, adminNotes, errorMessage);
    }

    private OrderReturn transition(
        String returnNumber,
        Set<OrderReturnStatus> fromAny,
        OrderReturnStatus to,
        String adminNotes,
        String errorMessage
    ) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (!fromAny.contains(orderReturn.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        orderReturn.setStatus(to);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        return orderReturnRepository.save(orderReturn);
    }

    // ---------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------

    private static String normalizeReason(String reason, String kind) {
        String normalized = StringUtils.normalizeSpace(StringUtils.defaultString(reason)).trim();
        if (normalized.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please describe the " + kind + " reason (at least 10 characters)");
        }
        return normalized.length() <= 4000 ? normalized : normalized.substring(0, 4000);
    }

    private static String validateRejectionReason(String rejectionReason) {
        String reason = StringUtils.normalizeSpace(StringUtils.defaultString(rejectionReason)).trim();
        if (reason.length() < 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
        return reason.length() <= 1000 ? reason : reason.substring(0, 1000);
    }

    private OrderReturn loadReturn(String returnNumber) {
        return orderReturnRepository
            .findOneByReturnNumber(returnNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Return request not found"));
    }

    private OrderReturn loadOwnedReturn(String userLogin, String returnNumber) {
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (!orderReturn.getUserLogin().equals(userLogin)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Return request not found");
        }
        return orderReturn;
    }

    private static String generateReturnNumber(String orderNumber) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        int maxTotalLen = 48;
        int prefixLen = "RET-".length() + "-".length() + suffix.length();
        int maxOrderLen = Math.max(1, maxTotalLen - prefixLen);
        String safeOrderNumber = StringUtils.defaultString(orderNumber);
        if (safeOrderNumber.length() > maxOrderLen) {
            safeOrderNumber = safeOrderNumber.substring(0, maxOrderLen);
        }
        return "RET-" + safeOrderNumber + "-" + suffix;
    }

    private static LocalDate parseShipmentDate(String shipmentDate) {
        if (StringUtils.isBlank(shipmentDate)) {
            return null;
        }
        String trimmed = shipmentDate.trim();
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("yyyy-MM-dd"))) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static String trimNotes(String notes) {
        String value = StringUtils.normalizeSpace(StringUtils.defaultString(notes)).trim();
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private static String mergeNotes(String existing, String addition) {
        String extra = trimNotes(addition);
        if (StringUtils.isBlank(extra)) {
            return existing;
        }
        if (StringUtils.isBlank(existing)) {
            return extra;
        }
        String merged = existing + "\n" + extra;
        return merged.length() <= 2000 ? merged : merged.substring(0, 2000);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveOrFallback(BigDecimal preferred, BigDecimal fallback) {
        BigDecimal value = money(preferred);
        return value.signum() > 0 ? value : money(fallback);
    }

    public record ReturnLineSelection(Long orderItemId, Integer quantity) {}

    public record InspectionLineDecision(Long returnItemId, Integer acceptedQuantity, ReturnDisposition disposition) {}

    /** UAT/demo: when Ledgerly has no invoice or cancel fails, still complete return flow for scenario tests. */
    private boolean shouldStubDemoCreditNote(CustomerOrder order, RuntimeException error) {
        if (!isDemoPaymentOrder(order)) {
            return false;
        }
        if (error != null) {
            return true;
        }
        return razorpayRefundService.isDemoMode();
    }

    private static boolean isDemoPaymentOrder(CustomerOrder order) {
        String paymentId = StringUtils.trimToEmpty(order.getRazorpayPaymentId());
        String razorpayOrderId = StringUtils.trimToEmpty(order.getRazorpayOrderId());
        return paymentId.startsWith("pay_demo") || razorpayOrderId.startsWith("order_demo");
    }
}
