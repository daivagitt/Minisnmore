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
import java.math.BigDecimal;
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
}
