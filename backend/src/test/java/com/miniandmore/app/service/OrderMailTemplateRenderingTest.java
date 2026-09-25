package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import tech.jhipster.config.JHipsterProperties;

/** Renders every customer order template with the real Thymeleaf engine so template/context mismatches fail the build. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderMailTemplateRenderingTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private SiteSettingRepository siteSettingRepository;

    private OrderMailService service;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);

        JHipsterProperties properties = new JHipsterProperties();
        properties.getMail().setFrom("hello@minisnmore.com");
        properties.getMail().setBaseUrl("https://minisnmore.com");

        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        when(orderItemRepository.findAllByOrderId(anyLong())).thenReturn(List.of(item()));

        service = new OrderMailService(properties, javaMailSender, messages, engine, orderItemRepository, siteSettingRepository, new ObjectMapper());
    }

    @Test
    void everyCustomerTemplateRenders() throws Exception {
        CustomerOrder cod = order("cod");
        CustomerOrder online = order("online");

        service.sendOrderConfirmation(cod);
        service.sendOutForDelivery(cod);
        service.sendOrderDelivered(cod);
        service.sendOrderCancelled(cod);
        service.sendCancellationRequested(cod, cancellation(null));
        service.sendCancellationAccepted(cod, cancellation(null));
        service.sendCancellationAccepted(online, cancellation(BigDecimal.valueOf(263)));
        service.sendCancellationRejected(cod, cancellation(null));
        service.sendRefundInitiated(online, BigDecimal.valueOf(263), "rfnd_1", "online");
        service.sendRefundCompleted(cod, BigDecimal.valueOf(263), "UPI-REF", "cod");
        for (OrderReturnStatus status : List.of(
            OrderReturnStatus.REQUESTED,
            OrderReturnStatus.APPROVED,
            OrderReturnStatus.REJECTED,
            OrderReturnStatus.PICKUP_SCHEDULED,
            OrderReturnStatus.PICKED_UP,
            OrderReturnStatus.RECEIVED,
            OrderReturnStatus.REFUNDED
        )) {
            service.sendReturnUpdate(cod, orderReturn(OrderRequestType.RETURN, status, null));
        }
        service.sendReturnQcFailed(cod, orderReturn(OrderRequestType.RETURN, OrderReturnStatus.REJECTED, "QC failed: tags removed"));
        OrderReturn wholeOrderReturn = orderReturn(OrderRequestType.RETURN, OrderReturnStatus.REQUESTED, null);
        wholeOrderReturn.setOrderItem(null);
        service.sendReturnUpdate(cod, wholeOrderReturn);
        for (OrderReturnStatus status : List.of(
            OrderReturnStatus.REQUESTED,
            OrderReturnStatus.APPROVED,
            OrderReturnStatus.REJECTED,
            OrderReturnStatus.SHIPPED,
            OrderReturnStatus.DELIVERED
        )) {
            service.sendReturnUpdate(cod, orderReturn(OrderRequestType.EXCHANGE, status, null));
        }

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, atLeastOnce()).send(sent.capture());
        assertThat(sent.getAllValues()).hasSize(24);
        for (MimeMessage message : sent.getAllValues()) {
            assertThat((String) message.getContent()).contains("MNM-TEST-1");
        }

        String codAccepted = (String) sent.getAllValues().get(5).getContent();
        assertThat(codAccepted).contains("Cancelled").doesNotContain("Refund Initiated");
        String onlineAccepted = (String) sent.getAllValues().get(6).getContent();
        assertThat(onlineAccepted).contains("Cancelled &amp; Refund Initiated").contains("₹263");
        String refundCompleted = (String) sent.getAllValues().get(9).getContent();
        assertThat(refundCompleted).contains("₹263");
    }

    private static CustomerOrder order(String paymentMethod) {
        return new CustomerOrder()
            .id(1L)
            .orderNumber("MNM-TEST-1")
            .userLogin("buyer@example.com")
            .paymentMethod(paymentMethod)
            .total(BigDecimal.valueOf(263))
            .subtotal(BigDecimal.valueOf(263))
            .trackingNumber("AWB1")
            .trackingCarrier("Manual")
            .shippingAddressJson(
                "{\"full_name\":\"Asha\",\"email\":\"asha@example.com\",\"phone\":\"9876543210\",\"line1\":\"12 Rose Lane\",\"city\":\"Pune\",\"state\":\"MH\",\"pincode\":\"411001\"}"
            );
    }

    private static OrderItem item() {
        return new OrderItem().id(11L).productName("Basic Shorts Set").itemSize("12-18 M").color("Baby Blue").qty(1).price(BigDecimal.valueOf(263));
    }

    private static OrderCancellationRequest cancellation(BigDecimal refund) {
        OrderCancellationRequest request = new OrderCancellationRequest();
        request.setRequestNumber("CAN-1");
        request.setReason("Ordered the wrong size");
        request.setRejectionReason("Already packed");
        request.setRequestedAt(Instant.now());
        request.setResolvedAt(Instant.now());
        request.setRefundAmount(refund);
        return request;
    }

    private static OrderReturn orderReturn(OrderRequestType type, OrderReturnStatus status, String rejectionReason) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-1");
        orderReturn.setRequestType(type);
        orderReturn.setStatus(status);
        orderReturn.setReason("Size did not fit");
        orderReturn.setRejectionReason(rejectionReason);
        orderReturn.setRequestedAt(Instant.now());
        orderReturn.setResolvedAt(Instant.now());
        orderReturn.setRefundAmount(BigDecimal.valueOf(263));
        orderReturn.setOrderItem(item());
        orderReturn.setReplacementSize("18-24 M");
        orderReturn.setReplacementColor("Baby Blue");
        return orderReturn;
    }
}
