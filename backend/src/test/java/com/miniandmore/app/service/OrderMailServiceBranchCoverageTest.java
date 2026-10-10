package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tech.jhipster.config.JHipsterProperties;

@ExtendWith(MockitoExtension.class)
class OrderMailServiceBranchCoverageTest {

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
        service = new OrderMailService(
            properties,
            javaMailSender,
            messageSource,
            templateEngine,
            orderItemRepository,
            siteSettingRepository,
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
    }

    @Test
    void sendRefundCompleted_nullAmountAndMethod_fallsBackToOrderFields() {
        CustomerOrder order = orderWithAddress().paymentMethod("cod").total(BigDecimal.valueOf(750));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("html");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Refund done");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        service.sendRefundCompleted(order, null, "rfnd_x", null);

        ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/refundCompletedEmail"), ctx.capture());
        assertThat(ctx.getValue().getVariable("refundAmount")).isEqualTo("₹750");
        assertThat(ctx.getValue().getVariable("paymentMethodDisplay")).isEqualTo("Cash on Delivery");
    }

    @Test
    void sendReturnUpdate_refundedZeroAmount_skipsRefundEmails() {
        CustomerOrder order = orderWithAddress();

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ZERO");
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRefundAmount(BigDecimal.ZERO);

        service.sendReturnUpdate(order, orderReturn);

        verify(templateEngine, never()).process(eq("mail/refundCompletedEmail"), any(Context.class));
        verify(templateEngine, never()).process(eq("mail/refundInitiatedEmail"), any(Context.class));
    }

    @Test
    void sendReturnUpdate_razorpayRefundPendingPayment_sendsInitiatedTemplate() {
        CustomerOrder order = orderWithAddress().paymentMethod("online").paymentStatus(PaymentStatus.PAID);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("html");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Refund initiated");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-RZP");
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRefundAmount(BigDecimal.valueOf(200));
        orderReturn.setRazorpayRefundId("rfnd_live_1");

        service.sendReturnUpdate(order, orderReturn);

        verify(templateEngine).process(eq("mail/refundInitiatedEmail"), any(Context.class));
        verify(templateEngine, never()).process(eq("mail/refundCompletedEmail"), any(Context.class));
    }

    @Test
    void sendReturnUpdate_razorpayRefundAlreadyRefunded_sendsCompletedTemplate() {
        CustomerOrder order = orderWithAddress().paymentMethod("online").paymentStatus(PaymentStatus.REFUNDED);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("html");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Refund completed");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-DONE");
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRefundAmount(BigDecimal.valueOf(200));
        orderReturn.setRazorpayRefundId("rfnd_live_2");

        service.sendReturnUpdate(order, orderReturn);

        verify(templateEngine).process(eq("mail/refundCompletedEmail"), any(Context.class));
        verify(templateEngine, never()).process(eq("mail/refundInitiatedEmail"), any(Context.class));
    }

    @Test
    void sendReturnReceived_exchangeUsesExchangeTitleKey() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("html");
        when(messageSource.getMessage(eq("email.order.exchange.received.title"), any(), any())).thenReturn("Exchange received");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderReturn exchange = new OrderReturn();
        exchange.setReturnNumber("EXG-1");
        exchange.setRequestType(OrderRequestType.EXCHANGE);
        exchange.setStatus(OrderReturnStatus.RECEIVED);

        service.sendReturnReceived(order, exchange);

        verify(messageSource).getMessage(eq("email.order.exchange.received.title"), any(), any());
    }

    @Test
    void buildReturnExtra_includesReplacementNoteOnLineItem() {
        CustomerOrder order = orderWithAddress();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("html");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Approved");
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of());

        OrderItem line = new OrderItem().id(3L).productName("Dress").qty(1).price(BigDecimal.TEN);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-REP");
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        orderReturn.setOrderItem(line);
        orderReturn.setReplacementSize("12M");
        orderReturn.setReplacementColor("Blue");
        orderReturn.setReplacementVariantSku("DRS-12M-BLU");

        service.sendReturnAccepted(order, orderReturn);

        ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/returnApprovedEmail"), ctx.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) ctx.getValue().getVariable("items");
        assertThat(items.get(0).get("exchangeNote")).contains("12M").contains("Blue").contains("DRS-12M-BLU");
    }

    @ParameterizedTest
    @CsvSource({
        "QC failed on inspection, true",
        "Item looks used, true",
        "quality issue noted, true",
        "tag removed, true",
        "visible damage on seam, true",
        "changed mind, false"
    })
    void isQcFailure_detectsKeywordCombinations(String text, boolean expected) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setRejectionReason(text);
        Boolean qc = ReflectionTestUtils.invokeMethod(service, "isQcFailure", orderReturn);
        assertThat(qc).isEqualTo(expected);
    }

    @Test
    void sendPickupFailedAdminNotice_blankAdminEmail_skipsSend() {
        JHipsterProperties blankFrom = new JHipsterProperties();
        blankFrom.getMail().setFrom("  ");
        service = new OrderMailService(
            blankFrom,
            javaMailSender,
            messageSource,
            templateEngine,
            orderItemRepository,
            siteSettingRepository,
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        CustomerOrder order = orderWithAddress();
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-PICK");

        service.sendPickupFailedAdminNotice(order, orderReturn);

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendPickupFailedAdminNotice_configuredAdmin_sendsMail() {
        when(siteSettingRepository.findFirstByOrderByIdAsc())
            .thenReturn(Optional.of(new SiteSetting().contactEmail("ops@minisnmore.com")));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        CustomerOrder order = orderWithAddress();
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-PICK");
        orderReturn.setReason("Customer unavailable");

        service.sendPickupFailedAdminNotice(order, orderReturn);

        verify(javaMailSender).send(mimeMessage);
    }

    private static CustomerOrder orderWithAddress() {
        return new CustomerOrder()
            .id(9L)
            .orderNumber("MNM-9")
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .paymentMethod("online")
            .total(BigDecimal.valueOf(999))
            .shippingAddressJson(
                "{\"full_name\":\"Jane\",\"email\":\"buyer@example.com\",\"phone\":\"9876543210\",\"line1\":\"12 MG\",\"city\":\"BLR\",\"state\":\"KA\",\"pincode\":\"560001\"}"
            );
    }
}
