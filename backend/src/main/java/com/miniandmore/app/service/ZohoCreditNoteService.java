package com.miniandmore.app.service;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

@Service
@Transactional
public class ZohoCreditNoteService {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoCreditNoteService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final String PREPAID_CANCELLATION_CREDIT_NOTE_NOTES = "Prepaid order cancelled before dispatch";

    private final ZohoBooksClient booksClient;
    private final ObjectMapper objectMapper;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ZohoBooksAccountResolver accountResolver;
    private final ZohoCreditNoteOrderSupport orderSupport;
    private final ZohoReturnCreditNotePersistence returnCreditNotePersistence;

    public ZohoCreditNoteService(
        ZohoBooksClient booksClient,
        ObjectMapper objectMapper,
        CustomerOrderRepository customerOrderRepository,
        OrderItemRepository orderItemRepository,
        ZohoBooksAccountResolver accountResolver,
        @Lazy ZohoCreditNoteOrderSupport orderSupport
    ) {
        this(booksClient, objectMapper, customerOrderRepository, orderItemRepository, accountResolver, orderSupport, null);
    }

    @Autowired
    public ZohoCreditNoteService(
        ZohoBooksClient booksClient,
        ObjectMapper objectMapper,
        CustomerOrderRepository customerOrderRepository,
        OrderItemRepository orderItemRepository,
        ZohoBooksAccountResolver accountResolver,
        @Lazy ZohoCreditNoteOrderSupport orderSupport,
        ZohoReturnCreditNotePersistence returnCreditNotePersistence
    ) {
        this.booksClient = booksClient;
        this.objectMapper = objectMapper;
        this.customerOrderRepository = customerOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.accountResolver = accountResolver;
        this.orderSupport = orderSupport;
        this.returnCreditNotePersistence = returnCreditNotePersistence;
    }

    public static String cancellationCreditNoteReference(String orderNumber) {
        return "CNCL-" + StringUtils.defaultString(orderNumber);
    }

