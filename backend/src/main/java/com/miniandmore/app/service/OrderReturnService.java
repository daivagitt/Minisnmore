package com.miniandmore.app.service;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.client.ZohoBooksIntegrationClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.OrderReturnItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.ManualRefundMethod;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PackageType;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnDisposition;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.OrderReturnItemRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.repository.StoreAddressRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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

    /** Feature switch: returns & exchanges. Flip to false to disable; no other code needs to change. */
    private static final boolean RETURNS_REFUNDS_ENABLED = true;
    private static final String RETURNS_DISABLED_MESSAGE = "Returns & exchanges are currently unavailable. Please contact support.";
    private static final int MAX_RETURN_IMAGES = 5;
    private static final int MAX_IMAGE_URL_LENGTH = 600;
    private static final ObjectMapper IMAGE_URLS_JSON = new ObjectMapper();

    private static final EnumSet<OrderReturnStatus> TERMINAL_RETURN_STATUSES = EnumSet.of(
        OrderReturnStatus.REJECTED,
        OrderReturnStatus.REFUNDED,
        OrderReturnStatus.COMPLETED,
        OrderReturnStatus.CANCELLED
    );

    /** Anything not terminal is "open" — computed so new intermediate statuses default to open, not the reverse. */
    private static final Set<OrderReturnStatus> OPEN_RETURN_STATUSES = EnumSet.complementOf(TERMINAL_RETURN_STATUSES);

    private static final EnumSet<OrderReturnStatus> RECEIVED_FROM_STATUSES = EnumSet.of(
        OrderReturnStatus.PICKUP_REQUESTED,
        OrderReturnStatus.PICKUP_SCHEDULED,
        OrderReturnStatus.PICKED_UP
    );

    private static final EnumSet<OrderReturnStatus> PICKED_UP_FROM_STATUSES = EnumSet.of(
        OrderReturnStatus.PICKUP_REQUESTED,
        OrderReturnStatus.PICKUP_SCHEDULED
    );

    /** Legacy REPACKING stays eligible so in-flight rows can still complete a package. */
    private static final EnumSet<OrderReturnStatus> OUTBOUND_PACKAGE_STATUSES = EnumSet.of(
        OrderReturnStatus.ITEM_ALLOCATED,
        OrderReturnStatus.REPACKING,
        OrderReturnStatus.READY_TO_SHIP
    );

    private static final EnumSet<OrderReturnStatus> OUTBOUND_PACKAGE_ADVANCE_STATUSES = EnumSet.of(
        OrderReturnStatus.ITEM_ALLOCATED,
        OrderReturnStatus.REPACKING
    );

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
    private final CaseSlaSupport caseSlaSupport;
    private final SiteSettingRepository siteSettingRepository;
    private final OrderPackageService orderPackageService;
    private final ExchangeRazorpaySettlementService exchangeRazorpaySettlementService;
    private final PackageShortShipService packageShortShipService;
    private final ReturnXpressBeesShipmentFactory shipmentFactory;

    private static final EnumSet<OrderReturnStatus> SUMMARY_CLOSED_STATUSES = EnumSet.of(
        OrderReturnStatus.REJECTED,
        OrderReturnStatus.REFUNDED,
        OrderReturnStatus.COMPLETED,
        OrderReturnStatus.CANCELLED,
        OrderReturnStatus.FAILED
    );

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
        ObjectMapper objectMapper,
        CaseSlaSupport caseSlaSupport,
        SiteSettingRepository siteSettingRepository,
        OrderPackageService orderPackageService,
        ExchangeRazorpaySettlementService exchangeRazorpaySettlementService,
        PackageShortShipService packageShortShipService,
        StoreAddressRepository storeAddressRepository
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
        this.caseSlaSupport = caseSlaSupport;
        this.siteSettingRepository = siteSettingRepository;
        this.orderPackageService = orderPackageService;
        this.exchangeRazorpaySettlementService = exchangeRazorpaySettlementService;
        this.packageShortShipService = packageShortShipService;
        this.shipmentFactory = new ReturnXpressBeesShipmentFactory(
            applicationProperties,
            objectMapper,
            orderReturnItemRepository,
            storeAddressRepository
        );
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
    public Map<String, Object> adminSummary() {
        long returns = 0;
        long exchanges = 0;
        for (Object[] row : orderReturnRepository.countGroupedByRequestType()) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            long count = ((Number) row[1]).longValue();
            if (row[0] == OrderRequestType.EXCHANGE) {
                exchanges += count;
            } else {
                returns += count;
            }
        }

        Map<String, Long> stages = new LinkedHashMap<>();
        stages.put("request", 0L);
        stages.put("pickup", 0L);
        stages.put("inspection", 0L);
        stages.put("exchange", 0L);
        stages.put("closed", 0L);
        long total = 0;
        for (Object[] row : orderReturnRepository.countGroupedByStatus()) {
            if (row == null || row.length < 2 || !(row[0] instanceof OrderReturnStatus status) || row[1] == null) {
                continue;
            }
            long count = ((Number) row[1]).longValue();
            total += count;
            String stage = stageForSummary(status);
            stages.put(stage, stages.getOrDefault(stage, 0L) + count);
        }

        long closed = stages.getOrDefault("closed", 0L);
        long open = Math.max(0, total - closed);
        // Approved cases awaiting reverse pickup booking (matches bookPickup preconditions).
        long xpressbeesReverseReady = orderReturnRepository.countByStatus(OrderReturnStatus.APPROVED);
        long overdue = 0;
        for (Object[] row : orderReturnRepository.findStatusAndRequestedAtByStatusNotIn(SUMMARY_CLOSED_STATUSES)) {
            if (row == null || row.length < 2 || !(row[0] instanceof OrderReturnStatus status) || !(row[1] instanceof Instant requestedAt)) {
                continue;
            }
            if (caseSlaSupport.isReturnOverdue(status.name().toLowerCase(), requestedAt)) {
                overdue++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("returns", returns);
        summary.put("exchanges", exchanges);
        summary.put("open", open);
        summary.put("closed", closed);
        summary.put("overdue", overdue);
        summary.put("xpressbeesReverseReady", xpressbeesReverseReady);
        summary.put("stages", stages);
        return summary;
    }

    private static String stageForSummary(OrderReturnStatus status) {
        return switch (status) {
            case REQUESTED, APPROVED -> "request";
            case PICKUP_REQUESTED, PICKUP_SCHEDULED, PICKUP_FAILED, PICKED_UP -> "pickup";
            case RECEIVED, INSPECTED, ACCEPTED -> "inspection";
            case ITEM_ALLOCATED, REPACKING, READY_TO_SHIP, SHIPPED, DELIVERED -> "exchange";
            case REFUNDED, COMPLETED, REJECTED, FAILED, CANCELLED -> "closed";
        };
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
        return requestReturn(userLogin, orderNumber, reason, List.of(), List.of());
    }

    public OrderReturn requestReturn(String userLogin, String orderNumber, String reason, List<ReturnLineSelection> requestedLines) {
        return requestReturn(userLogin, orderNumber, reason, requestedLines, List.of());
    }

    public OrderReturn requestReturn(
        String userLogin,
        String orderNumber,
        String reason,
        List<ReturnLineSelection> requestedLines,
        List<String> imageUrls
    ) {
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
        orderReturn.setImageUrlsJson(serializeReturnImageUrls(imageUrls));
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
            orderReturn.setRefundAmount(merchandiseRefund(order));
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

        BigDecimal subtotal = OrderMerchandiseAmounts.positiveOrFallback(order.getSubtotal(), orderItems
            .stream()
            .map(item -> money(item.getPrice()).multiply(BigDecimal.valueOf(Optional.ofNullable(item.getQty()).orElse(0))))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal discount = money(order.getDiscount());
        boolean hasPersistedLineAllocations = OrderMerchandiseAmounts.orderHasPersistedLineAllocations(orderItems);
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
            BigDecimal discountAllocation = OrderMerchandiseAmounts.allocatedDiscountFor(
                item,
                selected.getValue(),
                orderedQty,
                subtotal,
                discount,
                hasPersistedLineAllocations
            );
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
            if (orderReturn.getOrderItem() == null) {
                orderReturn.setOrderItem(item);
            }
        }
        orderReturn.setRefundAmount(totalRefund.setScale(2, RoundingMode.HALF_UP));
        orderReturnRepository.save(orderReturn);
    }

    public OrderReturn requestExchange(
        String userLogin,
        String orderNumber,
        Long orderItemId,
        String reason,
        String replacementSize,
        String replacementColor
    ) {
        return requestExchange(userLogin, orderNumber, orderItemId, reason, replacementSize, replacementColor, List.of());
    }

    public OrderReturn requestExchange(
        String userLogin,
        String orderNumber,
        Long orderItemId,
        String reason,
        String replacementSize,
        String replacementColor,
        List<String> imageUrls
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
        orderReturn.setImageUrlsJson(serializeReturnImageUrls(imageUrls));
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
        OrderReturn orderReturn = loadReturnForUpdate(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return is not awaiting approval");
        }
        if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
            if (orderReturn.getReplacementReservedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Replacement stock is already reserved");
            }
            reserveExchangeReplacement(orderReturn);
        }
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
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
        saveExchangeProductAndSyncZoho(orderReturn, product);
        CustomerOrder order = orderReturn.getOrder();
        BigDecimal originalAmount = netMerchandiseAmount(item, order, qty);
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
        // Route via sendReturnUpdate: EXCHANGE → exchangeRejected; RETURN → QC-failed or returnRejected.
        // Do not force QC-failed — that breaks diagram "Reject return → Inform Customer" for exchanges.
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
        String awb = StringUtils.left(StringUtils.trimToNull(reverseAwb), 80);
        orderReturn.setReverseAwb(awb);
        String pickupUrl = StringUtils.trimToNull(trackingUrl);
        if (pickupUrl == null && ReturnPickupTracking.isXpressBees(mode, carrierName)) {
            pickupUrl = xpressBeesClient.buildTrackingUrl(awb);
        }
        orderReturn.setTrackingUrl(StringUtils.left(pickupUrl, 500));
        orderReturn.setPickupWindow(StringUtils.left(StringUtils.trimToNull(pickupWindow), 200));
        orderReturn.setPickupFailureReason(null);
        orderReturn.setReverseCarrierStatus(XpressBeesReturnCarrierStatus.DATA_RECEIVED);
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
            XpressBeesClient.ReversePickupResult result = xpressBeesClient.createReversePickup(
                shipmentFactory.buildReversePickupRequest(orderReturn, preAssignedAwb)
            );
            orderReturn.setLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
            orderReturn.setCarrierName("XpressBees");
            orderReturn.setReverseAwb(StringUtils.left(result.awb(), 80));
            String tracking = StringUtils.defaultIfBlank(xpressBeesClient.buildTrackingUrl(result.awb()), result.trackingUrl());
            orderReturn.setTrackingUrl(StringUtils.left(StringUtils.trimToNull(tracking), 500));
            orderReturn.setPickupWindow(StringUtils.left(StringUtils.trimToNull(result.pickupWindow()), 200));
            orderReturn.setProviderRequestId(StringUtils.left(StringUtils.trimToNull(result.providerRequestId()), 120));
            orderReturn.setPickupFailureReason(null);
            orderReturn.setReverseCarrierStatus(XpressBeesReturnCarrierStatus.DATA_RECEIVED);
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
        if (StringUtils.isBlank(orderReturn.getRejectionReason()) && StringUtils.isNotBlank(orderReturn.getPickupFailureReason())) {
            orderReturn.setRejectionReason(StringUtils.left(orderReturn.getPickupFailureReason(), 1000));
        }
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
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (!hasReverseShipment(orderReturn)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Book a reverse pickup shipment before marking picked up"
            );
        }
        if (!PICKED_UP_FROM_STATUSES.contains(orderReturn.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Return must be scheduled for pickup before marking picked up"
            );
        }
        orderReturn.setStatus(OrderReturnStatus.PICKED_UP);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn markReceived(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (!hasReverseShipment(orderReturn)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Book a reverse pickup shipment before marking received"
            );
        }
        if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
            if (orderReturn.getStatus() != OrderReturnStatus.PICKED_UP) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Exchange item must be picked up before marking received"
                );
            }
        } else if (!RECEIVED_FROM_STATUSES.contains(orderReturn.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Return must be picked up or scheduled for pickup before marking received"
            );
        }
        orderReturn.setStatus(OrderReturnStatus.RECEIVED);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
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
        // Exchange accept only records disposition. Requested-item restock happens once later
        // (allocation or convert-to-refund). Credit notes are created once: cheaper replacement
        // after Razorpay refund initiated; otherwise at allocation or convert-to-refund.
        orderReturn.setDisposition(disposition);
        if (orderReturn.getRequestType() != OrderRequestType.EXCHANGE) {
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
        return settlePriceDifference(returnNumber, settlementStatus, amount, method, reference, settledBy, adminNotes, false);
    }

    public OrderReturn settlePriceDifference(
        String returnNumber,
        ExchangeSettlementStatus settlementStatus,
        BigDecimal amount,
        String method,
        String reference,
        String settledBy,
        String adminNotes,
        boolean forceManual
    ) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        assertIsExchange(orderReturn);
        if (orderReturn.getStatus() != OrderReturnStatus.APPROVED && orderReturn.getStatus() != OrderReturnStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price difference can only be settled for an approved exchange");
        }
        ExchangeSettlementStatus currentSettlement = orderReturn.getSettlementStatus();
        if (ExchangeSettlementAmounts.alreadySettled(currentSettlement)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Price difference already settled");
        }
        BigDecimal difference = money(orderReturn.getPriceDifference());
        ExchangeSettlementStatus expected;
        if (difference.signum() > 0) {
            expected = ExchangeSettlementStatus.COLLECTED;
        } else if (difference.signum() < 0) {
            expected = ExchangeSettlementStatus.REFUNDED;
        } else {
            expected = ExchangeSettlementStatus.NOT_REQUIRED;
        }
        if (settlementStatus == null || (settlementStatus != expected && settlementStatus != ExchangeSettlementStatus.WAIVED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement action does not match the exchange price difference");
        }
        BigDecimal expectedAmount = difference.abs().setScale(2, RoundingMode.HALF_UP);
        boolean razorpayRefund =
            !forceManual &&
            settlementStatus == ExchangeSettlementStatus.REFUNDED &&
            canRefundExchangeDifferenceViaRazorpay(orderReturn);
        BigDecimal recordedAmount = settlementStatus == ExchangeSettlementStatus.WAIVED ? BigDecimal.ZERO : money(amount);
        if (razorpayRefund && recordedAmount.signum() <= 0) {
            recordedAmount = expectedAmount;
        }
        if (settlementStatus != ExchangeSettlementStatus.WAIVED && recordedAmount.compareTo(expectedAmount) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement amount must equal the price difference");
        }
        if (settlementStatus != ExchangeSettlementStatus.WAIVED && !razorpayRefund && StringUtils.isAnyBlank(method, reference)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement method and transaction reference are required");
        }
        if (razorpayRefund) {
            exchangeRazorpaySettlementService.recordRazorpayRefund(
                returnNumber,
                recordedAmount,
                settledBy,
                adminNotes
            );
            OrderReturn settled = orderReturnRepository
                .findOneByReturnNumberForUpdate(returnNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Return not found"));
            // Refund initiated is enough — do not wait for refund.processed.
            // Persist the id via this save, not persistIfAbsent: that REQUIRES_NEW
            // update deadlocks if a later allocate/convert holds FOR UPDATE.
            issueExchangeCreditNote(settled, false);
            return orderReturnRepository.save(settled);
        }
        orderReturn = orderReturnRepository
            .findOneByReturnNumberForUpdate(returnNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Return not found"));
        if (orderReturn.getRequestType() != OrderRequestType.EXCHANGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price difference settlement applies only to exchanges");
        }
        if (orderReturn.getStatus() != OrderReturnStatus.APPROVED && orderReturn.getStatus() != OrderReturnStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price difference can only be settled for an approved exchange");
        }
        if (ExchangeSettlementAmounts.alreadySettled(orderReturn.getSettlementStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Price difference already settled");
        }
        String recordedMethod = StringUtils.left(StringUtils.trimToNull(method), 40);
        String recordedReference = StringUtils.left(StringUtils.trimToNull(reference), 160);
        orderReturn.setSettlementStatus(settlementStatus);
        orderReturn.setSettlementAmount(recordedAmount);
        orderReturn.setSettlementMethod(recordedMethod);
        orderReturn.setSettlementReference(recordedReference);
        orderReturn.setSettledAt(Instant.now());
        orderReturn.setSettledBy(StringUtils.left(StringUtils.defaultIfBlank(settledBy, "system"), 50));
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        if (settlementStatus == ExchangeSettlementStatus.REFUNDED) {
            issueExchangeCreditNote(orderReturn, false);
        }
        return orderReturnRepository.save(orderReturn);
    }

    public boolean canRefundExchangeDifferenceViaRazorpay(OrderReturn orderReturn) {
        return ExchangeSettlementAmounts.canRefundViaRazorpay(orderReturn);
    }

    public OrderReturn allocateReplacementItem(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturnForUpdate(returnNumber);
        assertIsExchange(orderReturn);
        if (orderReturn.getStatus() == OrderReturnStatus.ITEM_ALLOCATED) {
            return orderReturn;
        }
        if (orderReturn.getStatus() != OrderReturnStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exchange must be accepted before allocating the replacement");
        }
        // Compatibility for exchanges accepted before approval-time reservations were introduced.
        if (orderReturn.getReplacementReservedAt() == null) {
            OrderItem item = orderReturn.getOrderItem();
            Product product = resolveProductForUpdate(item.getProductId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Original product could not be resolved")
            );
            int qty = Math.max(1, Optional.ofNullable(item.getQty()).orElse(1));
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
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        restoreExchangeOriginalIfRestockable(orderReturn);
        issueExchangeCreditNote(orderReturn, false);
        issueReplacementInvoice(orderReturn);
        refundCheaperExchangeCreditLeftover(orderReturn);
        syncExchangeProductStockToZoho(orderReturn);
        orderReturn.setStatus(OrderReturnStatus.ITEM_ALLOCATED);
        return orderReturnRepository.save(orderReturn);
    }

    /**
     * Credit note for the item coming back. After a cheaper-replacement Razorpay
     * refund, created as soon as payment status is refund initiated — do not wait
     * for refund.processed. Unlinked so a continuing exchange can apply it to the
     * replacement invoice. Linked at convert-to-refund so the original invoice is
     * credited. Collect does not create one; same-price and higher-price notes are
     * created at allocation.
     */
    private void issueExchangeCreditNote(OrderReturn orderReturn, boolean linkToSourceInvoice) {
        CustomerOrder order = orderReturn.getOrder();
        if (order == null || order.getId() == null) {
            return;
        }
        if (applicationProperties.getLedgerly().isSyncReturns() && StringUtils.isBlank(orderReturn.getLedgerlyCreditNoteId())) {
            try {
                Optional<String> creditNoteId = ledgerlyIntegrationClient.createCreditNote(
                    order.getOrderNumber(),
                    orderReturn.getReturnNumber(),
                    exchangeOriginalAmount(orderReturn)
                );
                if (creditNoteId.isPresent()) {
                    orderReturn.setLedgerlyCreditNoteId(creditNoteId.orElseThrow());
                } else if (shouldStubDemoCreditNote(order, null)) {
                    orderReturn.setLedgerlyCreditNoteId("cn_demo_" + order.getOrderNumber());
                }
            } catch (RuntimeException e) {
                if (shouldStubDemoCreditNote(order, e)) {
                    orderReturn.setLedgerlyCreditNoteId("cn_demo_" + order.getOrderNumber());
                } else {
                    LOG.warn("Ledgerly exchange credit note failed for {}: {}", orderReturn.getReturnNumber(), e.getMessage());
                    orderReturn.setAdminNotes(
                        mergeNotes(orderReturn.getAdminNotes(), "Ledgerly credit note failed: " + StringUtils.left(e.getMessage(), 200))
                    );
                }
            }
        }
        if (applicationProperties.getZoho().isSyncReturns()) {
            if (StringUtils.isBlank(orderReturn.getZohoCreditNoteId())) {
                try {
                    Optional<String> zohoCreditNoteId = zohoBooksIntegrationClient.createCreditNote(
                        order.getId(),
                        orderReturn.getReturnNumber(),
                        returnedQuantitiesForZoho(orderReturn),
                        linkToSourceInvoice
                    );
                    if (zohoCreditNoteId.isPresent()) {
                        orderReturn.setZohoCreditNoteId(zohoCreditNoteId.orElseThrow());
                    } else if (shouldStubDemoCreditNote(order, null)) {
                        orderReturn.setZohoCreditNoteId("cn_zoho_demo_" + order.getOrderNumber());
                    }
                } catch (RuntimeException e) {
                    if (shouldStubDemoCreditNote(order, e)) {
                        orderReturn.setZohoCreditNoteId("cn_zoho_demo_" + order.getOrderNumber());
                    } else {
                        LOG.warn("Zoho exchange credit note failed for {}: {}", orderReturn.getReturnNumber(), e.getMessage());
                        orderReturn.setAdminNotes(
                            mergeNotes(orderReturn.getAdminNotes(), "Zoho credit note failed: " + StringUtils.left(e.getMessage(), 200))
                        );
                    }
                }
            }
            if (
                linkToSourceInvoice &&
                StringUtils.isNotBlank(orderReturn.getZohoCreditNoteId()) &&
                !StringUtils.startsWith(orderReturn.getZohoCreditNoteId(), "cn_zoho_demo_")
            ) {
                try {
                    Optional<String> attachedId = zohoBooksIntegrationClient.attachCreditNoteToSourceInvoice(
                        order.getId(),
                        orderReturn.getReturnNumber(),
                        orderReturn.getZohoCreditNoteId(),
                        returnedQuantitiesForZoho(orderReturn)
                    );
                    if (attachedId.isPresent()) {
                        orderReturn.setZohoCreditNoteId(attachedId.orElseThrow());
                    } else if (StringUtils.isNotBlank(orderReturn.getZohoCreditNoteId())) {
                        orderReturn.setZohoCreditNoteId(null);
                        orderReturn.setAdminNotes(
                            mergeNotes(
                                orderReturn.getAdminNotes(),
                                "Zoho credit note was voided without a linked replacement; retry convert or create manually"
                            )
                        );
                    }
                } catch (RuntimeException e) {
                    LOG.warn(
                        "Zoho attach credit note to original invoice failed for {}: {}",
                        orderReturn.getReturnNumber(),
                        e.getMessage()
                    );
                    orderReturn.setAdminNotes(
                        mergeNotes(orderReturn.getAdminNotes(), "Zoho credit note apply failed: " + StringUtils.left(e.getMessage(), 200))
                    );
                }
            }
        }
    }

    private void refundCheaperExchangeCreditLeftover(OrderReturn orderReturn) {
        if (
            !applicationProperties.getZoho().isSyncReturns() ||
            money(orderReturn.getPriceDifference()).signum() >= 0 ||
            orderReturn.getSettlementStatus() != ExchangeSettlementStatus.REFUNDED ||
            StringUtils.isBlank(orderReturn.getZohoCreditNoteId()) ||
            StringUtils.startsWith(orderReturn.getZohoCreditNoteId(), "cn_zoho_demo_") ||
            StringUtils.isBlank(orderReturn.getZohoReplacementInvoiceId())
        ) {
            return;
        }
        try {
            zohoBooksIntegrationClient.refundOpenCreditNoteBalance(
                orderReturn.getZohoCreditNoteId(),
                StringUtils.firstNonBlank(orderReturn.getSettlementReference(), orderReturn.getReturnNumber())
            );
        } catch (RuntimeException e) {
            LOG.warn(
                "Zoho leftover credit-note refund failed for {}: {}",
                orderReturn.getReturnNumber(),
                e.getMessage()
            );
            orderReturn.setAdminNotes(
                mergeNotes(orderReturn.getAdminNotes(), "Zoho leftover credit refund failed: " + StringUtils.left(e.getMessage(), 200))
            );
        }
    }

    private void issueReplacementInvoice(OrderReturn orderReturn) {
        CustomerOrder order = orderReturn.getOrder();
        if (order == null || order.getId() == null || !applicationProperties.getZoho().isSyncReturns()) {
            return;
        }
        if (StringUtils.isNotBlank(orderReturn.getZohoReplacementInvoiceId())) {
            return;
        }
        OrderItem item = orderReturn.getOrderItem();
        if (item == null || item.getId() == null) {
            return;
        }
        try {
            Optional<String> invoiceId = zohoBooksIntegrationClient.createExchangeReplacementInvoice(
                order.getId(),
                orderReturn.getReturnNumber(),
                item.getId(),
                orderReturn.getReplacementVariantSku(),
                orderReturn.getReplacementSize(),
                orderReturn.getReplacementColor(),
                orderReturn.getReplacementItemAmount(),
                Math.max(1, Optional.ofNullable(item.getQty()).orElse(1)),
                priorCreditNoteIds(order, orderReturn)
            );
            if (invoiceId != null && invoiceId.isPresent()) {
                orderReturn.setZohoReplacementInvoiceId(invoiceId.orElseThrow());
            }
        } catch (RuntimeException e) {
            LOG.warn("Zoho replacement invoice failed for {}: {}", orderReturn.getReturnNumber(), e.getMessage());
            orderReturn.setAdminNotes(
                mergeNotes(orderReturn.getAdminNotes(), "Zoho replacement invoice failed: " + StringUtils.left(e.getMessage(), 200))
            );
        }
    }

    private List<String> priorCreditNoteIds(CustomerOrder order, OrderReturn current) {
        List<String> ids = new ArrayList<>();
        String orderCreditNote = StringUtils.trimToNull(order.getZohoCreditNoteId());
        if (orderCreditNote != null) {
            ids.add(orderCreditNote);
        }
        if (order.getId() != null) {
            for (OrderReturn earlier : orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(order.getId())) {
                if (earlier.getRequestType() != OrderRequestType.EXCHANGE) {
                    continue;
                }
                String creditNoteId = StringUtils.trimToNull(earlier.getZohoCreditNoteId());
                if (creditNoteId != null && !ids.contains(creditNoteId)) {
                    ids.add(creditNoteId);
                }
            }
        }
        return ids;
    }

    private void releaseExchangeReservation(OrderReturn orderReturn) {
        releaseExchangeReservation(orderReturn, true);
    }

    private void releaseExchangeReservation(OrderReturn orderReturn, boolean syncZohoImmediately) {
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
            if (syncZohoImmediately) {
                saveExchangeProductAndSyncZoho(orderReturn, product);
            } else {
                productRepository.save(product);
            }
            orderReturn.setReplacementReservationReleasedAt(Instant.now());
            return;
        }
        orderReturn.setReplacementReservationReleasedAt(Instant.now());
    }

    public OrderReturn convertExchangeToRefund(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturnForUpdate(returnNumber);
        assertIsExchange(orderReturn);
        if (orderReturn.getStatus() == OrderReturnStatus.REFUNDED) {
            return orderReturn;
        }
        if (orderReturn.getStatus() != OrderReturnStatus.ACCEPTED && orderReturn.getStatus() != OrderReturnStatus.FAILED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Exchange must be accepted (or retried after a failed refund) before converting to a refund"
            );
        }
        releaseExchangeReservation(orderReturn, false);
        BigDecimal originalItemAmount = exchangeOriginalAmount(orderReturn);
        BigDecimal itemAmount = ExchangeSettlementAmounts.remainingItemRefund(orderReturn, originalItemAmount);
        if (itemAmount.signum() <= 0) {
            // Cash already went out as the price-difference refund. Do not record it again as
            // refundAmount or Razorpay/webhooks will double-count settlement + refund.
            issueExchangeCreditNote(orderReturn, true);
            OrderReturn saved = finalizeRefund(orderReturn, BigDecimal.ZERO, adminNotes, true);
            syncExchangeProductStockToZoho(saved);
            return saved;
        }
        OrderReturn saved = executeRefund(orderReturn, itemAmount, adminNotes);
        syncExchangeProductStockToZoho(saved);
        return saved;
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
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE && !isPackageCompleted(orderReturn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Create a package before marking ready to ship");
        }
        return transition(
            returnNumber,
            OrderReturnStatus.REPACKING,
            OrderReturnStatus.READY_TO_SHIP,
            adminNotes,
            "Item must be repacked before it is ready to ship"
        );
    }

    public boolean canCompletePackage(OrderReturn orderReturn) {
        return isOutboundPackageEligible(orderReturn) && StringUtils.isBlank(orderReturn.getOutboundAwb());
    }

    public boolean canBookOutbound(OrderReturn orderReturn) {
        return (
            isOutboundPackageEligible(orderReturn) &&
            orderReturn.getStatus() == OrderReturnStatus.READY_TO_SHIP &&
            isPackageCompleted(orderReturn) &&
            orderReturn.getSettlementStatus() != ExchangeSettlementStatus.PENDING &&
            StringUtils.isBlank(orderReturn.getOutboundAwb())
        );
    }

    public int defaultPackageWeightGrams(OrderReturn orderReturn) {
        int perLine = Math.max(1, applicationProperties.getXpressbees().getDefaultWeightGrams());
        int qty = Math.max(1, PackageShortShipService.exchangePackedQty(orderReturn));
        return perLine * qty;
    }

    public Map<String, Object> previewOutboundPackageRates(
        String returnNumber,
        Integer weightGrams,
        int lengthCm,
        int breadthCm,
        int heightCm
    ) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        assertOutboundPackageEligible(orderReturn);
        return orderPackageService.previewRatesForDestination(
            requireDestinationPincode(orderReturn),
            resolveOutboundWeight(orderReturn, weightGrams),
            lengthCm,
            breadthCm,
            heightCm,
            false,
            outboundOrderAmount(orderReturn)
        );
    }

    public OrderReturn completeOutboundPackage(
        String returnNumber,
        Integer weightGrams,
        int lengthCm,
        int breadthCm,
        int heightCm,
        String courierId,
        String courierName,
        String packageType
    ) {
        return completeOutboundPackage(
            returnNumber,
            weightGrams,
            lengthCm,
            breadthCm,
            heightCm,
            courierId,
            courierName,
            packageType,
            null
        );
    }

    public OrderReturn completeOutboundPackage(
        String returnNumber,
        Integer weightGrams,
        int lengthCm,
        int breadthCm,
        int heightCm,
        String courierId,
        String courierName,
        String packageType,
        Long packageBoxId
    ) {
        return completeOutboundPackage(
            returnNumber,
            weightGrams,
            lengthCm,
            breadthCm,
            heightCm,
            courierId,
            courierName,
            packageType,
            packageBoxId,
            null
        );
    }

    public OrderReturn completeOutboundPackage(
        String returnNumber,
        Integer weightGrams,
        int lengthCm,
        int breadthCm,
        int heightCm,
        String courierId,
        String courierName,
        String packageType,
        Long packageBoxId,
        List<PackageShortShipService.PackedLine> packedLines
    ) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        assertOutboundPackageEligible(orderReturn);
        if (StringUtils.isNotBlank(orderReturn.getOutboundAwb())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Outbound shipment already booked");
        }
        PackageType resolvedType = PackageType.parse(packageType).orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Package type must be custom, box or envelope"
                )
        );
        int actualWeight = resolveOutboundWeight(orderReturn, weightGrams);
        Map<String, Object> rates = orderPackageService.previewRatesForDestination(
            requireDestinationPincode(orderReturn),
            actualWeight,
            lengthCm,
            breadthCm,
            heightCm,
            false,
            outboundOrderAmount(orderReturn)
        );
        OrderPackageService.CourierSelection selected = orderPackageService.selectCourier(rates, courierId, courierName);
        if (xpressBeesClient.isForwardShipmentConfigured() && StringUtils.isBlank(selected.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No serviceable couriers for this package");
        }
        packageShortShipService.applyExchangeShortShip(orderReturn, packedQtyFromLines(orderReturn, packedLines));
        orderReturn.setPackageWeightGrams(actualWeight);
        orderReturn.setPackageLengthCm(lengthCm);
        orderReturn.setPackageBreadthCm(breadthCm);
        orderReturn.setPackageHeightCm(heightCm);
        orderReturn.setPackageChargeableWeightGrams(selected.chargeableWeightGrams());
        orderReturn.setPackageCourierId(StringUtils.left(selected.id(), 40));
        orderReturn.setPackageCourierName(StringUtils.left(selected.name(), 120));
        orderReturn.setPackageType(resolvedType);
        orderReturn.setPackageBoxId(packageBoxId);
        orderReturn.setPackedAt(Instant.now());
        if (OUTBOUND_PACKAGE_ADVANCE_STATUSES.contains(orderReturn.getStatus())) {
            orderReturn.setStatus(OrderReturnStatus.READY_TO_SHIP);
        }
        return orderReturnRepository.save(orderReturn);
    }

    private static Integer packedQtyFromLines(OrderReturn orderReturn, List<PackageShortShipService.PackedLine> packedLines) {
        if (packedLines == null || packedLines.isEmpty()) {
            return null;
        }
        Long orderItemId = orderReturn.getOrderItem() == null ? null : orderReturn.getOrderItem().getId();
        for (PackageShortShipService.PackedLine line : packedLines) {
            if (line == null || line.packedQty() == null) {
                continue;
            }
            if (orderItemId == null || line.orderItemId() == null || orderItemId.equals(line.orderItemId())) {
                return line.packedQty();
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Packed quantity must reference the exchange line item");
    }

    public Map<String, Object> shippingAddressPayload(OrderReturn orderReturn) {
        return shipmentFactory.shippingAddressPayload(orderReturn);
    }

    public Map<String, Object> dispatchAddressPayload() {
        return shipmentFactory.dispatchAddressPayload();
    }

    public String documentNumber(String prefix, OrderReturn orderReturn) {
        long id = orderReturn == null || orderReturn.getId() == null ? 0L : orderReturn.getId();
        return prefix + String.format("%05d", id);
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
        return bookOutbound(returnNumber, mode, carrierName, outboundAwb, trackingUrl, labelUrl, adminNotes, null, null, null);
    }

    public OrderReturn bookOutbound(
        String returnNumber,
        ReturnLogisticsMode mode,
        String carrierName,
        String outboundAwb,
        String trackingUrl,
        String labelUrl,
        String adminNotes,
        String courierId,
        String courierName,
        String packageType
    ) {
        return bookOutbound(
            returnNumber,
            mode,
            carrierName,
            outboundAwb,
            trackingUrl,
            labelUrl,
            adminNotes,
            courierId,
            courierName,
            packageType,
            null
        );
    }

    public OrderReturn bookOutbound(
        String returnNumber,
        ReturnLogisticsMode mode,
        String carrierName,
        String outboundAwb,
        String trackingUrl,
        String labelUrl,
        String adminNotes,
        String courierId,
        String courierName,
        String packageType,
        Long packageBoxId
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
        if (!isPackageCompleted(orderReturn)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Create a package before booking a shipment");
        }
        if (mode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logistics mode is required");
        }
        if (StringUtils.isNotBlank(packageType)) {
            PackageType resolvedType = PackageType.parse(packageType).orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Package type must be custom, box or envelope"
                    )
            );
            orderReturn.setPackageType(resolvedType);
            orderReturn.setPackageBoxId(packageBoxId);
        }
        applyOutboundCourierSelection(orderReturn, courierId, courierName);
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
        orderReturn.setOutboundCarrierStatus(XpressBeesReturnCarrierStatus.IN_TRANSIT);
        orderReturn.setStatus(OrderReturnStatus.SHIPPED);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
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
                shipmentFactory.buildForwardShipmentRequest(orderReturn, preAssignedAwb, outboundOrderAmount(orderReturn))
            );
            orderReturn.setOutboundLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
            orderReturn.setOutboundCarrierName("XpressBees");
            if (StringUtils.isNotBlank(result.courierId())) {
                orderReturn.setPackageCourierId(StringUtils.left(result.courierId(), 40));
            }
            if (StringUtils.isNotBlank(result.courierName())) {
                orderReturn.setPackageCourierName(StringUtils.left(result.courierName(), 120));
            }
            orderReturn.setOutboundAwb(StringUtils.left(result.awb(), 80));
            String tracking = StringUtils.defaultIfBlank(xpressBeesClient.buildTrackingUrl(result.awb()), result.trackingUrl());
            orderReturn.setOutboundTrackingUrl(StringUtils.left(StringUtils.trimToNull(tracking), 500));
            orderReturn.setOutboundLabelUrl(StringUtils.left(StringUtils.trimToNull(result.labelUrl()), 500));
            orderReturn.setOutboundProviderRequestId(StringUtils.left(StringUtils.trimToNull(result.providerRequestId()), 120));
            // ExpressBees AWB generation is a booking. "Shipped" waits for the first physical scan.
            orderReturn.setOutboundCarrierStatus(XpressBeesReturnCarrierStatus.AWAITING_PICKUP);
            orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
            OrderReturn saved = orderReturnRepository.save(orderReturn);
            queueReturnEmail(saved.getOrder(), saved);
            return saved;
        } catch (XpressBeesClient.XpressBeesApiException ex) {
            LOG.warn("ExpressBees forward shipment failed for {}: {}", orderReturn.getReturnNumber(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    private static boolean isOutboundPackageEligible(OrderReturn orderReturn) {
        return (
            orderReturn != null &&
            orderReturn.getRequestType() == OrderRequestType.EXCHANGE &&
            OUTBOUND_PACKAGE_STATUSES.contains(orderReturn.getStatus())
        );
    }

    private void assertOutboundPackageEligible(OrderReturn orderReturn) {
        assertIsExchange(orderReturn);
        if (!isOutboundPackageEligible(orderReturn)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Exchange replacement must be allocated or ready to ship before creating a package"
            );
        }
    }

    static boolean isPackageCompleted(OrderReturn orderReturn) {
        return (
            orderReturn != null &&
            orderReturn.getPackedAt() != null &&
            orderReturn.getPackageLengthCm() != null &&
            orderReturn.getPackageBreadthCm() != null &&
            orderReturn.getPackageHeightCm() != null &&
            orderReturn.getPackageLengthCm() >= 1 &&
            orderReturn.getPackageBreadthCm() >= 1 &&
            orderReturn.getPackageHeightCm() >= 1
        );
    }

    private int resolveOutboundWeight(OrderReturn orderReturn, Integer weightGrams) {
        if (weightGrams != null && weightGrams > 0) {
            return weightGrams;
        }
        if (orderReturn.getPackageWeightGrams() != null && orderReturn.getPackageWeightGrams() > 0) {
            return orderReturn.getPackageWeightGrams();
        }
        return defaultPackageWeightGrams(orderReturn);
    }

    private BigDecimal outboundOrderAmount(OrderReturn orderReturn) {
        if (orderReturn.getReplacementItemAmount() != null && orderReturn.getReplacementItemAmount().compareTo(BigDecimal.ZERO) > 0) {
            return orderReturn.getReplacementItemAmount();
        }
        OrderItem item = orderReturn.getOrderItem();
        if (item != null && item.getPrice() != null) {
            int qty = item.getQty() == null ? 1 : Math.max(1, item.getQty());
            return item.getPrice().multiply(BigDecimal.valueOf(qty));
        }
        return orderReturn.getOrder() == null || orderReturn.getOrder().getTotal() == null
            ? BigDecimal.ZERO
            : orderReturn.getOrder().getTotal();
    }

    private String requireDestinationPincode(OrderReturn orderReturn) {
        String pincode = shipmentFactory.destinationPincode(orderReturn);
        if (pincode == null || !pincode.matches("^[1-9]\\d{5}$")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Order shipping address must include a valid 6-digit pincode before packing"
            );
        }
        return pincode;
    }

    private static void applyOutboundCourierSelection(OrderReturn orderReturn, String courierId, String courierName) {
        String selectedId = StringUtils.trimToNull(courierId);
        if (selectedId == null) {
            return;
        }
        orderReturn.setPackageCourierId(StringUtils.left(selectedId, 40));
        String selectedName = StringUtils.trimToNull(courierName);
        if (selectedName != null) {
            orderReturn.setPackageCourierName(StringUtils.left(selectedName, 120));
        }
    }

    public OrderReturn markShipped(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
            if (!isPackageCompleted(orderReturn)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Create a package before marking shipped");
            }
            if (StringUtils.isBlank(orderReturn.getOutboundAwb())) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Book outbound shipment before marking shipped"
                );
            }
        }
        OrderReturn saved = transition(
            returnNumber,
            OrderReturnStatus.READY_TO_SHIP,
            OrderReturnStatus.SHIPPED,
            adminNotes,
            "Item must be ready to ship before marking shipped"
        );
        if (saved.getRequestType() == OrderRequestType.EXCHANGE) {
            // Non-XB has no carrier OFD scan — notify at ship. XB waits for notifyExchangeOutForDelivery.
            if (saved.getOutboundLogisticsMode() != ReturnLogisticsMode.XPRESSBEES) {
                notifyExchangeOutForDelivery(saved.getReturnNumber());
            }
        } else {
            queueReturnEmail(saved.getOrder(), saved);
        }
        return saved;
    }

    public void notifyExchangeOutForDelivery(String returnNumber) {
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getRequestType() != OrderRequestType.EXCHANGE || orderReturn.getStatus() != OrderReturnStatus.SHIPPED) {
            return;
        }
        CustomerOrder order = orderReturn.getOrder();
        if (order == null) {
            return;
        }
        AfterCommit.run(() -> {
            try {
                orderMailService.sendExchangeOutForDelivery(order, orderReturn);
            } catch (RuntimeException ex) {
                LOG.warn("Exchange out-for-delivery email failed for {}", orderReturn.getReturnNumber(), ex);
            }
        });
    }

    public OrderReturn markDelivered(String returnNumber, String adminNotes) {
        OrderReturn saved = transition(
            returnNumber,
            OrderReturnStatus.SHIPPED,
            OrderReturnStatus.DELIVERED,
            adminNotes,
            "Item must be shipped before marking delivered"
        );
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn completeExchange(String returnNumber, String adminNotes) {
        OrderReturn saved = transition(
            returnNumber,
            OrderReturnStatus.DELIVERED,
            OrderReturnStatus.COMPLETED,
            adminNotes,
            "Item must be delivered before completing the exchange"
        );
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
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
            refundAmount = merchandiseRefund(orderReturn.getOrder());
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
        if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
            issueExchangeCreditNote(orderReturn, true);
        }
        return finalizeRefund(orderReturn, refundAmount, adminNotes, true);
    }

    private OrderReturn executeRefund(OrderReturn orderReturn, BigDecimal refundAmount, String adminNotes) {
        CustomerOrder order = orderReturn.getOrder();
        if (isUncollectedCod(order)) {
            if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
                issueExchangeCreditNote(orderReturn, true);
            }
            return finalizeRefund(orderReturn, refundAmount, adminNotes, true);
        }
        if (isCollectedCod(order)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Collected COD refund must be recorded with refund method and payment reference"
            );
        }
        String existingRefundId = StringUtils.trimToNull(orderReturn.getRazorpayRefundId());
        String settlementRefundId = StringUtils.trimToNull(orderReturn.getSettlementReference());
        if (existingRefundId != null && existingRefundId.equals(settlementRefundId)) {
            existingRefundId = null;
        }
        String storedRefundId = existingRefundId;
        if (orderReturn.getStatus() == OrderReturnStatus.FAILED) {
            existingRefundId = null;
        }
        try {
            if (existingRefundId == null) {
                String refundId = razorpayRefundService
                    .findExistingRefundForOrder(order, refundAmount, RazorpayRefundService.PURPOSE_RETURN_REFUND)
                    .filter(id -> !id.equals(settlementRefundId))
                    .orElseGet(() ->
                        razorpayRefundService.refundPayment(order, refundAmount, RazorpayRefundService.PURPOSE_RETURN_REFUND)
                    );
                orderReturn.setRazorpayRefundId(refundId);
            }
            if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
                issueExchangeCreditNote(orderReturn, true);
            }
            return finalizeRefund(orderReturn, refundAmount, adminNotes, existingRefundId == null && storedRefundId == null);
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

    private OrderReturn finalizeRefund(
        OrderReturn orderReturn,
        BigDecimal refundAmount,
        String adminNotes,
        boolean restockOnThisRefund
    ) {
        CustomerOrder order = orderReturn.getOrder();
        if (restockOnThisRefund) {
            restoreRestockableStockOnRefund(orderReturn);
        }
        orderReturn.setRefundAmount(refundAmount);
        if (
            (orderReturn.getRequestType() == OrderRequestType.RETURN ||
                orderReturn.getRequestType() == OrderRequestType.EXCHANGE) &&
            order.getPaymentStatus() != PaymentStatus.REFUNDED
        ) {
            PaymentStatus nextPaymentStatus;
            boolean razorpayRefundInitiated =
                StringUtils.isNotBlank(orderReturn.getRazorpayRefundId()) &&
                orderReturn.getManualRefundRecordedAt() == null &&
                !StringUtils.equals(orderReturn.getRazorpayRefundId(), orderReturn.getSettlementReference());
            if (isUncollectedCod(order)) {
                nextPaymentStatus = order.getPaymentStatus();
            } else if (razorpayRefundInitiated) {
                nextPaymentStatus = PaymentStatus.REFUND_INITIATED;
            } else if (
                StringUtils.startsWith(orderReturn.getSettlementReference(), "rfnd_") &&
                orderReturn.getRequestType() == OrderRequestType.EXCHANGE &&
                StringUtils.isBlank(orderReturn.getRazorpayRefundId())
            ) {
                nextPaymentStatus = PaymentStatus.REFUND_INITIATED;
            } else if (cashMovedOnRefund(orderReturn, refundAmount).compareTo(money(order.getTotal())) >= 0) {
                nextPaymentStatus = PaymentStatus.REFUNDED;
            } else {
                nextPaymentStatus = PaymentStatus.PARTIALLY_REFUNDED;
            }
            if (nextPaymentStatus != order.getPaymentStatus()) {
                order.setPaymentStatus(nextPaymentStatus);
                customerOrderRepository.save(order);
            }
        }
        if (
            orderReturn.getRequestType() != OrderRequestType.EXCHANGE &&
            applicationProperties.getLedgerly().isSyncReturns() &&
            StringUtils.isBlank(orderReturn.getLedgerlyCreditNoteId())
        ) {
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
            if (
                orderReturn.getRequestType() != OrderRequestType.EXCHANGE &&
                applicationProperties.getZoho().isSyncReturns() &&
                StringUtils.isBlank(orderReturn.getZohoCreditNoteId())
            ) {
                try {
                    Optional<String> zohoCreditNoteId = zohoBooksIntegrationClient.createCreditNote(
                        order.getId(),
                        orderReturn.getReturnNumber(),
                        returnedQuantitiesForZoho(orderReturn)
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

    private static BigDecimal cashMovedOnRefund(OrderReturn orderReturn, BigDecimal refundAmount) {
        BigDecimal cash = money(refundAmount);
        if (orderReturn.getSettlementStatus() == ExchangeSettlementStatus.REFUNDED) {
            cash = cash.add(money(orderReturn.getSettlementAmount()));
        }
        return cash;
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
            refundAmount = merchandiseRefund(orderReturn.getOrder());
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
            putReturnPolicy(disabled);
            return disabled;
        }
        return customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, login)
            .map(order -> {
                var result = new LinkedHashMap<String, Object>();
                result.put("order_number", orderNumber);
                result.put("enabled", true);
                SiteSetting settings = currentSiteSettings().orElse(null);
                int windowDays = returnWindowDays(settings);
                result.put("window_days", windowDays);
                putRefundTimeline(result, settings);
                result.put("tracking_status", OrderTrackingStatusResolver.resolve(order));
                Instant deliveryAnchor = resolveDeliveryAnchor(order);
                result.put("delivered_at", deliveryAnchor);
                result.put("return_deadline", deliveryAnchor != null ? deliveryAnchor.plus(windowDays, ChronoUnit.DAYS) : null);
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
                putReturnPolicy(result);
                return result;
            });
    }

    public Map<String, Object> publicConfig() {
        var result = new LinkedHashMap<String, Object>();
        result.put("enabled", RETURNS_REFUNDS_ENABLED);
        putReturnPolicy(result);
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
        int windowDays = returnWindowDays();
        Instant windowEnd = deliveryAnchor.plus(windowDays, ChronoUnit.DAYS);
        if (Instant.now().isAfter(windowEnd)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Return window has expired (" + windowDays + " days from delivery)"
            );
        }
    }

    private int returnWindowDays() {
        return returnWindowDays(currentSiteSettings().orElse(null));
    }

    private static int returnWindowDays(SiteSetting settings) {
        return SiteSetting.resolveReturnWindowDays(settings != null ? settings.getReturnWindowDays() : null);
    }

    private Optional<SiteSetting> currentSiteSettings() {
        return siteSettingRepository.findFirstByOrderByIdAsc();
    }

    private void putRefundTimeline(Map<String, Object> result) {
        putRefundTimeline(result, currentSiteSettings().orElse(null));
    }

    private static void putRefundTimeline(Map<String, Object> result, SiteSetting settings) {
        int min = SiteSetting.resolveRefundMinBusinessDays(settings != null ? settings.getRefundMinBusinessDays() : null);
        int max = SiteSetting.resolveRefundMaxBusinessDays(
            min,
            settings != null ? settings.getRefundMaxBusinessDays() : null
        );
        result.put("refund_min_business_days", min);
        result.put("refund_max_business_days", max);
    }

    private void putReturnPolicy(Map<String, Object> result) {
        SiteSetting settings = currentSiteSettings().orElse(null);
        result.put("window_days", returnWindowDays(settings));
        putRefundTimeline(result, settings);
    }

    Instant resolveDeliveryAnchor(CustomerOrder order) {
        if (order == null) {
            return null;
        }
        // Dispatch (`shipment_date`) and tracking-pull timestamps are not delivery.
        // The return window starts only when physical delivery was recorded.
        return order.getShipmentDeliveredAt();
    }

    public String pickupTrackingUrl(OrderReturn orderReturn) {
        if (orderReturn == null) {
            return null;
        }
        String stored = StringUtils.trimToNull(orderReturn.getTrackingUrl());
        if (stored != null) {
            return stored;
        }
        if (!ReturnPickupTracking.isXpressBees(orderReturn.getLogisticsMode(), orderReturn.getCarrierName())) {
            return null;
        }
        return xpressBeesClient.buildTrackingUrl(orderReturn.getReverseAwb());
    }

    private void queueReturnEmail(CustomerOrder order, OrderReturn orderReturn) {
        queueReturnEmail(order, orderReturn, false);
    }

    private void queueReturnEmail(CustomerOrder order, OrderReturn orderReturn, boolean qcFailed) {
        if (order == null || orderReturn == null) {
            return;
        }
        AfterCommit.run(() -> {
            try {
                if (qcFailed) {
                    orderMailService.sendReturnQcFailed(order, orderReturn);
                } else {
                    orderMailService.sendReturnUpdate(order, orderReturn);
                }
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

    /**
     * Pushes current store stock for the exchanged product to Zoho after this transaction
     * commits, so a later rollback cannot leave Zoho ahead of local inventory.
     * Best-effort: does not create credit notes; failures are logged and noted.
     */
    private void saveExchangeProductAndSyncZoho(OrderReturn orderReturn, Product product) {
        productRepository.save(product);
        syncZohoInventoryAfterExchangeStockChange(orderReturn, product.getId());
    }

    private void syncZohoInventoryAfterExchangeStockChange(OrderReturn orderReturn, Long productId) {
        if (productId == null || orderReturn == null || !applicationProperties.getZoho().isSyncProductStock()) {
            return;
        }
        String returnNumber = orderReturn.getReturnNumber();
        AfterCommit.run(() -> {
            String error = null;
            try {
                Map<String, Object> result = zohoBooksIntegrationClient.syncProductStockOnly(productId);
                if (result != null && Boolean.FALSE.equals(result.get("ok"))) {
                    error = String.valueOf(result.getOrDefault("error", "Zoho stock reconcile returned ok=false"));
                }
            } catch (RuntimeException e) {
                error = e.getMessage();
            }
            if (error == null) {
                return;
            }
            LOG.warn("Zoho stock sync failed after exchange inventory change for {}: {}", returnNumber, error);
            String note = "Zoho stock sync failed: " + StringUtils.left(error, 200);
            orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), note));
            if (StringUtils.isBlank(returnNumber)) {
                return;
            }
            orderReturnRepository.findOneByReturnNumber(returnNumber).ifPresent(persisted -> {
                persisted.setAdminNotes(mergeNotes(persisted.getAdminNotes(), note));
                orderReturnRepository.save(persisted);
            });
        });
    }

    private Long restoreItemStock(OrderItem item) {
        return restoreItemStock(item, item == null ? 0 : Optional.ofNullable(item.getQty()).orElse(0));
    }

    private Long restoreItemStock(OrderItem item, int quantity) {
        if (item == null || StringUtils.isBlank(item.getProductId()) || quantity <= 0) {
            return null;
        }
        try {
            long productId = Long.parseLong(item.getProductId());
            Optional<Product> productOpt = productRepository.findOneForInventoryUpdate(productId);
            if (productOpt.isEmpty()) {
                return null;
            }
            Product product = productOpt.orElseThrow();
            if (variantSupport.isVariants(product)) {
                if (!variantSupport.incrementVariantStock(
                    product,
                    item.getVariantSku(),
                    item.getItemSize(),
                    item.getColor(),
                    quantity
                )) {
                    product.setStock(Optional.ofNullable(product.getStock()).orElse(0) + quantity);
                }
            } else {
                product.setStock(Optional.ofNullable(product.getStock()).orElse(0) + quantity);
            }
            productRepository.save(product);
            return product.getId();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Restocks the exchanged original once, when the replacement is allocated (or the exchange is
     * converted to a refund). Accepting an exchange only records disposition — it must not change stock.
     */
    private void restoreExchangeOriginalIfRestockable(OrderReturn orderReturn) {
        if (orderReturn.getRequestType() != OrderRequestType.EXCHANGE) {
            return;
        }
        if (orderReturn.getDisposition() != ReturnDisposition.RESTOCKABLE) {
            return;
        }
        if (orderReturn.getOriginalRestockedAt() != null) {
            return;
        }
        Long productId = restoreItemStock(orderReturn.getOrderItem());
        if (productId != null) {
            orderReturn.setOriginalRestockedAt(Instant.now());
        }
    }

    private void syncExchangeProductStockToZoho(OrderReturn orderReturn) {
        OrderItem item = orderReturn == null ? null : orderReturn.getOrderItem();
        if (item == null || StringUtils.isBlank(item.getProductId())) {
            return;
        }
        try {
            syncZohoInventoryAfterExchangeStockChange(orderReturn, Long.parseLong(item.getProductId()));
        } catch (NumberFormatException ignored) {
            // Catalog ids that are not numeric cannot be reconciled.
        }
    }

    /**
     * Local restock happens on the first successful refund money movement, matching the Zoho
     * credit note. Retries that already have a Razorpay refund id skip this so stock is not added twice.
     */
    private void restoreRestockableStockOnRefund(OrderReturn orderReturn) {
        if (orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
            restoreExchangeOriginalIfRestockable(orderReturn);
            return;
        }
        if (orderReturn.getRequestType() != OrderRequestType.RETURN) {
            return;
        }
        List<OrderReturnItem> lines = orderReturn.getId() == null
            ? List.of()
            : orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId());
        if (!lines.isEmpty()) {
            for (OrderReturnItem line : lines) {
                int acceptedQty = creditedQuantity(line);
                ReturnDisposition disposition = line.getDisposition() != null
                    ? line.getDisposition()
                    : orderReturn.getDisposition();
                if (acceptedQty > 0 && disposition == ReturnDisposition.RESTOCKABLE) {
                    restoreItemStock(line.getOrderItem(), acceptedQty);
                }
            }
            return;
        }
        if (orderReturn.getDisposition() != ReturnDisposition.RESTOCKABLE) {
            return;
        }
        if (orderReturn.getOrderItem() != null) {
            restoreItemStock(orderReturn.getOrderItem());
            return;
        }
        restoreInventoryForOrder(orderReturn.getOrder());
    }

    private void restoreInventoryForOrder(CustomerOrder order) {
        orderItemRepository.findAllByOrderId(order.getId()).forEach(this::restoreItemStock);
    }

    private Map<Long, Integer> returnedQuantitiesForZoho(OrderReturn orderReturn) {
        Map<Long, Integer> qtyByItemId = new LinkedHashMap<>();
        if (orderReturn.getId() != null) {
            for (OrderReturnItem line : orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId())) {
                if (line.getOrderItem() == null || line.getOrderItem().getId() == null) {
                    continue;
                }
                int qty = creditedQuantity(line);
                if (qty > 0) {
                    qtyByItemId.merge(line.getOrderItem().getId(), qty, Integer::sum);
                }
            }
        }
        if (qtyByItemId.isEmpty() && orderReturn.getOrderItem() != null && orderReturn.getOrderItem().getId() != null) {
            if (orderReturn.getId() == null || orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId()).isEmpty()) {
                qtyByItemId.put(
                    orderReturn.getOrderItem().getId(),
                    Math.max(1, Optional.ofNullable(orderReturn.getOrderItem().getQty()).orElse(1))
                );
            }
        }
        return qtyByItemId;
    }

    /**
     * Best-effort decrement when an item must be removed from sellable stock after it was credited
     * (e.g. operational correction). Not used in the standard exchange accept/allocate flow.
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
            if (variantSupport.isVariants(product)) {
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

    private static boolean hasReverseShipment(OrderReturn orderReturn) {
        return orderReturn.getLogisticsMode() != null || StringUtils.isNotBlank(orderReturn.getReverseAwb());
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

    private OrderReturn loadReturnForUpdate(String returnNumber) {
        return orderReturnRepository
            .findOneByReturnNumberForUpdate(returnNumber)
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

    /**
     * Warehouse inspection writes acceptedQty (including 0). Missing acceptedQty is not a full
     * accept — do not restock or credit requested units without that decision.
     */
    private static int creditedQuantity(OrderReturnItem line) {
        if (line == null || line.getAcceptedQty() == null) {
            return 0;
        }
        return Math.max(0, line.getAcceptedQty());
    }

    /**
     * Prefer the net amount snapshotted at reservation. If that is missing (legacy rows),
     * recompute with the same discount allocation as approve — never live gross price × qty.
     */
    private BigDecimal exchangeOriginalAmount(OrderReturn orderReturn) {
        if (orderReturn.getOriginalItemAmount() != null) {
            return money(orderReturn.getOriginalItemAmount());
        }
        OrderItem item = orderReturn.getOrderItem();
        if (item == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        int qty = Optional.ofNullable(item.getQty()).orElse(1);
        return netMerchandiseAmount(item, orderReturn.getOrder(), qty);
    }

    private BigDecimal netMerchandiseAmount(OrderItem item, CustomerOrder order, int qty) {
        if (item == null || qty <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal originalGross = money(item.getPrice()).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        if (order == null) {
            return originalGross;
        }
        int orderedQty = Math.max(1, Optional.ofNullable(item.getQty()).orElse(1));
        BigDecimal orderSubtotal = OrderMerchandiseAmounts.positiveOrFallback(order.getSubtotal(), originalGross);
        List<OrderItem> siblings = order.getId() == null
            ? List.of(item)
            : orderItemRepository.findAllByOrderId(order.getId());
        if (siblings.isEmpty()) {
            siblings = List.of(item);
        }
        BigDecimal allocatedDiscount = OrderMerchandiseAmounts.allocatedDiscountFor(
            item,
            qty,
            orderedQty,
            orderSubtotal,
            money(order.getDiscount()),
            OrderMerchandiseAmounts.orderHasPersistedLineAllocations(siblings)
        ).min(originalGross);
        return originalGross.subtract(allocatedDiscount).max(BigDecimal.ZERO);
    }

    /**
     * Product net only. Delivery, COD fee, and donation stay with the shop on returns and exchanges.
     */
    static BigDecimal merchandiseRefund(CustomerOrder order) {
        if (order == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return money(order.getSubtotal()).subtract(money(order.getDiscount())).max(BigDecimal.ZERO);
    }

    /**
     * New orders persist per-line {@code discountAllocation}; legacy orders keep zeros and fall back
     * to proportional share of the order discount.
     */
    @Transactional(readOnly = true)
    public List<String> imageUrls(OrderReturn orderReturn) {
        return parseImageUrls(orderReturn == null ? null : orderReturn.getImageUrlsJson());
    }

    private String serializeReturnImageUrls(List<String> imageUrls) {
        List<String> normalized = normalizeReturnImageUrls(imageUrls);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return IMAGE_URLS_JSON.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not save return images");
        }
    }

    private static List<String> parseImageUrls(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            JsonNode node = IMAGE_URLS_JSON.readTree(json);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> urls = new ArrayList<>();
            node.forEach(item -> {
                if (item != null && item.isTextual() && StringUtils.isNotBlank(item.asText())) {
                    urls.add(item.asText().trim());
                }
            });
            return List.copyOf(urls);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> normalizeReturnImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        String publicBase = null;
        if (applicationProperties.getStorage() != null && applicationProperties.getStorage().getS3() != null) {
            publicBase = applicationProperties.getStorage().getS3().getPublicBaseUrl();
        }
        List<String> normalized = new ArrayList<>();
        for (String raw : imageUrls) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            String url = raw.trim();
            if (url.length() > MAX_IMAGE_URL_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return image URL is too long");
            }
            String lower = url.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return image URL must be http or https");
            }
            if (StringUtils.isNotBlank(publicBase)) {
                var objectKey = ImagePreviewSupport.objectKeyFromPublicUrl(publicBase, url);
                String key = objectKey.orElse("");
                if (key.isEmpty() || !(key.startsWith("returns/") || key.contains("/returns/"))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return image must be uploaded for this request");
                }
            }
            if (!normalized.contains(url)) {
                normalized.add(url);
            }
            if (normalized.size() > MAX_RETURN_IMAGES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can attach up to " + MAX_RETURN_IMAGES + " images");
            }
        }
        return normalized;
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
