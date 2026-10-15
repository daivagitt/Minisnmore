package com.miniandmore.app.service;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.client.ZohoBooksIntegrationClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * When ops pack fewer units than ordered (damaged / missing stock), persist packed qty,
 * restock the leftover, and refund the customer for units that will not ship.
 */
@Service
public class PackageShortShipService {

    public static final String PURPOSE_PACKAGE_SHORT_SHIP = RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP;

    private static final Logger LOG = LoggerFactory.getLogger(PackageShortShipService.class);

    private final OrderItemRepository orderItemRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final ProductRepository productRepository;
    private final ProductVariantSupport variantSupport;
    private final RazorpayRefundService razorpayRefundService;
    private final ZohoBooksIntegrationClient zohoBooksIntegrationClient;
    private final LedgerlyIntegrationClient ledgerlyIntegrationClient;
    private final OrderMailService orderMailService;
    private final ApplicationProperties applicationProperties;

    public PackageShortShipService(
        OrderItemRepository orderItemRepository,
        CustomerOrderRepository customerOrderRepository,
        ProductRepository productRepository,
        ProductVariantSupport variantSupport,
        RazorpayRefundService razorpayRefundService,
        ZohoBooksIntegrationClient zohoBooksIntegrationClient,
        LedgerlyIntegrationClient ledgerlyIntegrationClient,
        OrderMailService orderMailService,
        ApplicationProperties applicationProperties
    ) {
        this.orderItemRepository = orderItemRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.productRepository = productRepository;
        this.variantSupport = variantSupport;
        this.razorpayRefundService = razorpayRefundService;
        this.zohoBooksIntegrationClient = zohoBooksIntegrationClient;
        this.ledgerlyIntegrationClient = ledgerlyIntegrationClient;
        this.orderMailService = orderMailService;
        this.applicationProperties = applicationProperties;
    }

    public record PackedLine(Long orderItemId, Integer packedQty) {}

    public static int orderedQty(OrderItem item) {
        if (item == null || item.getQty() == null) {
            return 1;
        }
        return Math.max(1, item.getQty());
    }

    public static int packedQty(OrderItem item) {
        if (item == null) {
            return 1;
        }
        if (item.getPackedQty() != null) {
            return Math.max(0, item.getPackedQty());
        }
        return orderedQty(item);
    }

    public static int exchangeOrderedQty(OrderReturn orderReturn) {
        OrderItem item = orderReturn == null ? null : orderReturn.getOrderItem();
        return orderedQty(item);
    }

    public static int exchangePackedQty(OrderReturn orderReturn) {
        if (orderReturn == null) {
            return 1;
        }
        if (orderReturn.getOutboundPackedQty() != null) {
            return Math.max(0, orderReturn.getOutboundPackedQty());
        }
        return exchangeOrderedQty(orderReturn);
    }

