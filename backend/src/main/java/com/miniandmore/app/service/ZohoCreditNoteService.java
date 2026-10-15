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
import java.util.Set;
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
    /**
     * Kept for Spring wiring. Do not persist from {@code createCreditNote} (NOT_SUPPORTED):
     * a nested REQUIRES_NEW update deadlocks allocate/convert, which hold FOR UPDATE on
     * the same row. Callers save the credit-note id on the entity instead; Zoho reuses
     * by return-number reference if that save is lost.
     */
    @SuppressWarnings("unused")
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

    /**
     * Applies remaining credit-note balance to an existing invoice. Used when convert-to-refund
     * follows a cheaper-replacement Razorpay settle that already created an unlinked note.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void applyCreditNoteToInvoice(String creditNoteId, String invoiceId) {
        String noteId = StringUtils.trimToNull(creditNoteId);
        String targetInvoiceId = StringUtils.trimToNull(invoiceId);
        if (noteId == null || targetInvoiceId == null) {
            return;
        }
        BigDecimal available = openCreditNoteBalance(noteId);
        if (available.signum() <= 0) {
            return;
        }
        applyCreditNoteAmount(noteId, targetInvoiceId, available);
    }

    /**
     * Attaches an existing unlinked credit note to the order's source invoice. If that invoice has
     * no outstanding (typical prepaid original), voids the unused note and creates a linked replacement.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<String> attachCreditNoteToSourceInvoice(
        Long orderId,
        String returnNumber,
        String creditNoteId,
        Map<Long, Integer> returnedQtyByOrderItemId
    ) {
        String noteId = StringUtils.trimToNull(creditNoteId);
        if (noteId == null || orderId == null) {
            return Optional.ofNullable(noteId);
        }
        Optional<CustomerOrder> orderOpt = customerOrderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return Optional.of(noteId);
        }
        CustomerOrder order = orderOpt.orElseThrow();
        String invoiceId = StringUtils.trimToNull(order.getZohoInvoiceId());
        BigDecimal available = openCreditNoteBalance(noteId);
        if (available.signum() <= 0) {
            return Optional.of(noteId);
        }
        BigDecimal outstanding = invoiceOutstanding(invoiceId);
        if (outstanding.signum() > 0 && invoiceId != null) {
            try {
                applyCreditNoteAmount(noteId, invoiceId, available.min(outstanding));
                return Optional.of(noteId);
            } catch (RuntimeException e) {
                LOG.warn(
                    "Zoho apply credit note {} to invoice {} failed; replacing with a linked note: {}",
                    noteId,
                    invoiceId,
                    e.getMessage()
                );
            }
        }
        return replaceWithLinkedCreditNote(noteId, orderId, returnNumber, returnedQtyByOrderItemId);
    }

    /**
     * Refunds leftover credit-note balance after a cheaper replacement invoice has consumed what it
     * can. Razorpay (or a manual difference refund) already paid that leftover in cash.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refundOpenCreditNoteBalance(String creditNoteId, String referenceNumber) {
        String noteId = StringUtils.trimToNull(creditNoteId);
        if (noteId == null) {
            return;
        }
        BigDecimal available = openCreditNoteBalance(noteId);
        if (available.signum() <= 0) {
            return;
        }
        String accountId = resolveRefundFromAccountId();
        if (accountId == null) {
            LOG.warn("Skipping leftover Zoho credit-note refund for {}: no cash/bank account in the org", noteId);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", LocalDate.now(INDIA).toString());
        payload.put("refund_mode", "others");
        payload.put("amount", available);
        payload.put("from_account_id", accountId);
        payload.put(
            "reference_number",
            StringUtils.left(StringUtils.defaultIfBlank(StringUtils.trimToNull(referenceNumber), noteId), 50)
        );
        booksClient.refundCreditNote(noteId, payload);
        LOG.info("Refunded leftover Zoho credit note {} ({})", noteId, available);
    }

    private void applyCreditNoteAmount(String noteId, String invoiceId, BigDecimal amount) {
        BigDecimal applied = moneyOrZero(amount);
        if (applied.signum() <= 0) {
            return;
        }
        Map<String, Object> invoiceApplication = new LinkedHashMap<>();
        invoiceApplication.put("invoice_id", invoiceId);
        invoiceApplication.put("amount_applied", applied);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invoices", List.of(invoiceApplication));
        booksClient.applyCreditNoteToInvoices(noteId, payload);
        LOG.info("Applied Zoho credit note {} ({}) to invoice {}", noteId, applied, invoiceId);
    }

    private Optional<String> replaceWithLinkedCreditNote(
        String noteId,
        Long orderId,
        String returnNumber,
        Map<Long, Integer> returnedQtyByOrderItemId
    ) {
        try {
            booksClient.voidCreditNote(noteId);
        } catch (RuntimeException e) {
            LOG.warn("Could not void unlinked Zoho credit note {}: {}", noteId, e.getMessage());
            return Optional.of(noteId);
        }
        Optional<String> created = findExistingCreditNoteIdByReference(returnNumber);
        if (created.isPresent()) {
            return created;
        }
        try {
            created = createCreditNote(orderId, returnNumber, returnedQtyByOrderItemId, true);
            if (created.isPresent()) {
                return created;
            }
        } catch (RuntimeException e) {
            LOG.warn("Could not recreate linked Zoho credit note for {}: {}", returnNumber, e.getMessage());
        }
        LOG.warn(
            "Zoho credit note {} was voided for {} but no linked replacement was created; clear local id and retry",
            noteId,
            returnNumber
        );
        return Optional.empty();
    }

    private BigDecimal invoiceOutstanding(String invoiceId) {
        Map<String, Object> invoice = loadInvoice(invoiceId);
        if (invoice.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (invoice.get("balance") != null) {
            return moneyOrZero(toBigDecimal(invoice.get("balance")));
        }
        return BigDecimal.ZERO;
    }

    private String resolveRefundFromAccountId() {
        try {
            for (Map<String, Object> account : booksClient.listBankAccounts()) {
                String id = StringUtils.trimToNull(stringValue(account.get("account_id")));
                if (id != null) {
                    return id;
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Could not list Zoho bank accounts for credit-note refund: {}", e.getMessage());
        }
        try {
            String fallback = null;
            for (Map<String, Object> account : booksClient.listAssetAccounts()) {
                String id = StringUtils.trimToNull(stringValue(account.get("account_id")));
                if (id == null) {
                    continue;
                }
                if (fallback == null) {
                    fallback = id;
                }
                String name = StringUtils.lowerCase(
                    StringUtils.defaultString(stringValue(account.get("account_name")), stringValue(account.get("name")))
                );
                if (name.contains("bank") || name.contains("cash") || name.contains("undeposited") || name.contains("petty")) {
                    return id;
                }
            }
            return fallback;
        } catch (RuntimeException e) {
            LOG.warn("Could not list Zoho asset accounts for credit-note refund: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal openCreditNoteBalance(String creditNoteId) {
        try {
            Map<String, Object> response = booksClient.getCreditNote(creditNoteId);
            Object nested = response == null ? null : response.get("creditnote");
            @SuppressWarnings("unchecked")
            Map<String, Object> creditNote = nested instanceof Map<?, ?> note ? (Map<String, Object>) note : response;
            if (creditNote == null || creditNote.isEmpty()) {
                return BigDecimal.ZERO;
            }
            String status = StringUtils.lowerCase(StringUtils.trimToEmpty(stringValue(creditNote.get("status"))));
            if (Set.of("void", "voided").contains(status)) {
                return BigDecimal.ZERO;
            }
            if (creditNote.get("balance") != null) {
                return moneyOrZero(toBigDecimal(creditNote.get("balance")));
            }
            return moneyOrZero(toBigDecimal(creditNote.get("total")));
        } catch (RuntimeException e) {
            LOG.warn("Could not read Zoho credit note {}: {}", creditNoteId, e.getMessage());
            return BigDecimal.ZERO;
        }
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
            lineItems.forEach(line -> {
                line.remove("invoice_item_id");
                line.remove("invoice_id");
            });
        }
        Map<String, Object> payload = buildCreditNotePayload(
            customerId,
            returnNumber,
            linkToSourceInvoice ? invoiceId : null,
            invoice,
            order,
            lineItems
        );
        if (includeInvoiceCharges) {
            copyInvoiceChargesToCreditNote(invoice, order, payload);
            payload.put("notes", PREPAID_CANCELLATION_CREDIT_NOTE_NOTES);
        }
        return createCreditNoteWithSelfHealing(returnNumber, payload, invoice, order, includeInvoiceCharges);
    }

    /**
     * Same shape as sales-order self-heal on this org: keep as much of the document as Zoho
     * accepts, and only strip the field that the previous attempt rejected.
     */
    private Optional<String> createCreditNoteWithSelfHealing(
        String returnNumber,
        Map<String, Object> payload,
        Map<String, Object> invoice,
        CustomerOrder order,
        boolean failOnOpenFailure
    ) {
        List<Map<String, Object>> attempts = creditNoteCreateAttempts(payload, invoice, order);
        RuntimeException lastError = null;
        for (int index = 0; index < attempts.size(); index++) {
            try {
                return finalizeCreatedCreditNote(booksClient.createCreditNote(attempts.get(index)), failOnOpenFailure);
            } catch (RestClientResponseException e) {
                Optional<String> existing = findExistingCreditNoteIdByReference(returnNumber);
                if (existing.isPresent()) {
                    LOG.info(
                        "Zoho credit note create failed for {}; reusing existing credit note {}",
                        returnNumber,
                        existing.orElseThrow()
                    );
                    return existing;
                }
                lastError = e;
                if (index + 1 >= attempts.size() || !isRetryableCreditNoteCreate(e, attempts.get(index))) {
                    break;
                }
                LOG.warn(
                    "Zoho rejected credit note for {} ({}); retrying with a narrower invoice association",
                    returnNumber,
                    e.getMessage()
                );
            } catch (RuntimeException e) {
                return recoverExistingCreditNoteOrThrow(returnNumber, e);
            }
        }
        if (lastError != null) {
            return recoverExistingCreditNoteOrThrow(returnNumber, lastError);
        }
        return Optional.empty();
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
        CustomerOrder order,
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
        applyGstInvoiceAssociation(payload, invoice, order, StringUtils.isNotBlank(invoiceId));
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

    private List<Map<String, Object>> creditNoteCreateAttempts(
        Map<String, Object> payload,
        Map<String, Object> invoice,
        CustomerOrder order
    ) {
        List<Map<String, Object>> attempts = new ArrayList<>();
        addDistinctCreditNoteAttempt(attempts, payload);
        addDistinctCreditNoteAttempt(attempts, copyWithoutInvoiceItemIds(payload));
        addDistinctCreditNoteAttempt(attempts, copyPayloadWithoutInvoiceLineLinkage(payload, invoice, order));
        Map<String, Object> numberOnly = copyPayloadWithoutInvoiceLineLinkage(payload, invoice, order);
        removeGstInvoiceTypeFields(numberOnly);
        if (StringUtils.isNotBlank(stringValue(numberOnly.get("invoice_number")))) {
            addDistinctCreditNoteAttempt(attempts, numberOnly);
        }
        return attempts;
    }

    private static void addDistinctCreditNoteAttempt(List<Map<String, Object>> attempts, Map<String, Object> payload) {
        String signature = creditNoteAttemptSignature(payload);
        for (Map<String, Object> existing : attempts) {
            if (signature.equals(creditNoteAttemptSignature(existing))) {
                return;
            }
        }
        attempts.add(payload);
    }

    private static String creditNoteAttemptSignature(Map<String, Object> payload) {
        return (payload.containsKey("invoice_id") ? "id" : "-")
            + "|"
            + (payload.containsKey("invoice_number") ? "num" : "-")
            + "|"
            + (payload.containsKey("invoice_type") || payload.containsKey("reference_invoice_type") ? "type" : "-")
            + "|"
            + (creditNoteLinesHaveInvoiceItemId(payload) ? "line" : "-");
    }

    private static boolean creditNoteLinesHaveInvoiceItemId(Map<String, Object> payload) {
        Object rawLines = payload.get("line_items");
        if (!(rawLines instanceof List<?> lines)) {
            return false;
        }
        for (Object lineObj : lines) {
            if (lineObj instanceof Map<?, ?> line && line.get("invoice_item_id") != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keep the invoice id (uat3 linked create) but drop line-item ids that Zoho code 6 often rejects.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyWithoutInvoiceItemIds(Map<String, Object> payload) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        Object rawLines = payload.get("line_items");
        if (!(rawLines instanceof List<?> lines)) {
            return copy;
        }
        List<Map<String, Object>> copiedLines = new ArrayList<>();
        for (Object lineObj : lines) {
            if (lineObj instanceof Map<?, ?> line) {
                Map<String, Object> copiedLine = new LinkedHashMap<>((Map<String, Object>) line);
                copiedLine.remove("invoice_item_id");
                copiedLine.remove("invoice_id");
                copiedLines.add(copiedLine);
            }
        }
        copy.put("line_items", copiedLines);
        return copy;
    }

    /**
     * GST India still requires the original invoice number or an invoice type when the credit note
     * is not applied against the paid invoice.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> copyPayloadWithoutInvoiceLineLinkage(
        Map<String, Object> payload,
        Map<String, Object> invoice,
        CustomerOrder order
    ) {
        Map<String, Object> copy = copyWithoutInvoiceItemIds(payload);
        copy.remove("invoice_id");
        applyGstInvoiceAssociation(copy, invoice, order, false);
        return copy;
    }

    private void applyGstInvoiceAssociation(
        Map<String, Object> payload,
        Map<String, Object> invoice,
        CustomerOrder order,
        boolean linkedToInvoice
    ) {
        String invoiceNumber = firstNonBlank(
            stringValue(payload.get("invoice_number")),
            stringValue(invoice.get("invoice_number"))
        );
        if (invoiceNumber != null) {
            payload.put("invoice_number", invoiceNumber);
        }
        if (!linkedToInvoice || invoiceNumber == null) {
            putGstInvoiceTypeFields(payload, gstCreditNoteInvoiceType(invoice, order));
        }
    }

    private static void putGstInvoiceTypeFields(Map<String, Object> payload, String invoiceType) {
        if (StringUtils.isBlank(invoiceType)) {
            return;
        }
        payload.put("invoice_type", invoiceType);
        // India GST orgs often expect this alias on create/import (Books UI: "Reference invoice type").
        payload.put("reference_invoice_type", invoiceType);
    }

    private static void removeGstInvoiceTypeFields(Map<String, Object> payload) {
        payload.remove("invoice_type");
        payload.remove("reference_invoice_type");
    }

    private String gstCreditNoteInvoiceType(Map<String, Object> invoice, CustomerOrder order) {
        String declared = firstNonBlank(
            stringValue(invoice.get("reference_invoice_type")),
            stringValue(invoice.get("invoice_type"))
        );
        if (declared != null && !Set.of("invoice", "sales_invoice").contains(StringUtils.lowerCase(declared))) {
            return normalizeGstReferenceInvoiceType(declared);
        }
        String gst = StringUtils.lowerCase(StringUtils.trimToEmpty(stringValue(invoice.get("gst_treatment"))));
        BigDecimal total = moneyOrZero(toBigDecimal(invoice.get("total")));
        if (total.signum() <= 0 && order != null) {
            total = moneyOrZero(order.getTotal());
        }
        boolean b2cLarge = total.compareTo(new BigDecimal("100000.00")) > 0;
        return switch (gst) {
            case "business_gst" -> "registered";
            case "deemed_export" -> "deemed_export";
            case "overseas" -> "overseas";
            case "sez", "sez_developer_lut", "sez_with_lut", "business_sez" -> "sez";
            default -> b2cLarge ? "b2c_large" : "b2c_others";
        };
    }

    private static String normalizeGstReferenceInvoiceType(String declared) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(declared)).replace(' ', '_');
        return switch (normalized) {
            case "registered", "b2b" -> "registered";
            case "b2c_large", "b2cl" -> "b2c_large";
            case "b2c_others", "b2cs" -> "b2c_others";
            case "deemed_export" -> "deemed_export";
            case "overseas" -> "overseas";
            case "sez" -> "sez";
            default -> declared.trim();
        };
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null && StringUtils.isNotBlank(stringValue(value))) {
            target.put(key, value);
        }
    }

    private boolean isRetryableCreditNoteCreate(RestClientResponseException exception, Map<String, Object> payload) {
        if (isMissingGstInvoiceAssociation(exception) || isInvalidInvoiceTypeElement(exception)) {
            return true;
        }
        // Zoho code 6 is generic; only walk down the ladder while invoice line linkage may be the cause.
        return isInvalidCreditNoteValue(exception) && creditNoteLinesHaveInvoiceItemId(payload);
    }

    private boolean isInvalidCreditNoteValue(RestClientResponseException exception) {
        return zohoErrorCode(exception) == 6;
    }

    private boolean isMissingGstInvoiceAssociation(RestClientResponseException exception) {
        if (zohoErrorCode(exception) == 12069) {
            return true;
        }
        String body = StringUtils.lowerCase(StringUtils.defaultString(exception.getResponseBodyAsString()));
        return exception.getStatusCode().value() == 400 && body.contains("associated invoice number or invoice type");
    }

    private boolean isInvalidInvoiceTypeElement(RestClientResponseException exception) {
        if (exception.getStatusCode().value() != 400 || zohoErrorCode(exception) != 8) {
            return false;
        }
        String body = StringUtils.lowerCase(StringUtils.defaultString(exception.getResponseBodyAsString()));
        return body.contains("invoice_type") || body.contains("reference_invoice_type");
    }

    private int zohoErrorCode(RestClientResponseException exception) {
        if (exception.getStatusCode().value() != 400) {
            return Integer.MIN_VALUE;
        }
        String body = StringUtils.trimToEmpty(exception.getResponseBodyAsString());
        if (body.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        try {
            return objectMapper.readTree(body).path("code").asInt(Integer.MIN_VALUE);
        } catch (Exception ignored) {
            return Integer.MIN_VALUE;
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
