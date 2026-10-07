package com.miniandmore.app.service;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Raises the replacement sales invoice for an exchange and applies earlier credit notes
 * (this exchange and any previous credit notes on the same order) so the balance due is
 * the price difference.
 */
@Service
public class ZohoExchangeInvoiceService {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoExchangeInvoiceService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final ZohoBooksClient booksClient;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ZohoCreditNoteOrderSupport orderSupport;

    public ZohoExchangeInvoiceService(
        ZohoBooksClient booksClient,
        CustomerOrderRepository customerOrderRepository,
        OrderItemRepository orderItemRepository,
        @Lazy ZohoCreditNoteOrderSupport orderSupport
    ) {
        this.booksClient = booksClient;
        this.customerOrderRepository = customerOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderSupport = orderSupport;
    }

    public static String replacementInvoiceReference(String returnNumber) {
        return "EXINV-" + StringUtils.defaultString(returnNumber);
    }

    public Optional<String> createReplacementInvoice(
        Long orderId,
        String returnNumber,
        Long orderItemId,
        String replacementSku,
        String replacementSize,
        String replacementColor,
        BigDecimal replacementAmount,
        int quantity,
        List<String> creditNoteIds
    ) {
        CustomerOrder order = customerOrderRepository.findById(orderId).orElse(null);
        OrderItem original = orderItemId == null ? null : orderItemRepository.findById(orderItemId).orElse(null);
        if (order == null || original == null) {
            return Optional.empty();
        }
        int qty = Math.max(1, quantity);
        BigDecimal amount = money(replacementAmount);
        if (amount.signum() <= 0) {
            amount = money(original.getPrice()).multiply(BigDecimal.valueOf(qty));
        }
        String reference = replacementInvoiceReference(returnNumber);
        Optional<String> existingId = findExistingInvoiceId(reference);
        String invoiceId = existingId.orElse(null);
        BigDecimal balance = amount;
        if (invoiceId == null) {
            try {
                Map<String, Object> created = booksClient.createInvoice(
                    buildInvoicePayload(order, original, returnNumber, reference, replacementSku, replacementSize, replacementColor, amount, qty)
                );
                Map<String, Object> invoice = invoiceBody(created);
                invoiceId = stringValue(invoice.get("invoice_id"));
                balance = positiveOr(toBigDecimal(invoice.get("balance")), toBigDecimal(invoice.get("total")), amount);
            } catch (RuntimeException e) {
                Optional<String> recovered = findExistingInvoiceId(reference);
                if (recovered.isEmpty()) {
                    throw e;
                }
                LOG.info("Zoho replacement invoice create failed for {}; reusing existing {}", returnNumber, recovered.orElseThrow());
                invoiceId = recovered.orElseThrow();
                Map<String, Object> loaded = invoiceBody(booksClient.getInvoice(invoiceId));
                balance = positiveOr(toBigDecimal(loaded.get("balance")), toBigDecimal(loaded.get("total")), amount);
            }
        } else {
            Map<String, Object> loaded = invoiceBody(booksClient.getInvoice(invoiceId));
            balance = positiveOr(toBigDecimal(loaded.get("balance")), toBigDecimal(loaded.get("total")), amount);
            if (balance.signum() <= 0) {
                return Optional.of(invoiceId);
            }
        }
        if (StringUtils.isBlank(invoiceId)) {
            return Optional.empty();
        }
        applyCreditNotes(invoiceId, balance, creditNoteIds);
        return Optional.of(invoiceId);
    }

    private Map<String, Object> buildInvoicePayload(
        CustomerOrder order,
        OrderItem original,
        String returnNumber,
        String reference,
        String replacementSku,
        String replacementSize,
        String replacementColor,
        BigDecimal amount,
        int qty
    ) {
        String sourceInvoiceId = StringUtils.trimToNull(order.getZohoInvoiceId());
        Map<String, Object> sourceInvoice = loadInvoice(sourceInvoiceId);
        String customerId = stringValue(sourceInvoice.get("customer_id"));
        if (StringUtils.isBlank(customerId)) {
            customerId = orderSupport.resolveZohoContact(order);
        }
        OrderItem probe = new OrderItem();
        probe.setProductId(original.getProductId());
        probe.setProductName(original.getProductName());
        probe.setVariantSku(StringUtils.firstNonBlank(replacementSku, original.getVariantSku()));
        probe.setItemSize(StringUtils.firstNonBlank(replacementSize, original.getItemSize()));
        probe.setColor(StringUtils.firstNonBlank(replacementColor, original.getColor()));
        probe.setPrice(amount.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP));
        String zohoItemId = orderSupport
            .resolveZohoItemId(probe)
            .orElseThrow(() -> new IllegalStateException("Zoho item is required for exchange replacement " + original.getProductName()));

