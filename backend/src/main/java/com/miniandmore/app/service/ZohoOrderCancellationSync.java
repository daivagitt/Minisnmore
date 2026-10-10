package com.miniandmore.app.service;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

@Service
@Transactional
public class ZohoOrderCancellationSync {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoOrderCancellationSync.class);

    private final CustomerOrderRepository customerOrderRepository;
    private final ZohoBooksClient booksClient;
    private final ZohoCreditNoteService creditNoteService;

    public ZohoOrderCancellationSync(
        CustomerOrderRepository customerOrderRepository,
        ZohoBooksClient booksClient,
        ZohoCreditNoteService creditNoteService
    ) {
        this.customerOrderRepository = customerOrderRepository;
        this.booksClient = booksClient;
        this.creditNoteService = creditNoteService;
    }

    public void cancelOrder(Long orderId) {
        customerOrderRepository
            .findById(orderId)
            .ifPresent(order -> {
                List<String> failures = new ArrayList<>();
                if (shouldCreatePrepaidCancellationCreditNote(order)) {
                    creditNoteService.createPrepaidCancellationCreditNote(order, failures);
                    persistCancellationSyncResult(order, failures);
                    return;
                }
                String invoiceId = StringUtils.trimToNull(order.getZohoInvoiceId());
                if (invoiceId != null) {
                    try {
                        voidInvoiceForCancellation(invoiceId);
                    } catch (RuntimeException e) {
                        LOG.warn("Could not void Zoho invoice {} for cancelled order {}: {}", invoiceId, order.getOrderNumber(), e.getMessage());
                        failures.add("invoice " + invoiceId + ": " + exceptionMessage(e));
                    }
                }
                String salesOrderId = StringUtils.trimToNull(order.getZohoSalesOrderId());
                if (salesOrderId != null && failures.isEmpty()) {
                    try {
                        booksClient.voidSalesOrder(salesOrderId, "Customer cancelled before dispatch");
                    } catch (RuntimeException e) {
                        if (e instanceof RestClientResponseException responseException && isAlreadyVoided(responseException)) {
                            LOG.debug("Zoho sales order {} was already void", salesOrderId);
                            persistCancellationSyncResult(order, List.of());
                            return;
                        }
                        LOG.warn(
                            "Could not void Zoho sales order {} for cancelled order {}: {}",
                            salesOrderId,
                            order.getOrderNumber(),
                            e.getMessage()
                        );
                        failures.add("sales order " + salesOrderId + ": " + exceptionMessage(e));
                    }
                }
                persistCancellationSyncResult(order, failures);
            });
    }

    private static boolean shouldCreatePrepaidCancellationCreditNote(CustomerOrder order) {
        return !ZohoSyncService.isCodOrder(order) && StringUtils.isNotBlank(order.getZohoInvoiceId());
    }

    private void persistCancellationSyncResult(CustomerOrder order, List<String> failures) {
        order.setZohoLastSyncedAt(Instant.now());
        if (failures.isEmpty()) {
            order.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
            order.setZohoSyncError(null);
        } else {
            order.setZohoSyncStatus(ZohoSyncStatus.FAILED);
            order.setZohoSyncError(StringUtils.abbreviate(String.join("; ", failures), 1000));
        }
        customerOrderRepository.save(order);
    }

    private void voidInvoiceForCancellation(String invoiceId) {
        try {
            booksClient.voidInvoice(invoiceId);
            return;
        } catch (RestClientResponseException e) {
            if (isAlreadyVoided(e)) {
                return;
            }
            if (!isPaidInvoiceVoidRejected(e)) {
                throw e;
            }
        }

        Map<String, Object> response = booksClient.listInvoicePayments(invoiceId);
        Object rawPayments = response.get("payments");
        if (!(rawPayments instanceof List<?> payments) || payments.isEmpty()) {
            throw new IllegalStateException("Zoho invoice is paid but no removable invoice payment was returned");
        }
        int removed = 0;
        for (Object rawPayment : payments) {
            if (!(rawPayment instanceof Map<?, ?> payment)) {
                continue;
            }
            String invoicePaymentId = StringUtils.trimToNull(stringValue(payment.get("invoice_payment_id")));
            if (invoicePaymentId == null) {
                continue;
            }
            booksClient.deleteInvoicePayment(invoiceId, invoicePaymentId);
            removed++;
        }
        if (removed == 0) {
            throw new IllegalStateException("Zoho invoice is paid but its invoice payment id is missing");
        }
        booksClient.voidInvoice(invoiceId);
    }

    private static boolean isPaidInvoiceVoidRejected(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        return exception.getStatusCode().value() == 400 && (body.contains("\"code\":4001") || body.contains("cannot void a paid invoice"));
    }

    private static boolean isAlreadyVoided(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        return exception.getStatusCode().value() == 400 && body.contains("already") && body.contains("void");
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

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
