package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tech.jhipster.config.JHipsterProperties;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderMailServiceQcAndRefundMatrixTest {

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

    private OrderMailService service;

    @BeforeEach
    void setUp() {
        JHipsterProperties properties = new JHipsterProperties();
        properties.getMail().setFrom("store@example.com");
        properties.getMail().setBaseUrl("https://example.test");
        service = new OrderMailService(
            properties,
            javaMailSender,
            messageSource,
            templateEngine,
            orderItemRepository,
            siteSettingRepository,
            new ObjectMapper()
        );
        lenient().when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        lenient().when(orderItemRepository.findAllByOrderId(any())).thenReturn(java.util.List.of());
        lenient().when(templateEngine.process(any(String.class), any())).thenReturn("<html/>");
        lenient().when(messageSource.getMessage(any(), any(), any(), any())).thenReturn("title");
    }

    @Test
    void isQcFailure_nullAndKeywordMatrix() {
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", new Object[] { null })).isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", notes("ok", "fine"))).isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", notes("QC fail", null))).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", notes("quality issue", ""))).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", notes("please inspect", null))).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", notes(null, "qc"))).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", notes(null, "quality"))).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", notes("", "inspect stains"))).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", notes("", "looks used"))).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", notes("", "damage on hem"))).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isQcFailure", notes("", "missing tag"))).isTrue();
    }

    @Test
    void sendRefundOutcome_nonPositiveAmountReturnsImmediately() {
        OrderReturn zero = new OrderReturn();
        zero.setRefundAmount(BigDecimal.ZERO);
        OrderReturn negative = new OrderReturn();
        negative.setRefundAmount(new BigDecimal("-1"));
        CustomerOrder order = new CustomerOrder().paymentMethod("online").paymentStatus(PaymentStatus.PAID).total(new BigDecimal("99"));
        ReflectionTestUtils.invokeMethod(service, "sendRefundOutcome", order, zero);
        ReflectionTestUtils.invokeMethod(service, "sendRefundOutcome", order, negative);
        assertThat(zero.getRefundAmount().signum()).isZero();
    }

    private static OrderReturn notes(String admin, String reason) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setAdminNotes(admin);
        orderReturn.setRejectionReason(reason);
        return orderReturn;
    }
}
