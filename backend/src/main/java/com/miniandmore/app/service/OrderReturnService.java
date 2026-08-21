package com.miniandmore.app.service;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.client.ZohoBooksIntegrationClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
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
    private static final int RETURN_WINDOW_DAYS = 7;

    private static final Set<OrderReturnStatus> OPEN_RETURN_STATUSES = EnumSet.of(
        OrderReturnStatus.REQUESTED,
        OrderReturnStatus.APPROVED,
        OrderReturnStatus.RECEIVED,
        OrderReturnStatus.FAILED
    );

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

    public OrderReturn requestReturn(String userLogin, String orderNumber, String reason) {
        String normalizedReason = StringUtils.normalizeSpace(StringUtils.defaultString(reason)).trim();
        if (normalizedReason.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please describe the return reason (at least 10 characters)");
        }
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, userLogin)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        assertReturnEligible(order);
        if (orderReturnRepository.existsByOrderIdAndStatusIn(order.getId(), OPEN_RETURN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A return request is already open for this order");
        }
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber(generateReturnNumber(order.getOrderNumber()));
        orderReturn.setStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setReason(normalizedReason.length() <= 4000 ? normalizedReason : normalizedReason.substring(0, 4000));
        orderReturn.setRequestedAt(Instant.now());
        orderReturn.setOrder(order);
        orderReturn.setUserLogin(userLogin);
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(order, saved);
        return saved;
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

    public OrderReturn approve(String returnNumber, String adminNotes) {
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return is not awaiting approval");
        }
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        orderReturn.setAdminNotes(trimNotes(adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn reject(String returnNumber, String rejectionReason, String adminNotes) {
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return is not awaiting approval");
        }
        String reason = StringUtils.normalizeSpace(StringUtils.defaultString(rejectionReason)).trim();
        if (reason.length() < 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
        orderReturn.setStatus(OrderReturnStatus.REJECTED);
        orderReturn.setRejectionReason(reason.length() <= 1000 ? reason : reason.substring(0, 1000));
        orderReturn.setAdminNotes(trimNotes(adminNotes));
        orderReturn.setResolvedAt(Instant.now());
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn markReceived(String returnNumber, String adminNotes) {
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return must be approved before marking received");
        }
        orderReturn.setStatus(OrderReturnStatus.RECEIVED);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueReturnEmail(saved.getOrder(), saved);
        return saved;
    }

    public OrderReturn processRefund(String returnNumber, String adminNotes) {
        OrderReturn orderReturn = loadReturn(returnNumber);
        if (orderReturn.getStatus() != OrderReturnStatus.RECEIVED && orderReturn.getStatus() != OrderReturnStatus.FAILED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Return must be received (or retry after a failed refund) before processing refund"
            );
        }
        CustomerOrder order = orderReturn.getOrder();
        BigDecimal refundAmount = order.getTotal();
        String existingRefundId = StringUtils.trimToNull(orderReturn.getRazorpayRefundId());
        try {
            if (existingRefundId == null) {
                String refundId = razorpayRefundService.refundPayment(order, refundAmount);
                orderReturn.setRazorpayRefundId(refundId);
            }
            orderReturn.setRefundAmount(refundAmount);
            if (order.getPaymentStatus() != PaymentStatus.REFUNDED) {
                restoreInventory(order);
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
                orderReturnRepository.save(orderReturn);
            }
            throw e;
        }
    }

    public Map<String, Object> eligibilityForUser(String login, String orderNumber) {
        return customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, login)
            .map(order -> {
                var result = new LinkedHashMap<String, Object>();
                result.put("order_number", orderNumber);
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
                return result;
            });
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

    private void restoreInventory(CustomerOrder order) {
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        for (OrderItem item : items) {
            if (StringUtils.isBlank(item.getProductId())) {
                continue;
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
