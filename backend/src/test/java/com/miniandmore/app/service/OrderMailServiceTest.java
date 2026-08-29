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
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.repository.OrderItemRepository;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.List;
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
    private MimeMessage mimeMessage;

    private OrderMailService service;

    @BeforeEach
    void setUp() {
        JHipsterProperties properties = new JHipsterProperties();
        properties.getMail().setFrom("hello@minisnmore.com");
        properties.getMail().setBaseUrl("https://minisnmore.com/");
        service = new OrderMailService(
            properties,
            javaMailSender,
            messageSource,
            templateEngine,
            orderItemRepository,
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
        service.sendOrderCancelled(order);
        OrderReturn requested = new OrderReturn();
        requested.setReturnNumber("RET-1");
        requested.setStatus(OrderReturnStatus.REQUESTED);
        requested.setReason("Product arrived damaged in transit");
        service.sendReturnUpdate(order, requested);

        verify(templateEngine).process(eq("mail/orderOutForDeliveryEmail"), any(Context.class));
        verify(templateEngine).process(eq("mail/orderDeliveredEmail"), any(Context.class));
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
        service = new OrderMailService(
            properties,
            javaMailSender,
            messageSource,
            templateEngine,
            orderItemRepository,
            new ObjectMapper()
        );
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
