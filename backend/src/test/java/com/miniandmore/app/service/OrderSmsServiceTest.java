package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.FastSmsClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSmsServiceTest {

    @Mock
    private FastSmsClient fastSmsClient;

    private ApplicationProperties properties;
    private OrderSmsService service;

    @BeforeEach
    void setUp() {
        properties = new ApplicationProperties();
        properties.getFastsms().setEnabled(true);
        service = new OrderSmsService(fastSmsClient, properties, new ObjectMapper());
    }

    @Test
    void sendOrderConfirmation_sendsGuideTemplate() {
        when(fastSmsClient.isConfigured()).thenReturn(true);
        when(fastSmsClient.sendSms(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        CustomerOrder order = new CustomerOrder()
            .orderNumber("12347")
            .total(new BigDecimal("100.00"))
            .shippingAddressJson(
                "{\"full_name\":\"Parthee\",\"phone\":\"+91 98765 43210\",\"email\":\"a@example.com\"}"
            );

        service.sendOrderConfirmation(order);

        verify(fastSmsClient).sendSms(
            eq("9876543210"),
            eq(
                "Dear Parthee, your order 12347 from Minis n More has been confirmed. Order amount: Rs.100. Thank you for shopping with us."
            ),
            eq("1777178696636494530"),
            eq("1701178669941271239")
        );
    }

    @Test
    void sendOrderCancelled_sendsGuideTemplate() {
        when(fastSmsClient.isConfigured()).thenReturn(true);
        when(fastSmsClient.sendSms(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        CustomerOrder order = new CustomerOrder()
            .orderNumber("12347")
            .shippingAddressJson("{\"full_name\":\"Parthee\",\"phone\":\"9740199455\"}");

        service.sendOrderCancelled(order);

        verify(fastSmsClient).sendSms(
            eq("9740199455"),
            eq(
                "Dear Parthee, your Minis n More order 12347 has been cancelled successfully as requested. If payment was made online, the applicable refund will be processed as per our refund policy."
            ),
            eq("1777178696692470339"),
            eq("1701178669941271239")
        );
    }

    @Test
    void sendOrderDelivered_isNoOpUntilDltTemplateRegistered() {
        service.sendOrderDelivered(
            new CustomerOrder()
                .orderNumber("ORD-1")
                .shippingAddressJson("{\"full_name\":\"Parthee\",\"phone\":\"9740199455\"}")
        );
        verify(fastSmsClient, never()).sendSms(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendOrderConfirmation_skipsWhenNoPhone() {
        when(fastSmsClient.isConfigured()).thenReturn(true);
        CustomerOrder order = new CustomerOrder().orderNumber("ORD-1").shippingAddressJson("{\"full_name\":\"Ada\"}");

        service.sendOrderConfirmation(order);

        verify(fastSmsClient).isConfigured();
        verify(fastSmsClient, never()).sendSms(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendOutForDelivery_isNoOpWithoutDltTemplate() {
        CustomerOrder order = new CustomerOrder().orderNumber("ORD-1");
        service.sendOutForDelivery(order);
        verify(fastSmsClient, never()).isConfigured();
    }

    @Test
    void sendOrderConfirmation_skipsWhenFastSmsNotConfigured() {
        when(fastSmsClient.isConfigured()).thenReturn(false);
        CustomerOrder order = new CustomerOrder()
            .orderNumber("ORD-2")
            .total(BigDecimal.TEN)
            .shippingAddressJson("{\"full_name\":\"Ada\",\"phone\":\"9876543210\"}");

        service.sendOrderConfirmation(order);

        verify(fastSmsClient).isConfigured();
        verify(fastSmsClient, never()).sendSms(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendOrderConfirmation_invalidPhone_skipsSend() {
        when(fastSmsClient.isConfigured()).thenReturn(true);
        CustomerOrder order = new CustomerOrder()
            .orderNumber("ORD-3")
            .total(BigDecimal.TEN)
            .shippingAddressJson("{\"full_name\":\"Ada\",\"phone\":\"12345\"}");

        service.sendOrderConfirmation(order);

        verify(fastSmsClient, never()).sendSms(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendOrderConfirmation_swallowsSmsFailure() {
        when(fastSmsClient.isConfigured()).thenReturn(true);
        when(fastSmsClient.sendSms(anyString(), anyString(), anyString(), anyString())).thenThrow(new RuntimeException("gateway down"));
        CustomerOrder order = new CustomerOrder()
            .orderNumber("ORD-4")
            .total(BigDecimal.TEN)
            .shippingAddressJson("{\"full_name\":\"Ada\",\"phone\":\"9876543210\"}");

        service.sendOrderConfirmation(order);

        verify(fastSmsClient).sendSms(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendReturnUpdate_approved_sendsTemplate() {
        configureReturnTemplates();
        when(fastSmsClient.isConfigured()).thenReturn(true);
        when(fastSmsClient.sendSms(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        CustomerOrder order = orderWithPhone();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);

        service.sendReturnUpdate(order, orderReturn);

        verify(fastSmsClient).sendSms(
            eq("9876543210"),
            eq(
                "Dear Parthee, your return/exchange for order 12347 has been approved. We'll arrange pickup shortly. — Minis n More"
            ),
            eq("tpl-approved"),
            eq("1701178669941271239")
        );
    }

    @Test
    void sendReturnUpdate_rejected_sendsTemplate() {
        configureReturnTemplates();
        when(fastSmsClient.isConfigured()).thenReturn(true);
        when(fastSmsClient.sendSms(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        service.sendReturnUpdate(orderWithPhone(), returnWithStatus(OrderReturnStatus.REJECTED));

        verify(fastSmsClient).sendSms(
            eq("9876543210"),
            eq(
                "Dear Parthee, we're unable to approve your return/exchange request for order 12347. Contact support for details. — Minis n More"
            ),
            eq("tpl-rejected"),
            eq("1701178669941271239")
        );
    }

    @Test
    void sendReturnUpdate_pickupRequested_sendsTemplate() {
        configureReturnTemplates();
        when(fastSmsClient.isConfigured()).thenReturn(true);
        when(fastSmsClient.sendSms(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        service.sendReturnUpdate(orderWithPhone(), returnWithStatus(OrderReturnStatus.PICKUP_REQUESTED));

        verify(fastSmsClient).sendSms(
            eq("9876543210"),
            eq(
                "Dear Parthee, pickup for your return/exchange on order 12347 is scheduled. Please keep the item ready. — Minis n More"
            ),
            eq("tpl-pickup"),
            eq("1701178669941271239")
        );
    }

    @Test
    void sendReturnUpdate_refunded_sendsTemplateWithAmount() {
        configureReturnTemplates();
        when(fastSmsClient.isConfigured()).thenReturn(true);
        when(fastSmsClient.sendSms(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRefundAmount(new BigDecimal("499.50"));

        service.sendReturnUpdate(orderWithPhone(), orderReturn);

        verify(fastSmsClient).sendSms(
            eq("9876543210"),
            eq("Dear Parthee, your refund of Rs.500 for order 12347 has been processed. — Minis n More"),
            eq("tpl-refunded"),
            eq("1701178669941271239")
        );
    }

    @Test
    void sendReturnUpdate_failed_sendsTemplate() {
        configureReturnTemplates();
        when(fastSmsClient.isConfigured()).thenReturn(true);
        when(fastSmsClient.sendSms(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        service.sendReturnUpdate(orderWithPhone(), returnWithStatus(OrderReturnStatus.FAILED));

        verify(fastSmsClient).sendSms(
            eq("9876543210"),
            eq(
                "Dear Parthee, we could not process your refund for order 12347. Our team will contact you shortly. — Minis n More"
            ),
            eq("tpl-failed"),
            eq("1701178669941271239")
        );
    }

    @Test
    void sendReturnUpdate_noTemplateId_skipsSend() {
        service.sendReturnUpdate(orderWithPhone(), returnWithStatus(OrderReturnStatus.APPROVED));

        verify(fastSmsClient, never()).sendSms(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendReturnUpdate_unhandledStatus_skipsSend() {
        configureReturnTemplates();

        service.sendReturnUpdate(orderWithPhone(), returnWithStatus(OrderReturnStatus.REQUESTED));

        verify(fastSmsClient, never()).sendSms(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendReturnUpdate_nullInputs_areNoOp() {
        service.sendReturnUpdate(null, returnWithStatus(OrderReturnStatus.APPROVED));
        service.sendReturnUpdate(orderWithPhone(), null);
        verify(fastSmsClient, never()).sendSms(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void render_blankTemplate_returnsEmptyString() {
        assertThat(service.render("  ", orderWithPhone(), Map.of())).isEmpty();
    }

    @Test
    void resolveMobile_blankShippingAddress_returnsNull() {
        CustomerOrder order = new CustomerOrder().shippingAddressJson("");

        assertThat(service.resolveMobile(order)).isNull();
    }

    @Test
    void sendOutForDelivery_nullOrder_isNoOp() {
        service.sendOutForDelivery(null);
        verify(fastSmsClient, never()).sendSms(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendOrderDelivered_nullOrder_isNoOp() {
        service.sendOrderDelivered(null);
        verify(fastSmsClient, never()).sendSms(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void resolveCustomerName_fallsBackToUserLogin() {
        CustomerOrder order = new CustomerOrder().userLogin("buyer@example.com").shippingAddressJson("{}");

        assertThat(service.resolveCustomerName(order)).isEqualTo("buyer@example.com");
    }

    @Test
    void resolveCustomerName_invalidJson_fallsBackToCustomer() {
        CustomerOrder order = new CustomerOrder().shippingAddressJson("not-json");

        assertThat(service.resolveCustomerName(order)).isEqualTo("Customer");
    }

    @Test
    void formatAmount_roundsAndHandlesNull() {
        assertThat(OrderSmsService.formatAmount(null)).isEqualTo("0");
        assertThat(OrderSmsService.formatAmount(new BigDecimal("99.60"))).isEqualTo("100");
    }

    private void configureReturnTemplates() {
        ApplicationProperties.FastSms.Templates templates = properties.getFastsms().getTemplates();
        templates.setReturnApprovedTemplateId("tpl-approved");
        templates.setReturnRejectedTemplateId("tpl-rejected");
        templates.setReturnPickupScheduledTemplateId("tpl-pickup");
        templates.setReturnRefundedTemplateId("tpl-refunded");
        templates.setReturnFailedTemplateId("tpl-failed");
    }

    private CustomerOrder orderWithPhone() {
        return new CustomerOrder()
            .orderNumber("12347")
            .shippingAddressJson("{\"full_name\":\"Parthee\",\"phone\":\"9876543210\"}");
    }

    private OrderReturn returnWithStatus(OrderReturnStatus status) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-12347-ABC");
        orderReturn.setStatus(status);
        return orderReturn;
    }
}
