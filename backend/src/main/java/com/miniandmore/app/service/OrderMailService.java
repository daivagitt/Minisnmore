package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.repository.OrderItemRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tech.jhipster.config.JHipsterProperties;

/**
 * Customer transactional mail for order lifecycle, matching Minis n More auth email branding.
 */
@Service
public class OrderMailService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderMailService.class);
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final ZoneId ORDER_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);

    private final JHipsterProperties jHipsterProperties;
    private final JavaMailSender javaMailSender;
    private final MessageSource messageSource;
    private final SpringTemplateEngine templateEngine;
    private final OrderItemRepository orderItemRepository;
    private final ObjectMapper objectMapper;

    public OrderMailService(
        JHipsterProperties jHipsterProperties,
        JavaMailSender javaMailSender,
        MessageSource messageSource,
        SpringTemplateEngine templateEngine,
        OrderItemRepository orderItemRepository,
        ObjectMapper objectMapper
    ) {
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
        this.orderItemRepository = orderItemRepository;
        this.objectMapper = objectMapper;
    }

    public void sendOrderConfirmation(CustomerOrder order) {
        sendOrderTemplate(order, "mail/orderConfirmationEmail", "email.order.confirm.title", Map.of());
    }

    public void sendOutForDelivery(CustomerOrder order) {
        sendOrderTemplate(order, "mail/orderOutForDeliveryEmail", "email.order.shipped.title", Map.of());
    }

    public void sendOrderDelivered(CustomerOrder order) {
        sendOrderTemplate(order, "mail/orderDeliveredEmail", "email.order.delivered.title", Map.of());
    }

    public void sendOrderCancelled(CustomerOrder order) {
        sendOrderTemplate(order, "mail/orderCancelledEmail", "email.order.cancel.title", Map.of());
    }

    public void sendReturnUpdate(CustomerOrder order, OrderReturn orderReturn) {
        if (orderReturn == null) {
            return;
        }
        String statusKey = returnStatusKey(orderReturn.getStatus());
        sendOrderTemplate(
            order,
            "mail/orderReturnEmail",
            "email.order.return." + statusKey + ".title",
            Map.of(
                "returnNumber",
                StringUtils.defaultString(orderReturn.getReturnNumber()),
                "returnStatus",
                statusKey,
                "returnReason",
                StringUtils.defaultString(orderReturn.getReason()),
                "rejectionReason",
                StringUtils.defaultString(orderReturn.getRejectionReason())
            )
        );
    }

    private void sendOrderTemplate(CustomerOrder order, String templateName, String titleKey, Map<String, Object> extra) {
        if (order == null) {
            return;
        }
        String to = resolveRecipient(order);
        if (StringUtils.isBlank(to)) {
            LOG.debug("Skipping order email {}; no recipient for order '{}'", templateName, order.getOrderNumber());
            return;
        }
        try {
            Locale locale = DEFAULT_LOCALE;
            Context context = new Context(locale);
            String customerName = resolveCustomerName(order);
            String orderNumber = StringUtils.defaultString(order.getOrderNumber());
            String baseUrl = StringUtils.defaultIfBlank(jHipsterProperties.getMail().getBaseUrl(), "https://minisnmore.com");
            String orderUrl = trimTrailingSlash(baseUrl) + "/account/orders/" + orderNumber;
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("customerName", customerName);
            context.setVariable("orderNumber", orderNumber);
            context.setVariable("orderUrl", orderUrl);
            context.setVariable("supportEmail", StringUtils.defaultIfBlank(jHipsterProperties.getMail().getFrom(), "hello@minisnmore.com"));
            context.setVariable("placedOnFormatted", formatOrderDate(order.getPlacedAt()));
            context.setVariable("totalFormatted", formatInr(order.getTotal()));
            context.setVariable("subtotalFormatted", formatInr(order.getSubtotal()));
            context.setVariable("hasDiscount", isPositive(order.getDiscount()));
            context.setVariable("discountFormatted", formatInr(order.getDiscount()));
            context.setVariable("hasShipping", isPositive(order.getShipping()));
            context.setVariable("shippingFormatted", formatInr(order.getShipping()));
            context.setVariable("hasCodFee", isPositive(order.getCodFee()));
            context.setVariable("codFeeFormatted", formatInr(order.getCodFee()));
            context.setVariable("hasTax", isPositive(order.getTax()));
            context.setVariable("taxFormatted", formatInr(order.getTax()));
            context.setVariable("hasDonation", isPositive(order.getDonation()));
            context.setVariable("donationFormatted", formatInr(order.getDonation()));
            context.setVariable("couponCode", StringUtils.defaultString(order.getCouponCode()));
            context.setVariable("paymentMethod", displayPaymentMethod(order.getPaymentMethod()));
            context.setVariable("trackingNumber", StringUtils.defaultString(order.getTrackingNumber()));
            context.setVariable("trackingCarrier", StringUtils.defaultString(order.getTrackingCarrier()));
            context.setVariable("shippingAddress", formatAddress(order.getShippingAddressJson()));
            Map<String, Object> address = buildAddress(order.getShippingAddressJson());
            context.setVariable("addressName", address.get("name"));
            context.setVariable("addressPhone", address.get("phone"));
            context.setVariable("addressLines", address.get("lines"));
            context.setVariable("items", loadLineItems(order.getId()));
            extra.forEach(context::setVariable);
            String content = templateEngine.process(templateName, context);
            String subject = messageSource.getMessage(titleKey, new Object[] { orderNumber }, locale);
            sendHtml(to, subject, content);
        } catch (RuntimeException ex) {
            LOG.warn("Order email '{}' could not be prepared for '{}'", templateName, order.getOrderNumber(), ex);
        }
    }

    private void sendHtml(String to, String subject, String content) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            message.setTo(to);
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, true);
            javaMailSender.send(mimeMessage);
            LOG.info("Sent order email to '{}' with subject '{}'", to, subject);
        } catch (MailException | MessagingException e) {
            LOG.warn("Order email could not be sent to '{}'", to, e);
        }
    }

    String resolveRecipient(CustomerOrder order) {
        JsonNode address = parseAddress(order.getShippingAddressJson());
        String fromAddress = validEmail(text(address, "email"));
        if (fromAddress != null) {
            return fromAddress;
        }
        return validEmail(order.getUserLogin());
    }

    String resolveCustomerName(CustomerOrder order) {
        JsonNode address = parseAddress(order.getShippingAddressJson());
        String fullName = text(address, "full_name");
        if (StringUtils.isNotBlank(fullName)) {
            return fullName.trim();
        }
        String email = resolveRecipient(order);
        if (StringUtils.contains(email, "@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return StringUtils.defaultIfBlank(order.getUserLogin(), "Customer");
    }

    private List<Map<String, String>> loadLineItems(Long orderId) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (orderId == null) {
            return rows;
        }
        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);
        for (OrderItem item : items) {
            String details = joinNonBlank(" · ", item.getItemSize(), item.getColor());
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", StringUtils.defaultString(item.getProductName()));
            row.put("details", details);
            row.put("qty", item.getQty() == null ? "1" : String.valueOf(item.getQty()));
            row.put("unitPrice", formatInr(item.getPrice()));
            row.put("amount", formatInr(lineAmount(item)));
            row.put("image", StringUtils.defaultString(item.getProductImage()));
            rows.add(row);
        }
        return rows;
    }

    private static BigDecimal lineAmount(OrderItem item) {
        BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        int qty = item.getQty() == null ? 1 : item.getQty();
        return price.multiply(BigDecimal.valueOf(qty));
    }

    private JsonNode parseAddress(String json) {
        if (StringUtils.isBlank(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String formatAddress(String json) {
        JsonNode address = parseAddress(json);
        return joinNonBlank(
            ", ",
            text(address, "line1"),
            text(address, "line2"),
            text(address, "city"),
            text(address, "state"),
            text(address, "pincode")
        );
    }

    private Map<String, Object> buildAddress(String json) {
        JsonNode node = parseAddress(json);
        List<String> lines = new ArrayList<>();
        String line1 = text(node, "line1");
        String line2 = text(node, "line2");
        String city = text(node, "city");
        String stateZip = joinNonBlank(" - ", text(node, "state"), text(node, "pincode"));
        if (StringUtils.isNotBlank(line1)) {
            lines.add(line1);
        }
        if (StringUtils.isNotBlank(line2)) {
            lines.add(line2);
        }
        if (StringUtils.isNotBlank(city)) {
            lines.add(city);
        }
        if (StringUtils.isNotBlank(stateZip)) {
            lines.add(stateZip);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", text(node, "full_name"));
        result.put("phone", text(node, "phone"));
        result.put("lines", lines);
        return result;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String formatOrderDate(Instant instant) {
        if (instant == null) {
            return "";
        }
        return ORDER_DATE_FORMAT.format(instant.atZone(ORDER_ZONE));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field)) {
            return "";
        }
        return StringUtils.trimToEmpty(node.path(field).asText(""));
    }

    private static String validEmail(String value) {
        String email = StringUtils.trimToNull(value);
        if (email == null || !email.contains("@") || email.length() > 160) {
            return null;
        }
        return email;
    }

    private static String formatInr(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return "₹" + value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String displayPaymentMethod(String paymentMethod) {
        if ("cod".equalsIgnoreCase(StringUtils.trimToEmpty(paymentMethod))) {
            return "Cash on Delivery";
        }
        return "Online payment";
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static String joinNonBlank(String separator, String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                present.add(part.trim());
            }
        }
        return String.join(separator, present);
    }

    private static String returnStatusKey(OrderReturnStatus status) {
        if (status == null) {
            return "requested";
        }
        return status.name().toLowerCase(Locale.ROOT);
    }
}
