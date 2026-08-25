package com.miniandmore.app.service;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.client.ZohoBooksIntegrationClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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

    public OrderReturnService(
        OrderReturnRepository orderReturnRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderItemRepository orderItemRepository,
        ProductRepository productRepository,
        RazorpayRefundService razorpayRefundService,
        LedgerlyIntegrationClient ledgerlyIntegrationClient,
        ZohoBooksIntegrationClient zohoBooksIntegrationClient,
        ProductVariantSupport variantSupport,
        OrderMailService orderMailService,
        OrderSmsService orderSmsService,
        ApplicationProperties applicationProperties
    ) {
        this.orderReturnRepository = orderReturnRepository;
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

    // ---------------------------------------------------------------------
    // Request creation (Return / Exchange)
    // ---------------------------------------------------------------------

    public OrderReturn requestReturn(String userLogin, String orderNumber, String reason) {
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
        queueReturnEmail(order, saved);
        return saved;
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
            restoreItemStock(orderReturn.getOrderItem());
        } else {
            restoreInventoryForOrder(orderReturn.getOrder());
        }
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        orderReturn.setAdminNotes(trimNotes(adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
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
        boolean reversed = orderReturn.getRequestType() == OrderRequestType.EXCHANGE
            ? reverseItemStock(orderReturn.getOrderItem())
            : reverseInventoryForOrder(orderReturn.getOrder());
        orderReturn.setStatus(OrderReturnStatus.REJECTED);
        orderReturn.setRejectionReason(reason);
        String notes = mergeNotes(orderReturn.getAdminNotes(), adminNotes);
        if (!reversed) {
            notes = mergeNotes(notes, "Note: inventory could not be fully reversed (item may have already been resold).");
        }
        orderReturn.setAdminNotes(notes);
        orderReturn.setResolvedAt(Instant.now());
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
            OrderReturnStatus.PICKUP_REQUESTED,
            OrderReturnStatus.PICKED_UP,
            adminNotes,
            "Pickup must be requested before marking picked up"
        );
    }

    public OrderReturn markReceived(String returnNumber, String adminNotes) {
        OrderReturn saved = transition(
            returnNumber,
            EnumSet.of(OrderReturnStatus.APPROVED, OrderReturnStatus.PICKUP_REQUESTED, OrderReturnStatus.PICKED_UP),
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

    public OrderReturn acceptAfterInspection(String returnNumber, String adminNotes) {
        return transition(
            returnNumber,
            OrderReturnStatus.INSPECTED,
            OrderReturnStatus.ACCEPTED,
            adminNotes,
            "Return must be inspected before accepting"
        );
    }

    // ---------------------------------------------------------------------
    // Exchange-only fulfilment
    // ---------------------------------------------------------------------

    public OrderReturn allocateReplacementItem(String returnNumber, String adminNotes) {
        assertFeatureEnabled();
        OrderReturn orderReturn = loadReturn(returnNumber);
        assertIsExchange(orderReturn);
        if (orderReturn.getStatus() != OrderReturnStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exchange must be accepted before allocating the replacement");
        }
        OrderItem item = orderReturn.getOrderItem();
        Product product = resolveProductForUpdate(item.getProductId()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Original product could not be resolved")
        );
        int qty = Optional.ofNullable(item.getQty()).orElse(1);
        boolean allocated = variantSupport.decrementVariantStock(
            product,
            orderReturn.getReplacementVariantSku(),
            orderReturn.getReplacementSize(),
            orderReturn.getReplacementColor(),
            qty
        );
        if (!allocated) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Replacement is no longer in stock; convert this exchange to a refund instead."
            );
        }
        productRepository.save(product);
        orderReturn.setStatus(OrderReturnStatus.ITEM_ALLOCATED);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        return orderReturnRepository.save(orderReturn);
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
        OrderItem item = orderReturn.getOrderItem();
        BigDecimal itemAmount = item.getPrice().multiply(BigDecimal.valueOf(Optional.ofNullable(item.getQty()).orElse(1)));
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
        CustomerOrder order = orderReturn.getOrder();
        return executeRefund(orderReturn, order.getTotal(), adminNotes);
    }

    private OrderReturn executeRefund(OrderReturn orderReturn, BigDecimal refundAmount, String adminNotes) {
        CustomerOrder order = orderReturn.getOrder();
        String existingRefundId = StringUtils.trimToNull(orderReturn.getRazorpayRefundId());
        try {
            if (existingRefundId == null) {
                String refundId = razorpayRefundService.refundPayment(order, refundAmount);
                orderReturn.setRazorpayRefundId(refundId);
            }
            orderReturn.setRefundAmount(refundAmount);
            if (orderReturn.getRequestType() == OrderRequestType.RETURN && order.getPaymentStatus() != PaymentStatus.REFUNDED) {
                order.setPaymentStatus(PaymentStatus.REFUNDED);
                customerOrderRepository.save(order);
            }
            if (applicationProperties.getLedgerly().isSyncReturns() && StringUtils.isBlank(orderReturn.getLedgerlyCreditNoteId())) {
                try {
                    Optional<String> creditNoteId = ledgerlyIntegrationClient.cancelOrder(
                        order.getOrderNumber(),
                        orderReturn.getReturnNumber(),
                        orderReturn.getRazorpayRefundId()
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

    // ---------------------------------------------------------------------
    // Inventory helpers
    // ---------------------------------------------------------------------

    private void restoreItemStock(OrderItem item) {
        if (item == null || StringUtils.isBlank(item.getProductId())) {
            return;
        }
        try {
            long productId = Long.parseLong(item.getProductId());
            productRepository
                .findOneForInventoryUpdate(productId)
                .ifPresent(product -> {
                    int qty = Optional.ofNullable(item.getQty()).orElse(0);
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
