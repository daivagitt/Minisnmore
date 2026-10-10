package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tech.jhipster.config.JHipsterProperties;

@ExtendWith(MockitoExtension.class)
class OrderMailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private MessageSource messageSource;

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @Mock
    private MimeMessage mimeMessage;

    private OrderMailService service;

    @BeforeEach
    void setUp() {
        JHipsterProperties properties = new JHipsterProperties();
        properties.getMail().setFrom("hello@minisnmore.com");
        properties.getMail().setBaseUrl("https://minisnmore.com/");
        service = newService(properties);
    }

    private OrderMailService newService(JHipsterProperties properties) {
        return new OrderMailService(
            properties,
            javaMailSender,
            messageSource,
            templateEngine,
            orderItemRepository,
            siteSettingRepository,
            new ObjectMapper()
        );
    }

    @Test
    void sendOrderConfirmation_sendsHtmlWhenRecipientPresent() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/orderConfirmationEmail"), any(Context.class))).thenReturn("<html>ok</html>");
        when(messageSource.getMessage(eq("email.order.confirm.title"), any(), eq(Locale.ENGLISH))).thenReturn("Confirmed");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(
            List.of(new OrderItem().productName("Romper").itemSize("6-12M").color("Pink").qty(2).price(BigDecimal.valueOf(499)))
        );

        service.sendOrderConfirmation(order);

        verify(javaMailSender).send(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/orderConfirmationEmail"), contextCaptor.capture());
        Context context = contextCaptor.getValue();
        assertThat(context.getVariable("customerName")).isEqualTo("Asha");
        assertThat(context.getVariable("orderUrl")).isEqualTo("https://minisnmore.com/account/orders/MNM-1");
        assertThat(context.getVariable("paymentMethod")).isEqualTo("Cash on Delivery");
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, String>> items = (List<java.util.Map<String, String>>) context.getVariable("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("amount")).isEqualTo("₹998");
    }

    @Test
    void lifecycleAndReturnEmails_processTheirTemplates() {
        CustomerOrder order = orderWithAddress().paymentMethod("online").trackingNumber("XB123").trackingCarrier("Xpressbees");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        service.sendOutForDelivery(order);
        service.sendOrderDelivered(order);
        service.sendDeliveryFailed(order);
        service.sendOrderCancelled(order);
        OrderReturn requested = new OrderReturn();
        requested.setReturnNumber("RET-1");
        requested.setStatus(OrderReturnStatus.REQUESTED);
        requested.setReason("Product arrived damaged in transit");
        service.sendReturnUpdate(order, requested);

        verify(templateEngine).process(eq("mail/orderOutForDeliveryEmail"), any(Context.class));
        verify(templateEngine).process(eq("mail/orderDeliveredEmail"), any(Context.class));
        verify(templateEngine).process(eq("mail/deliveryFailedEmail"), any(Context.class));
        verify(templateEngine).process(eq("mail/orderCancelledEmail"), any(Context.class));
        verify(templateEngine).process(eq("mail/orderReturnEmail"), any(Context.class));
    }

    @Test
    void sendReturnUpdate_skipsFailedAndNull() {
        service.sendReturnUpdate(orderWithAddress(), null);
        OrderReturn failed = new OrderReturn();
        failed.setStatus(OrderReturnStatus.FAILED);
        service.sendReturnUpdate(orderWithAddress(), failed);
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendOrderConfirmation_skipsWhenNoEmail() {
        CustomerOrder order = new CustomerOrder().id(1L).orderNumber("MNM-2").userLogin("guest-user");
        service.sendOrderConfirmation(order);
        verify(javaMailSender, never()).createMimeMessage();
    }

    @Test
    void resolveRecipient_fallsBackToLoginEmail() {
        CustomerOrder order = new CustomerOrder().userLogin("buyer@example.com").shippingAddressJson("{not-json");
        assertThat(service.resolveRecipient(order)).isEqualTo("buyer@example.com");
        assertThat(service.resolveCustomerName(order)).isEqualTo("buyer");
    }

    @Test
    void sendOrderConfirmation_swallowsMailException() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());
        doThrow(new MailSendException("fail")).when(javaMailSender).send(mimeMessage);

        service.sendOrderConfirmation(order);

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void sendOrderConfirmation_swallowsPrepareFailures() {
        CustomerOrder order = orderWithAddress();
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new RuntimeException("thymeleaf"));
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        service.sendOrderConfirmation(order);

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendOrderConfirmation_handlesSparseLineItemsAndBaseUrl() {
        JHipsterProperties properties = new JHipsterProperties();
        properties.getMail().setFrom("hello@minisnmore.com");
        properties.getMail().setBaseUrl("https://minisnmore.com");
        service = newService(properties);
        CustomerOrder order = new CustomerOrder()
            .userLogin("guest")
            .orderNumber("MNM-3")
            .paymentMethod("online")
            .shippingAddressJson("{\"email\":\"ok@example.com\"}");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");

        service.sendOrderConfirmation(order);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/orderConfirmationEmail"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getVariable("orderUrl")).isEqualTo("https://minisnmore.com/account/orders/MNM-3");
        assertThat(contextCaptor.getValue().getVariable("paymentMethod")).isEqualTo("Online payment");
        assertThat(contextCaptor.getValue().getVariable("customerName")).isEqualTo("ok");
        assertThat(contextCaptor.getValue().getVariable("totalFormatted")).isEqualTo("₹0");
    }

    @Test
    void resolveCustomerName_usesLoginWhenNoEmail() {
        CustomerOrder order = new CustomerOrder().userLogin("shopper");
        assertThat(service.resolveCustomerName(order)).isEqualTo("shopper");
    }

    @Test
    void sendReturnUpdate_nullStatusUsesRequestedKey() {
        CustomerOrder order = orderWithAddress();
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-2");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(eq("email.order.return.requested.title"), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(
            List.of(new OrderItem().productName("Tee").qty(null).price(null))
        );

        service.sendReturnUpdate(order, orderReturn);

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void resolveRecipient_rejectsOverlongEmail() {
        String longEmail = "a".repeat(150) + "@example.com";
        CustomerOrder order = new CustomerOrder().userLogin("shopper").shippingAddressJson("{\"email\":\"" + longEmail + "\"}");
        assertThat(service.resolveRecipient(order)).isNull();
        service.sendOrderConfirmation(order);
        verify(javaMailSender, never()).createMimeMessage();
    }

    @Test
    void sendOrderConfirmation_nullOrder_isNoOp() {
        service.sendOrderConfirmation(null);
        verify(javaMailSender, never()).createMimeMessage();
    }

    @Test
    void sendOrderConfirmation_includesSecondAddressLineAndOrderDate() {
        CustomerOrder order = orderWithAddress()
            .placedAt(java.time.Instant.parse("2026-08-14T10:00:00Z"))
            .shippingAddressJson(
                "{\"full_name\":\"Asha\",\"email\":\"asha@example.com\",\"line1\":\"12 Rose Lane\",\"line2\":\"Near Park\",\"city\":\"Pune\",\"state\":\"MH\",\"pincode\":\"411001\"}"
            );
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        service.sendOrderConfirmation(order);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/orderConfirmationEmail"), contextCaptor.capture());
        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) contextCaptor.getValue().getVariable("addressLines");
        assertThat(lines).contains("Near Park");
        assertThat(contextCaptor.getValue().getVariable("orderDate")).isEqualTo("Fri, 14 Aug 2026, 3:30 PM IST");
        assertThat(contextCaptor.getValue().getVariable("placedOnFormatted")).isEqualTo("Fri, 14 Aug 2026, 3:30 PM IST");
    }

    @Test
    void sendPickupFailedAdminNotice_sendsToSiteContactEmail() {
        CustomerOrder order = orderWithAddress();
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-9");
        orderReturn.setPickupFailureReason("Customer not available");
        SiteSetting settings = new SiteSetting();
        settings.setContactEmail("ops@minisnmore.com");
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(java.util.Optional.of(settings));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendPickupFailedAdminNotice(order, orderReturn);

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void cancellationEmails_processTheirTemplates() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-1");
        req.setReason("Changed mind");

        service.sendCancellationRequested(order, req);
        verify(templateEngine).process(eq("mail/cancellationRequestedEmail"), any(Context.class));

        service.sendCancellationAccepted(order, req);
        verify(templateEngine).process(eq("mail/cancellationAcceptedEmail"), any(Context.class));

        service.sendCancellationRejected(order, req);
        verify(templateEngine).process(eq("mail/cancellationRejectedEmail"), any(Context.class));
    }

    @Test
    void sendRefundFailed_processesTemplate() {
        CustomerOrder order = orderWithAddress().paymentMethod("online");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        service.sendRefundFailed(order, BigDecimal.valueOf(1299), "rfnd_fail_1", "online");

        verify(templateEngine).process(eq("mail/refundFailedEmail"), any(Context.class));
    }

    @Test
    void sendOrderCancelled_onlineRefunded_includesRefundExtras() {
        CustomerOrder order = orderWithAddress()
            .paymentMethod("online")
            .paymentStatus(PaymentStatus.REFUNDED)
            .total(BigDecimal.valueOf(1299));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        service.sendOrderCancelled(order);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/orderCancelledEmail"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getVariable("hasRefund")).isEqualTo(true);
        assertThat(contextCaptor.getValue().getVariable("refundAmount")).isEqualTo("₹1299");
    }

    @Test
    void sendCancellationAccepted_onlineWithoutExplicitRefund_usesOrderTotal() {
        CustomerOrder order = orderWithAddress().paymentMethod("online").total(BigDecimal.valueOf(899));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-2");
        req.setReason("Changed mind");

        service.sendCancellationAccepted(order, req);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/cancellationAcceptedEmail"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getVariable("hasRefund")).isEqualTo(true);
        assertThat(contextCaptor.getValue().getVariable("refundAmount")).isEqualTo("₹899");
    }

    @Test
    void sendReturnUpdate_qcRejection_usesQcFailedTemplate() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-QC");
        orderReturn.setStatus(OrderReturnStatus.REJECTED);
        orderReturn.setRejectionReason("QC failed — item shows signs of use");

        service.sendReturnUpdate(order, orderReturn);

        verify(templateEngine).process(eq("mail/returnQcFailedEmail"), any(Context.class));
        verify(templateEngine, never()).process(eq("mail/returnRejectedEmail"), any(Context.class));
    }

    @Test
    void sendReturnUpdate_returnShipped_usesStatusSpecificTemplate() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-SHIP");
        orderReturn.setStatus(OrderReturnStatus.SHIPPED);

        service.sendReturnUpdate(order, orderReturn);

        verify(templateEngine).process(eq("mail/orderReturnEmail"), any(Context.class));
    }

    @Test
    void sendReturnUpdate_manualRefundCompleted_sendsCompletedTemplate() {
        CustomerOrder order = orderWithAddress().paymentMethod("online");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-MAN");
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRefundAmount(BigDecimal.valueOf(499));
        orderReturn.setManualRefundReference("UTR-MAN-1");
        orderReturn.setManualRefundRecordedAt(java.time.Instant.now());

        service.sendReturnUpdate(order, orderReturn);

        verify(templateEngine).process(eq("mail/refundCompletedEmail"), any(Context.class));
        verify(templateEngine, never()).process(eq("mail/refundInitiatedEmail"), any(Context.class));
    }

    @Test
    void refundEmails_processTheirTemplates() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        service.sendRefundInitiated(order, BigDecimal.valueOf(1299), "rfnd_123", "online");
        verify(templateEngine).process(eq("mail/refundInitiatedEmail"), any(Context.class));

        service.sendRefundCompleted(order, BigDecimal.valueOf(1299), "rfnd_123", "online");
        verify(templateEngine).process(eq("mail/refundCompletedEmail"), any(Context.class));
    }

    @Test
    void returnAndPickupEmails_processTheirTemplates() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-123");
        orderReturn.setStatus(OrderReturnStatus.APPROVED);

        service.sendReturnAccepted(order, orderReturn);
        verify(templateEngine).process(eq("mail/returnApprovedEmail"), any(Context.class));

        service.sendReturnRejected(order, orderReturn);
        verify(templateEngine).process(eq("mail/returnRejectedEmail"), any(Context.class));

        service.sendPickupScheduled(order, orderReturn);
        verify(templateEngine).process(eq("mail/pickupScheduledEmail"), any(Context.class));

        service.sendOutForPickup(order, orderReturn);
        verify(templateEngine).process(eq("mail/outForPickupEmail"), any(Context.class));

        service.sendReturnQcFailed(order, orderReturn);
        verify(templateEngine).process(eq("mail/returnQcFailedEmail"), any(Context.class));
    }

    @Test
    void exchangeEmails_processTheirTemplates() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn exchange = new OrderReturn();
        exchange.setReturnNumber("EXG-123");
        exchange.setRequestType(OrderRequestType.EXCHANGE);
        exchange.setStatus(OrderRequestType.EXCHANGE != null ? OrderReturnStatus.APPROVED : null);
        exchange.setReason("Need a larger size for my baby");

        service.sendExchangeRequested(order, exchange);
        ArgumentCaptor<Context> requestedContext = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/exchangeRequestedEmail"), requestedContext.capture());
        assertThat(requestedContext.getValue().getVariable("returnReason")).isEqualTo("Need a larger size for my baby");
        assertThat(requestedContext.getValue().getVariable("exchangeReason")).isEqualTo("Need a larger size for my baby");

        service.sendExchangeAccepted(order, exchange);
        verify(templateEngine).process(eq("mail/exchangeApprovedEmail"), any(Context.class));

        service.sendExchangeRejected(order, exchange);
        verify(templateEngine).process(eq("mail/exchangeRejectedEmail"), any(Context.class));

        service.sendExchangeOutForDelivery(order, exchange);
        verify(templateEngine).process(eq("mail/exchangeOutForDeliveryEmail"), any(Context.class));

        service.sendExchangeDelivered(order, exchange);
        verify(templateEngine).process(eq("mail/exchangeDeliveredEmail"), any(Context.class));
    }

    @Test
    void sendReturnUpdate_routesExchangesCorrectly() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn exchange = new OrderReturn();
        exchange.setReturnNumber("EXG-456");
        exchange.setRequestType(OrderRequestType.EXCHANGE);

        exchange.setStatus(OrderReturnStatus.APPROVED);
        service.sendReturnUpdate(order, exchange);
        verify(templateEngine).process(eq("mail/exchangeApprovedEmail"), any(Context.class));

        exchange.setStatus(OrderReturnStatus.REJECTED);
        service.sendReturnUpdate(order, exchange);
        verify(templateEngine).process(eq("mail/exchangeRejectedEmail"), any(Context.class));

        exchange.setStatus(OrderReturnStatus.SHIPPED);
        service.sendReturnUpdate(order, exchange);
        verify(templateEngine, org.mockito.Mockito.never()).process(eq("mail/exchangeOutForDeliveryEmail"), any(Context.class));

        exchange.setStatus(OrderReturnStatus.DELIVERED);
        service.sendReturnUpdate(order, exchange);
        verify(templateEngine).process(eq("mail/exchangeDeliveredEmail"), any(Context.class));

        exchange.setStatus(OrderReturnStatus.CANCELLED);
        service.sendReturnUpdate(order, exchange);
        verify(templateEngine).process(eq("mail/exchangeCancelledEmail"), any(Context.class));

        exchange.setStatus(OrderReturnStatus.PICKUP_REQUESTED);
        service.sendReturnUpdate(order, exchange);
        verify(templateEngine).process(eq("mail/pickupScheduledEmail"), any(Context.class));

        exchange.setStatus(OrderReturnStatus.RECEIVED);
        service.sendReturnUpdate(order, exchange);
        verify(templateEngine).process(eq("mail/returnReceivedEmail"), any(Context.class));

        org.mockito.Mockito.clearInvocations(templateEngine);
        for (OrderReturnStatus silent : List.of(
            OrderReturnStatus.PICKUP_FAILED,
            OrderReturnStatus.INSPECTED,
            OrderReturnStatus.ACCEPTED,
            OrderReturnStatus.ITEM_ALLOCATED,
            OrderReturnStatus.REPACKING,
            OrderReturnStatus.READY_TO_SHIP,
            OrderReturnStatus.FAILED
        )) {
            exchange.setStatus(silent);
            service.sendReturnUpdate(order, exchange);
        }
        verify(templateEngine, org.mockito.Mockito.never()).process(anyString(), any(Context.class));
    }

    @Test
    void sendReturnUpdate_razorpayRefund_sendsInitiatedEmail() {
        CustomerOrder order = orderWithAddress().paymentMethod("online");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-9");
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRazorpayRefundId("rfnd_live_1");
        orderReturn.setRefundAmount(BigDecimal.valueOf(499));

        service.sendReturnUpdate(order, orderReturn);

        verify(templateEngine).process(eq("mail/refundInitiatedEmail"), any(Context.class));
    }

    @Test
    void sendOrderConfirmation_stampsIstDateHeaderOnSentMessage() throws Exception {
        CustomerOrder order = orderWithAddress();
        MimeMessage realMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(realMessage);
        when(templateEngine.process(eq("mail/orderConfirmationEmail"), any(Context.class))).thenReturn("<html>ok</html>");
        when(messageSource.getMessage(eq("email.order.confirm.title"), any(), eq(Locale.ENGLISH))).thenReturn("Confirmed");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        service.sendOrderConfirmation(order);

        assertThat(realMessage.getHeader("Date", null)).endsWith("+0530");
    }

    @Test
    void sendReturnUpdate_exchangeRefunded_usesRefundLifecycleTemplates() {
        CustomerOrder order = orderWithAddress().paymentMethod("online");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn exchange = new OrderReturn();
        exchange.setReturnNumber("EXG-1");
        exchange.setRequestType(OrderRequestType.EXCHANGE);
        exchange.setStatus(OrderReturnStatus.REFUNDED);
        exchange.setRazorpayRefundId("rfnd_exg_1");
        exchange.setRefundAmount(BigDecimal.valueOf(499));

        service.sendReturnUpdate(order, exchange);

        verify(templateEngine).process(eq("mail/refundInitiatedEmail"), any(Context.class));
    }

    @Test
    void sendReturnUpdate_exchangeZeroRefund_sendsNoRefundMail() {
        CustomerOrder order = orderWithAddress().paymentMethod("online");

        OrderReturn exchange = new OrderReturn();
        exchange.setReturnNumber("EXG-0");
        exchange.setRequestType(OrderRequestType.EXCHANGE);
        exchange.setStatus(OrderReturnStatus.REFUNDED);
        exchange.setRefundAmount(BigDecimal.ZERO);

        service.sendReturnUpdate(order, exchange);

        verify(templateEngine, org.mockito.Mockito.never()).process(anyString(), any(Context.class));
    }

    @Test
    void sendReturnUpdate_exchangeReceived_usesExchangeSubject() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn exchange = new OrderReturn();
        exchange.setReturnNumber("EXG-R");
        exchange.setRequestType(OrderRequestType.EXCHANGE);
        exchange.setStatus(OrderReturnStatus.RECEIVED);

        service.sendReturnUpdate(order, exchange);

        verify(messageSource).getMessage(eq("email.order.exchange.received.title"), any(), eq(Locale.ENGLISH));
    }

    @Test
    void sendReturnUpdate_razorpayRefund_sendsCompletedWhenPaymentRefunded() {
        CustomerOrder order = orderWithAddress().paymentMethod("online").paymentStatus(PaymentStatus.REFUNDED);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-10");
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRazorpayRefundId("rfnd_live_2");
        orderReturn.setRefundAmount(BigDecimal.valueOf(499));

        service.sendReturnUpdate(order, orderReturn);

        verify(templateEngine).process(eq("mail/refundCompletedEmail"), any(Context.class));
    }

    private CustomerOrder orderWithAddress() {
        return new CustomerOrder()
            .id(9L)
            .orderNumber("MNM-1")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .total(BigDecimal.valueOf(1299))
            .shippingAddressJson(
                "{\"full_name\":\"Asha\",\"email\":\"asha@example.com\",\"line1\":\"12 Rose Lane\",\"city\":\"Pune\",\"state\":\"MH\",\"pincode\":\"411001\"}"
            );
    }
}