        Map<String, Object> line = new LinkedHashMap<>();
        line.put("item_id", zohoItemId);
        line.put(
            "name",
            ZohoSyncService.variantDisplayName(original.getProductName(), probe.getItemSize(), probe.getColor())
        );
        line.put("rate", probe.getPrice());
        line.put("quantity", qty);
        if (StringUtils.isNotBlank(probe.getVariantSku())) {
            line.put("description", "SKU: " + probe.getVariantSku());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_id", customerId);
        payload.put("reference_number", reference);
        payload.put("date", LocalDate.now(INDIA).toString());
        payload.put("is_inclusive_tax", sourceInvoice.get("is_inclusive_tax") != null ? sourceInvoice.get("is_inclusive_tax") : Boolean.TRUE);
        payload.put("line_items", List.of(line));
        payload.put(
            "notes",
            "Exchange replacement for order " +
            order.getOrderNumber() +
            " (" +
            returnNumber +
            "). Credit notes on this order are applied to this invoice."
        );
        return payload;
    }

    private void applyCreditNotes(String invoiceId, BigDecimal startingBalance, List<String> creditNoteIds) {
        BigDecimal remaining = money(startingBalance);
        if (remaining.signum() <= 0) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String rawId : creditNoteIds == null ? List.<String>of() : creditNoteIds) {
            String creditNoteId = StringUtils.trimToNull(rawId);
            if (creditNoteId == null || !seen.add(creditNoteId) || remaining.signum() <= 0) {
                continue;
            }
            BigDecimal available = creditNoteBalance(creditNoteId);
            if (available.signum() <= 0) {
                continue;
            }
            BigDecimal applied = available.min(remaining).setScale(2, RoundingMode.HALF_UP);
            Map<String, Object> invoiceApplication = new LinkedHashMap<>();
            invoiceApplication.put("invoice_id", invoiceId);
            invoiceApplication.put("amount_applied", applied);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("invoices", List.of(invoiceApplication));
            try {
                booksClient.applyCreditNoteToInvoices(creditNoteId, payload);
                remaining = remaining.subtract(applied);
                LOG.info("Applied Zoho credit note {} ({}) to replacement invoice {}", creditNoteId, applied, invoiceId);
            } catch (RuntimeException e) {
                LOG.warn("Could not apply Zoho credit note {} to invoice {}: {}", creditNoteId, invoiceId, e.getMessage());
            }
        }
    }

    private BigDecimal creditNoteBalance(String creditNoteId) {
        try {
            Map<String, Object> creditNote = invoiceBody(booksClient.getCreditNote(creditNoteId));
            String status = StringUtils.lowerCase(StringUtils.trimToEmpty(stringValue(creditNote.get("status"))));
            if (Set.of("void", "voided").contains(status)) {
                return BigDecimal.ZERO;
            }
            if (creditNote.get("balance") != null) {
                return money(toBigDecimal(creditNote.get("balance")));
            }
            return money(toBigDecimal(creditNote.get("total")));
        } catch (RuntimeException e) {
            LOG.warn("Could not read Zoho credit note {}: {}", creditNoteId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private Optional<String> findExistingInvoiceId(String reference) {
        try {
            Map<String, Object> existing = booksClient.findInvoiceByReferenceNumber(reference);
            if (existing == null || existing.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(StringUtils.trimToNull(stringValue(existing.get("invoice_id"))));
        } catch (RuntimeException e) {
            LOG.warn("Could not look up Zoho invoice by reference {}: {}", reference, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadInvoice(String invoiceId) {
        if (StringUtils.isBlank(invoiceId)) {
            return Map.of();
        }
        try {
            return invoiceBody(booksClient.getInvoice(invoiceId));
        } catch (RuntimeException e) {
            LOG.warn("Could not load Zoho invoice {} for exchange replacement: {}", invoiceId, e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invoiceBody(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return Map.of();
        }
        Object nested = response.get("invoice");
        if (nested instanceof Map<?, ?> invoice) {
            return (Map<String, Object>) invoice;
        }
        Object creditNote = response.get("creditnote");
        if (creditNote instanceof Map<?, ?> note) {
            return (Map<String, Object>) note;
        }
        return response;
    }

    private static BigDecimal positiveOr(BigDecimal first, BigDecimal second, BigDecimal fallback) {
        if (first != null && first.signum() > 0) {
            return first;
        }
        if (second != null && second.signum() > 0) {
            return second;
        }
        return money(fallback);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(text);
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
