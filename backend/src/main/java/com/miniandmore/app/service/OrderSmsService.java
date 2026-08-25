package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.FastSmsClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Customer transactional SMS for order lifecycle via FastSMS India.
 * Message bodies and template IDs must match SMS_API_Integration_Guide.md.pdf §4.
 */
@Service
public class OrderSmsService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderSmsService.class);

    private final FastSmsClient fastSmsClient;
    private final ApplicationProperties.FastSms config;
    private final ObjectMapper objectMapper;

    public OrderSmsService(
        FastSmsClient fastSmsClient,
        ApplicationProperties applicationProperties,
        ObjectMapper objectMapper
    ) {
        this.fastSmsClient = fastSmsClient;
        this.config = applicationProperties.getFastsms();
        this.objectMapper = objectMapper;
    }

    public void sendOrderConfirmation(CustomerOrder order) {
        ApplicationProperties.FastSms.Templates templates = config.getTemplates();
        send(
            order,
            render(
                templates.getOrderConfirmed(),
                order,
                Map.of(
                    "name",
                    resolveCustomerName(order),
                    "orderId",
                    StringUtils.defaultString(order.getOrderNumber()),
                    "amount",
                    formatAmount(order.getTotal())
                )
            ),
            templates.getOrderConfirmedTemplateId()
        );
    }

    public void sendOutForDelivery(CustomerOrder order) {
        // No DLT template registered yet (guide §4.2 note) — skip until FastSMS provides a templateId.
        LOG.debug("Skipping out-for-delivery SMS for '{}'; no DLT template configured", order != null ? order.getOrderNumber() : null);
    }

    public void sendOrderCancelled(CustomerOrder order) {
        ApplicationProperties.FastSms.Templates templates = config.getTemplates();
        send(
            order,
            render(
                templates.getOrderCancelled(),
                order,
                Map.of(
                    "name",
                    resolveCustomerName(order),
                    "orderId",
                    StringUtils.defaultString(order.getOrderNumber())
                )
            ),
            templates.getOrderCancelledTemplateId()
        );
    }

    public void sendReturnUpdate(CustomerOrder order, OrderReturn orderReturn) {
        if (order == null || orderReturn == null || orderReturn.getStatus() == null) {
            return;
        }
        ApplicationProperties.FastSms.Templates templates = config.getTemplates();
        String template;
        String templateId;
        switch (orderReturn.getStatus()) {
            case APPROVED -> {
                template = templates.getReturnApproved();
                templateId = templates.getReturnApprovedTemplateId();
            }
            case REJECTED -> {
                template = templates.getReturnRejected();
                templateId = templates.getReturnRejectedTemplateId();
            }
            case PICKUP_REQUESTED -> {
                template = templates.getReturnPickupScheduled();
                templateId = templates.getReturnPickupScheduledTemplateId();
            }
            case REFUNDED -> {
                template = templates.getReturnRefunded();
                templateId = templates.getReturnRefundedTemplateId();
            }
            case FAILED -> {
                template = templates.getReturnFailed();
                templateId = templates.getReturnFailedTemplateId();
            }
            default -> {
                return;
            }
        }
        if (StringUtils.isBlank(templateId)) {
            // No DLT template registered yet for this event — skip until one is configured.
            LOG.debug("Skipping return SMS for '{}'; no DLT template configured", orderReturn.getReturnNumber());
            return;
        }
        send(
            order,
            render(
                template,
                order,
                Map.of(
                    "name",
                    resolveCustomerName(order),
                    "orderId",
                    StringUtils.defaultString(order.getOrderNumber()),
                    "amount",
                    formatAmount(orderReturn.getRefundAmount())
                )
            ),
            templateId
        );
    }

    private void send(CustomerOrder order, String message, String templateId) {
        if (order == null || StringUtils.isBlank(message)) {
            return;
        }
        if (!fastSmsClient.isConfigured()) {
            LOG.debug("Skipping order SMS; FastSMS not configured for '{}'", order.getOrderNumber());
            return;
        }
        String mobile = resolveMobile(order);
        if (mobile == null) {
            LOG.debug("Skipping order SMS; no mobile for order '{}'", order.getOrderNumber());
            return;
        }
        try {
            fastSmsClient.sendSms(mobile, message, templateId, config.getEntityId());
        } catch (RuntimeException ex) {
            LOG.warn("Order SMS failed for '{}'", order.getOrderNumber(), ex);
        }
    }

    String render(String template, CustomerOrder order, Map<String, String> vars) {
        if (StringUtils.isBlank(template)) {
            return "";
        }
        String text = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", StringUtils.defaultString(entry.getValue()));
        }
        return text;
    }

    String resolveMobile(CustomerOrder order) {
        JsonNode address = parseAddress(order.getShippingAddressJson());
        return FastSmsClient.normalizeMobile(text(address, "phone"));
    }

    String resolveCustomerName(CustomerOrder order) {
        JsonNode address = parseAddress(order.getShippingAddressJson());
        String fullName = text(address, "full_name");
        if (StringUtils.isNotBlank(fullName)) {
            return fullName.trim();
        }
        return StringUtils.defaultIfBlank(order.getUserLogin(), "Customer");
    }

    private JsonNode parseAddress(String json) {
        if (StringUtils.isBlank(json)) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.nullNode();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    /** Numeric amount only — template already includes {@code Rs.} prefix. */
    static String formatAmount(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(0, RoundingMode.HALF_UP);
        return value.toPlainString();
    }
}