    public void applyForwardShortShip(CustomerOrder order, List<OrderItem> items, List<PackedLine> requested) {
        if (order == null || items == null || items.isEmpty()) {
            return;
        }
        Map<Long, Integer> requestedById = requestedById(requested);
        boolean alreadyPacked = items.stream().anyMatch(item -> item.getPackedQty() != null);
        if (alreadyPacked) {
            assertRequestedMatchesExisting(items, requestedById);
            return;
        }
        int totalPacked = 0;
        Map<Long, Integer> shortQtyByItemId = new LinkedHashMap<>();
        for (OrderItem item : items) {
            int ordered = orderedQty(item);
            int packed = resolvePacked(item, requestedById, ordered);
            if (packed < 0 || packed > ordered) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Packed quantity must be between 0 and the ordered quantity");
            }
            item.setPackedQty(packed);
            totalPacked += packed;
            int shortQty = ordered - packed;
            if (shortQty > 0 && item.getId() != null) {
                shortQtyByItemId.put(item.getId(), shortQty);
            }
        }
        if (totalPacked < 1) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Pack at least one unit, or cancel the order if nothing can be shipped"
            );
        }
        orderItemRepository.saveAll(items);
        if (shortQtyByItemId.isEmpty()) {
            return;
        }
        BigDecimal refund = OrderMerchandiseAmounts.merchandiseRefundForQuantities(order, items, shortQtyByItemId);
        for (OrderItem item : items) {
            Integer shortQty = item.getId() == null ? null : shortQtyByItemId.get(item.getId());
            if (shortQty != null && shortQty > 0) {
                restoreItemStock(item, shortQty);
            }
        }
        settleForwardRefund(order, refund, shortQtyByItemId);
    }

    public void applyExchangeShortShip(OrderReturn orderReturn, Integer requestedPackedQty) {
        if (orderReturn == null) {
            return;
        }
        int ordered = exchangeOrderedQty(orderReturn);
        if (orderReturn.getOutboundPackedQty() != null) {
            if (requestedPackedQty != null && requestedPackedQty != orderReturn.getOutboundPackedQty()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Packed quantities cannot be changed after the package is saved"
                );
            }
            return;
        }
        int packed = requestedPackedQty == null ? ordered : requestedPackedQty;
        if (packed < 1 || packed > ordered) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                packed < 1
                    ? "Pack at least one replacement unit, or convert this exchange to a refund"
                    : "Packed quantity cannot exceed the replacement quantity"
            );
        }
        orderReturn.setOutboundPackedQty(packed);
        int shortQty = ordered - packed;
        if (shortQty <= 0) {
            return;
        }
        restoreReplacementStock(orderReturn, shortQty, ordered);
        BigDecimal originalAmount = exchangeOriginalAmount(orderReturn);
        BigDecimal remaining = ExchangeSettlementAmounts.remainingItemRefund(orderReturn, originalAmount);
        BigDecimal refund = remaining
            .multiply(BigDecimal.valueOf(shortQty))
            .divide(BigDecimal.valueOf(ordered), 2, RoundingMode.HALF_UP);
        settleExchangeRefund(orderReturn, refund, shortQty);
    }

    private void assertRequestedMatchesExisting(List<OrderItem> items, Map<Long, Integer> requestedById) {
        if (requestedById.isEmpty()) {
            return;
        }
        for (OrderItem item : items) {
            if (item.getId() == null || !requestedById.containsKey(item.getId())) {
                continue;
            }
            int existing = packedQty(item);
            if (requestedById.get(item.getId()) != existing) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Packed quantities cannot be changed after the package is saved"
                );
            }
        }
    }

    private static int resolvePacked(OrderItem item, Map<Long, Integer> requestedById, int ordered) {
        if (item.getId() != null && requestedById.containsKey(item.getId())) {
            Integer packed = requestedById.get(item.getId());
            return packed == null ? ordered : packed;
        }
        return ordered;
    }

    private static Map<Long, Integer> requestedById(List<PackedLine> requested) {
        Map<Long, Integer> byId = new LinkedHashMap<>();
        if (requested == null) {
            return byId;
        }
        for (PackedLine line : requested) {
            if (line == null || line.orderItemId() == null || line.packedQty() == null) {
                continue;
            }
            if (byId.putIfAbsent(line.orderItemId(), line.packedQty()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A packed line was specified more than once");
            }
        }
        return byId;
    }

    private void settleForwardRefund(CustomerOrder order, BigDecimal refund, Map<Long, Integer> shortQtyByItemId) {
        if (refund == null || refund.signum() <= 0) {
            return;
        }
        order.setPackageShortRefundAmount(refund);
        if (isUncollectedCod(order)) {
            order.setTotal(money(order.getTotal()).subtract(refund).max(BigDecimal.ZERO));
            customerOrderRepository.save(order);
            issueCreditNotes(order, "PKG-SHORT-" + order.getOrderNumber(), shortQtyByItemId, true, null, refund);
            return;
        }
        if (isPrepaidRazorpay(order)) {
            String refundId = razorpayRefundService
                .findExistingRefundForOrder(order, refund, PURPOSE_PACKAGE_SHORT_SHIP)
                .orElseGet(() -> razorpayRefundService.refundPayment(order, refund, PURPOSE_PACKAGE_SHORT_SHIP));
            order.setPackageShortRazorpayRefundId(refundId);
            order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
            customerOrderRepository.save(order);
            issueCreditNotes(order, "PKG-SHORT-" + order.getOrderNumber(), shortQtyByItemId, true, null, refund);
            return;
        }
        order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        order.setNotes(mergeNotes(order.getNotes(), "Packed short of ordered qty. Pending COD refund of ₹" + refund.toPlainString()));
        customerOrderRepository.save(order);
        issueCreditNotes(order, "PKG-SHORT-" + order.getOrderNumber(), shortQtyByItemId, true, null, refund);
    }

    private void settleExchangeRefund(OrderReturn orderReturn, BigDecimal refund, int shortQty) {
        if (refund == null || refund.signum() <= 0) {
            return;
        }
        orderReturn.setPackageShortRefundAmount(refund);
        CustomerOrder order = orderReturn.getOrder();
        Map<Long, Integer> qtyMap = new LinkedHashMap<>();
        if (orderReturn.getOrderItem() != null && orderReturn.getOrderItem().getId() != null) {
            qtyMap.put(orderReturn.getOrderItem().getId(), shortQty);
        }
        String creditRef = "PKG-SHORT-" + orderReturn.getReturnNumber();
        if (order != null && isPrepaidRazorpay(order)) {
            String refundId = razorpayRefundService
                .findExistingRefundForOrder(order, refund, PURPOSE_PACKAGE_SHORT_SHIP)
                .orElseGet(() -> razorpayRefundService.refundPayment(order, refund, PURPOSE_PACKAGE_SHORT_SHIP));
            orderReturn.setPackageShortRazorpayRefundId(refundId);
            if (order.getPaymentStatus() != PaymentStatus.REFUNDED) {
                order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
                customerOrderRepository.save(order);
            }
            issueCreditNotes(order, creditRef, qtyMap, false, orderReturn.getZohoReplacementInvoiceId(), refund);
            return;
        }
        if (order != null && isUncollectedCod(order)) {
            order.setTotal(money(order.getTotal()).subtract(refund).max(BigDecimal.ZERO));
            customerOrderRepository.save(order);
            issueCreditNotes(order, creditRef, qtyMap, false, orderReturn.getZohoReplacementInvoiceId(), refund);
            return;
        }
        if (order != null && order.getPaymentStatus() != PaymentStatus.REFUNDED) {
            order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
            customerOrderRepository.save(order);
        }
        issueCreditNotes(order, creditRef, qtyMap, false, orderReturn.getZohoReplacementInvoiceId(), refund);
    }

    private void issueCreditNotes(
        CustomerOrder order,
        String reference,
        Map<Long, Integer> qtyByItemId,
        boolean linkToSourceInvoice,
        String applyToInvoiceId,
        BigDecimal refundAmount
    ) {
        if (order == null || order.getId() == null) {
            return;
        }
        BigDecimal ledgerlyAmount = refundAmount != null && refundAmount.signum() > 0
            ? refundAmount
            : money(order.getPackageShortRefundAmount());
        try {
            if (applicationProperties.getLedgerly() != null && applicationProperties.getLedgerly().isSyncReturns()) {
                ledgerlyIntegrationClient.createCreditNote(order.getOrderNumber(), reference, ledgerlyAmount);
            }
        } catch (RuntimeException ex) {
            LOG.warn("Ledgerly credit note failed for package short-ship {}: {}", reference, ex.getMessage());
        }
        try {
            if (applicationProperties.getZoho() != null && applicationProperties.getZoho().isSyncReturns()) {
                Optional<String> creditNoteId = zohoBooksIntegrationClient.createCreditNote(
                    order.getId(),
                    reference,
                    qtyByItemId,
                    linkToSourceInvoice
                );
                if (creditNoteId.isPresent() && StringUtils.isNotBlank(applyToInvoiceId)) {
                    zohoBooksIntegrationClient.applyCreditNoteToInvoice(creditNoteId.orElseThrow(), applyToInvoiceId);
                }
            }
        } catch (RuntimeException ex) {
            LOG.warn("Zoho credit note failed for package short-ship {}: {}", reference, ex.getMessage());
        }
    }

    private void restoreReplacementStock(OrderReturn orderReturn, int shortQty, int ordered) {
        OrderItem item = orderReturn.getOrderItem();
        if (item == null || StringUtils.isBlank(item.getProductId()) || shortQty <= 0) {
            return;
        }
        try {
            Product product = productRepository.findOneForInventoryUpdate(Long.parseLong(item.getProductId())).orElse(null);
            if (product == null) {
                return;
            }
            boolean restored = variantSupport.incrementVariantStock(
                product,
                orderReturn.getReplacementVariantSku(),
                orderReturn.getReplacementSize(),
                orderReturn.getReplacementColor(),
                shortQty
            );
            if (!restored) {
                product.setStock(Optional.ofNullable(product.getStock()).orElse(0) + shortQty);
            }
            productRepository.save(product);
            if (shortQty >= ordered) {
                orderReturn.setReplacementReservationReleasedAt(java.time.Instant.now());
            }
        } catch (NumberFormatException ignored) {
            // Catalog ids that are not numeric cannot be restocked locally.
        }
    }

    private void restoreItemStock(OrderItem item, int quantity) {
        if (item == null || StringUtils.isBlank(item.getProductId()) || quantity <= 0) {
            return;
        }
        try {
            Product product = productRepository.findOneForInventoryUpdate(Long.parseLong(item.getProductId())).orElse(null);
            if (product == null) {
                return;
            }
            if (variantSupport.isVariants(product)) {
                if (
                    !variantSupport.incrementVariantStock(
                        product,
                        item.getVariantSku(),
                        item.getItemSize(),
                        item.getColor(),
                        quantity
                    )
                ) {
                    product.setStock(Optional.ofNullable(product.getStock()).orElse(0) + quantity);
                }
            } else {
                product.setStock(Optional.ofNullable(product.getStock()).orElse(0) + quantity);
            }
            productRepository.save(product);
        } catch (NumberFormatException ignored) {
            // Catalog ids that are not numeric cannot be restocked locally.
        }
    }

    private BigDecimal exchangeOriginalAmount(OrderReturn orderReturn) {
        if (orderReturn.getOriginalItemAmount() != null) {
            return money(orderReturn.getOriginalItemAmount());
        }
        OrderItem item = orderReturn.getOrderItem();
        if (item == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        Map<Long, Integer> qty = Map.of(item.getId() == null ? -1L : item.getId(), exchangeOrderedQty(orderReturn));
        List<OrderItem> siblings = item.getId() == null || orderReturn.getOrder() == null || orderReturn.getOrder().getId() == null
            ? List.of(item)
            : orderItemRepository.findAllByOrderId(orderReturn.getOrder().getId());
        if (siblings.isEmpty()) {
            siblings = List.of(item);
        }
        return OrderMerchandiseAmounts.merchandiseRefundForQuantities(orderReturn.getOrder(), siblings, qty);
    }

    public void notifyPackageShortRefundProcessed(CustomerOrder order, BigDecimal refund, String refundId) {
        AfterCommit.run(() -> {
            try {
                orderMailService.sendRefundCompleted(order, refund, refundId, order.getPaymentMethod());
            } catch (RuntimeException ex) {
                LOG.warn("Package short-ship refund email failed for {}", order.getOrderNumber(), ex);
            }
        });
    }

    private static boolean isPrepaidRazorpay(CustomerOrder order) {
        return ExchangeSettlementAmounts.isPrepaidRazorpayEligible(order);
    }

    private static boolean isUncollectedCod(CustomerOrder order) {
        return order != null && "cod".equalsIgnoreCase(order.getPaymentMethod()) && order.getPaymentStatus() != PaymentStatus.PAID;
    }

    private static BigDecimal money(BigDecimal value) {
        return OrderMerchandiseAmounts.money(value);
    }

    private static String mergeNotes(String existing, String extra) {
        String left = StringUtils.trimToEmpty(existing);
        String right = StringUtils.trimToEmpty(extra);
        if (right.isEmpty()) {
            return StringUtils.left(left, 500);
        }
        if (left.isEmpty()) {
            return StringUtils.left(right, 500);
        }
        return StringUtils.left(left + "\n" + right, 500);
    }
}