    public void createPrepaidCancellationCreditNote(CustomerOrder order, List<String> failures) {
        if (StringUtils.isNotBlank(order.getZohoCreditNoteId())) {
            LOG.debug(
                "Zoho credit note {} already exists for cancelled prepaid order {}",
                order.getZohoCreditNoteId(),
                order.getOrderNumber()
            );
            return;
        }
        String referenceNumber = cancellationCreditNoteReference(order.getOrderNumber());
        Optional<String> existingCreditNoteId = findExistingCreditNoteIdByReference(referenceNumber);
        if (existingCreditNoteId.isPresent()) {
            order.setZohoCreditNoteId(existingCreditNoteId.orElseThrow());
            LOG.info(
                "Reusing Zoho credit note {} for cancelled prepaid order {} (reference {})",
                order.getZohoCreditNoteId(),
                order.getOrderNumber(),
                referenceNumber
            );
            return;
        }
        try {
            Optional<String> creditNoteId = createCreditNoteInternal(
                order.getId(),
                referenceNumber,
                null,
                true
            );
            if (creditNoteId.isPresent()) {
                order.setZohoCreditNoteId(creditNoteId.orElseThrow());
            } else {
                failures.add("credit note: Zoho returned no credit note id");
            }
        } catch (RuntimeException e) {
            LOG.warn(
                "Could not create Zoho credit note for cancelled prepaid order {}: {}",
                order.getOrderNumber(),
                e.getMessage()
            );
            failures.add("credit note: " + exceptionMessage(e));
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<String> createCreditNote(Long orderId, String returnNumber) {
        return createCreditNote(orderId, returnNumber, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<String> createCreditNote(Long orderId, String returnNumber, Map<Long, Integer> returnedQtyByOrderItemId) {
        return createCreditNote(orderId, returnNumber, returnedQtyByOrderItemId, true);
    }

    /**
     * @param linkToSourceInvoice when false, the credit note stays open so a later replacement
     * invoice can apply it. Refunds and cancellations keep the link to the original invoice.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<String> createCreditNote(
        Long orderId,
        String returnNumber,
        Map<Long, Integer> returnedQtyByOrderItemId,
        boolean linkToSourceInvoice
    ) {
        return createCreditNoteInternal(orderId, returnNumber, returnedQtyByOrderItemId, false, linkToSourceInvoice);
    }

    private Optional<String> createCreditNoteInternal(
        Long orderId,
        String returnNumber,
        Map<Long, Integer> returnedQtyByOrderItemId,
        boolean includeInvoiceCharges
    ) {
        return createCreditNoteInternal(orderId, returnNumber, returnedQtyByOrderItemId, includeInvoiceCharges, true);
    }

    private Optional<String> createCreditNoteInternal(
        Long orderId,
        String returnNumber,
        Map<Long, Integer> returnedQtyByOrderItemId,
        boolean includeInvoiceCharges,
        boolean linkToSourceInvoice
    ) {
        Optional<CustomerOrder> orderOpt = customerOrderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }
        CustomerOrder order = orderOpt.orElseThrow();
        if (StringUtils.isNotBlank(returnNumber)) {
            Optional<String> existingCreditNoteId = findExistingCreditNoteIdByReference(returnNumber);
            if (existingCreditNoteId.isPresent()) {
                LOG.info(
                    "Reusing Zoho credit note {} for {} (reference {})",
                    existingCreditNoteId.orElseThrow(),
                    order.getOrderNumber(),
                    returnNumber
                );
                return existingCreditNoteId;
            }
        }
        String invoiceId = StringUtils.trimToNull(order.getZohoInvoiceId());
        Map<String, Object> invoice = loadInvoice(invoiceId);
        if (StringUtils.isNotBlank(invoiceId) && invoice.isEmpty()) {
            throw new IllegalStateException("Could not load Zoho invoice " + invoiceId + " for credit note");
        }
        String customerId = stringValue(invoice.get("customer_id"));
        if (StringUtils.isBlank(customerId)) {
            customerId = orderSupport.resolveZohoContact(order);
        }
        Map<Long, Integer> returnedQty = returnedQtyByOrderItemId == null
            ? quantitiesForAllOrderItems(order)
            : returnedQtyByOrderItemId;
        List<Map<String, Object>> lineItems = buildCreditNoteLineItems(order, invoice, returnedQty);
        if (lineItems.isEmpty()) {
            throw new IllegalStateException("Zoho credit note has no returned line items for " + returnNumber);
        }
        if (!linkToSourceInvoice) {
            lineItems.forEach(line -> line.remove("invoice_item_id"));
        }
        Map<String, Object> payload = buildCreditNotePayload(
            customerId,
            returnNumber,
            linkToSourceInvoice ? invoiceId : null,
            invoice,
            lineItems
        );
        if (includeInvoiceCharges) {
            copyInvoiceChargesToCreditNote(invoice, order, payload);
            payload.put("notes", PREPAID_CANCELLATION_CREDIT_NOTE_NOTES);
        }
        Map<String, Object> response;
        try {
            response = booksClient.createCreditNote(payload);
        } catch (RestClientResponseException e) {
            if (!isInvalidCreditNoteValue(e) || StringUtils.isBlank(invoiceId)) {
                return recoverExistingCreditNoteOrThrow(returnNumber, e);
            }
            LOG.warn(
                "Zoho rejected invoice-linked credit note for {} ({}); retrying without invoice linkage",
                returnNumber,
                e.getMessage()
            );
            try {
                response = booksClient.createCreditNote(copyPayloadWithoutInvoiceLinkage(payload));
            } catch (RuntimeException retryEx) {
                return recoverExistingCreditNoteOrThrow(returnNumber, retryEx);
            }
        } catch (RuntimeException e) {
            return recoverExistingCreditNoteOrThrow(returnNumber, e);
        }
        Optional<String> created = finalizeCreatedCreditNote(response, includeInvoiceCharges);
        return created;
    }

    private Optional<String> recoverExistingCreditNoteOrThrow(String returnNumber, RuntimeException error) {
        Optional<String> existing = findExistingCreditNoteIdByReference(returnNumber);
        if (existing.isPresent()) {
            LOG.info(
                "Zoho credit note create failed for {}; reusing existing credit note {}",
                returnNumber,
                existing.orElseThrow()
            );
            return existing;
        }
        throw error;
    }

    private Optional<String> findExistingCreditNoteIdByReference(String referenceNumber) {
        try {
            Map<String, Object> existing = booksClient.findCreditNoteByReferenceNumber(referenceNumber);
            if (existing == null || existing.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(StringUtils.trimToNull(stringValue(existing.get("creditnote_id"))));
        } catch (RuntimeException e) {
            LOG.warn("Could not look up Zoho credit note by reference {}: {}", referenceNumber, e.getMessage());
            return Optional.empty();
        }
    }

    private void copyInvoiceChargesToCreditNote(Map<String, Object> invoice, CustomerOrder order, Map<String, Object> payload) {
        Object shipping = invoice.get("shipping_charge");
        if (isBlankNumber(shipping) || toBigDecimal(shipping).compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal orderShipping = moneyOrZero(order.getShipping());
            shipping = orderShipping.compareTo(BigDecimal.ZERO) > 0 ? orderShipping : null;
        }
        if (!isBlankNumber(shipping) && toBigDecimal(shipping).compareTo(BigDecimal.ZERO) > 0) {
            payload.put("shipping_charge", shipping);
        }
        Object discount = invoice.get("discount");
        if (isBlankNumber(discount) || toBigDecimal(discount).compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal orderDiscount = moneyOrZero(order.getDiscount());
            discount = orderDiscount.compareTo(BigDecimal.ZERO) > 0 ? orderDiscount : null;
        }
        if (!isBlankNumber(discount) && toBigDecimal(discount).compareTo(BigDecimal.ZERO) > 0) {
            payload.put("discount", discount);
            payload.put(
                "discount_type",
                StringUtils.defaultIfBlank(stringValue(invoice.get("discount_type")), "entity_level")
            );
            Object discountBeforeTax = invoice.get("is_discount_before_tax");
            if (discountBeforeTax != null) {
                payload.put("is_discount_before_tax", discountBeforeTax);
            } else {
                payload.put("is_discount_before_tax", true);
            }
        }
    }

    private Map<String, Object> buildCreditNotePayload(
        String customerId,
        String returnNumber,
        String invoiceId,
        Map<String, Object> invoice,
        List<Map<String, Object>> lineItems
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_id", customerId);
        payload.put("reference_number", returnNumber);
        String invoiceDate = stringValue(invoice.get("date"));
        payload.put("date", StringUtils.defaultIfBlank(invoiceDate, LocalDate.now(INDIA).toString()));
        if (invoice.get("is_inclusive_tax") != null) {
            payload.put("is_inclusive_tax", invoice.get("is_inclusive_tax"));
        }
        if (StringUtils.isNotBlank(invoiceId)) {
            payload.put("invoice_id", invoiceId);
        }
        payload.put("line_items", lineItems);
        // Zoho copies invoice shipping unless the payload states a charge. Default 0 so
        // returns/exchanges do not credit delivery; cancellation overwrites when shipping > 0.
        payload.put("shipping_charge", BigDecimal.ZERO);
        return payload;
    }

    private List<Map<String, Object>> buildCreditNoteLineItems(
        CustomerOrder order,
        Map<String, Object> invoice,
        Map<Long, Integer> returnedQtyByOrderItemId
    ) {
        Map<String, java.util.Deque<Map<String, Object>>> invoiceLinesByItemId = indexInvoiceLines(invoice);
        List<Map<String, Object>> lineItems = new ArrayList<>();
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        if (items.isEmpty()) {
            items = new ArrayList<>(order.getItemses());
        }
        for (OrderItem item : items) {
            if (item.getId() == null) {
                continue;
            }
            Optional<String> zohoItemId = orderSupport.resolveZohoItemId(item);
            Map<String, Object> invoiceLine = zohoItemId.map(id -> pollInvoiceLine(invoiceLinesByItemId, id)).orElse(Map.of());
            Integer returnedQty = returnedQtyByOrderItemId.get(item.getId());
            if (returnedQty == null || returnedQty <= 0) {
                continue;
            }
            String linkedItemId = zohoItemId.orElseThrow(
                () -> new IllegalStateException("Zoho item is required for order line " + item.getProductName())
            );
            lineItems.add(creditNoteLine(item, returnedQty, linkedItemId, invoiceLine));
        }
        return lineItems;
    }

    private Map<Long, Integer> quantitiesForAllOrderItems(CustomerOrder order) {
        Map<Long, Integer> qtyByItemId = new LinkedHashMap<>();
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        if (items.isEmpty()) {
            items = new ArrayList<>(order.getItemses());
        }
        for (OrderItem item : items) {
            if (item.getId() == null) {
                continue;
            }
            int quantity = Optional.ofNullable(item.getQty()).orElse(0);
            if (quantity > 0) {
                qtyByItemId.merge(item.getId(), quantity, Integer::sum);
            }
        }
        return qtyByItemId;
    }

    private Map<String, Object> creditNoteLine(
        OrderItem item,
        int quantity,
        String zohoItemId,
        Map<String, Object> invoiceLine
    ) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("item_id", firstNonBlank(stringValue(invoiceLine.get("item_id")), zohoItemId));
        line.put("quantity", quantity);
        Object invoiceRate = invoiceLine.get("rate");
        line.put("rate", isBlankNumber(invoiceRate) ? item.getPrice() : invoiceRate);
        String invoiceItemId = firstNonBlank(
            stringValue(invoiceLine.get("line_item_id")),
            stringValue(invoiceLine.get("invoice_item_id"))
        );
        if (StringUtils.isNotBlank(invoiceItemId)) {
            line.put("invoice_item_id", invoiceItemId);
        }
        copyIfPresent(invoiceLine, line, "tax_id");
        copyIfPresent(invoiceLine, line, "warehouse_id");
        copyIfPresent(invoiceLine, line, "location_id");
        return line;
    }

    private Optional<String> finalizeCreatedCreditNote(Map<String, Object> response, boolean failOnOpenFailure) {
        @SuppressWarnings("unchecked")
        Map<String, Object> creditNote = (Map<String, Object>) response.getOrDefault("creditnote", response);
        String creditNoteId = stringValue(creditNote.get("creditnote_id"));
        if (StringUtils.isNotBlank(creditNoteId) && accountResolver.isStockAdjustmentEnabled()) {
            try {
                booksClient.markCreditNoteAsOpen(creditNoteId);
                LOG.debug("Opened Zoho credit note {} for inventory return", creditNoteId);
            } catch (RuntimeException e) {
                if (failOnOpenFailure) {
                    throw new IllegalStateException(
                        "Could not open Zoho credit note " + creditNoteId + ": " + e.getMessage(),
                        e
                    );
                }
                LOG.warn("Could not open Zoho credit note {} (stock may not increment): {}", creditNoteId, e.getMessage());
            }
        }
        return Optional.ofNullable(creditNoteId).filter(StringUtils::isNotBlank);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadInvoice(String invoiceId) {
        if (StringUtils.isBlank(invoiceId)) {
            return Map.of();
        }
        try {
            Map<String, Object> response = booksClient.getInvoice(invoiceId);
            Map<String, Object> invoice = (Map<String, Object>) response.getOrDefault("invoice", response);
            return invoice == null ? Map.of() : invoice;
        } catch (RuntimeException e) {
            LOG.warn("Could not load Zoho invoice {} for credit note: {}", invoiceId, e.getMessage());
            return Map.of();
        }
    }

    private static Map<String, Object> pollInvoiceLine(
        Map<String, java.util.Deque<Map<String, Object>>> invoiceLinesByItemId,
        String zohoItemId
    ) {
        java.util.Deque<Map<String, Object>> queue = invoiceLinesByItemId.get(zohoItemId);
        if (queue == null || queue.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> invoiceLine = queue.pollFirst();
        return invoiceLine == null ? Map.of() : invoiceLine;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyPayloadWithoutInvoiceLinkage(Map<String, Object> payload) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.remove("invoice_id");
        Object rawLines = payload.get("line_items");
        if (!(rawLines instanceof List<?> lines)) {
            return copy;
        }
        List<Map<String, Object>> copiedLines = new ArrayList<>();
        for (Object lineObj : lines) {
            if (lineObj instanceof Map<?, ?> line) {
                Map<String, Object> copiedLine = new LinkedHashMap<>((Map<String, Object>) line);
                copiedLine.remove("invoice_item_id");
                copiedLines.add(copiedLine);
            }
        }
        copy.put("line_items", copiedLines);
        return copy;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null && StringUtils.isNotBlank(stringValue(value))) {
            target.put(key, value);
        }
    }

    private boolean isInvalidCreditNoteValue(RestClientResponseException exception) {
        if (exception.getStatusCode().value() != 400) {
            return false;
        }
        String body = StringUtils.trimToEmpty(exception.getResponseBodyAsString());
        if (body.isEmpty()) {
            return false;
        }
        try {
            return objectMapper.readTree(body).path("code").asInt(Integer.MIN_VALUE) == 6;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, java.util.Deque<Map<String, Object>>> indexInvoiceLines(Map<String, Object> invoice) {
        Map<String, java.util.Deque<Map<String, Object>>> indexed = new LinkedHashMap<>();
        if (invoice == null || invoice.isEmpty()) {
            return indexed;
        }
        Object rawLines = invoice.get("line_items");
        if (!(rawLines instanceof List<?> lines)) {
            return indexed;
        }
        for (Object lineObj : lines) {
            if (!(lineObj instanceof Map<?, ?> line)) {
                continue;
            }
            String itemId = stringValue(line.get("item_id"));
            if (StringUtils.isNotBlank(itemId)) {
                indexed.computeIfAbsent(itemId, ignored -> new java.util.ArrayDeque<>()).addLast((Map<String, Object>) line);
            }
        }
        return indexed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String exceptionMessage(Throwable e) {
        if (e instanceof RestClientResponseException response) {
            String body = response.getResponseBodyAsString();
            if (StringUtils.isNotBlank(body)) {
                return response.getStatusCode() + " " + StringUtils.abbreviate(body, 500);
            }
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean isBlankNumber(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number) {
            return false;
        }
        return value.toString().trim().isEmpty();
    }

    private static BigDecimal moneyOrZero(BigDecimal value) {
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
}
