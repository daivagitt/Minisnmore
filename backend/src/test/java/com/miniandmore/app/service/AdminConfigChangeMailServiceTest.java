package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.domain.enumeration.DiscountType;
import com.miniandmore.app.service.dto.CouponDTO;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tech.jhipster.config.JHipsterProperties;

@ExtendWith(MockitoExtension.class)
class AdminConfigChangeMailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private MessageSource messageSource;

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    private ApplicationProperties applicationProperties;
    private AdminConfigChangeMailService service;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        JHipsterProperties jHipsterProperties = new JHipsterProperties();
        jHipsterProperties.getMail().setFrom("hello@minisnmore.com");
        service = new AdminConfigChangeMailService(
            applicationProperties,
            jHipsterProperties,
            javaMailSender,
            messageSource,
            templateEngine,
            new ObjectMapper()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("admin.user", "n/a"));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void notifySiteSettingsChanged_emailsConfiguredInboxWithDiff() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/adminConfigChangeEmail"), any(Context.class))).thenReturn("<html>ok</html>");
        when(messageSource.getMessage(eq("email.admin.config.title"), any(), eq(Locale.ENGLISH))).thenReturn(
            "Minis n More admin configuration changed (Site settings)"
        );
        when(messageSource.getMessage(eq("email.admin.config.intro.saved"), any(), eq(Locale.ENGLISH))).thenReturn(
            "Site settings were saved"
        );
        when(messageSource.getMessage(eq("email.admin.config.preheader.saved"), any(), eq(Locale.ENGLISH))).thenReturn(
            "Site settings were updated"
        );

        SiteSettingDTO previous = new SiteSettingDTO();
        previous.setBrandName("Old Brand");
        previous.setGstRate(new BigDecimal("5.00"));
        SiteSettingDTO current = new SiteSettingDTO();
        current.setBrandName("New Brand");
        current.setGstRate(new BigDecimal("12"));

        service.notifySiteSettingsChanged(previous, current);

        verify(javaMailSender).send(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/adminConfigChangeEmail"), contextCaptor.capture());
        Context context = contextCaptor.getValue();
        assertThat(context.getVariable("changedBy")).isEqualTo("admin.user");
        assertThat(context.getVariable("area")).isEqualTo("Site settings");
        assertThat(context.getVariable("hasChanges")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> changes = (List<Map<String, String>>) context.getVariable("changes");
        assertThat(changes)
            .anySatisfy(row -> {
                assertThat(row.get("field")).isEqualTo("Brand Name");
                assertThat(row.get("from")).isEqualTo("Old Brand");
                assertThat(row.get("to")).isEqualTo("New Brand");
            })
            .anySatisfy(row -> {
                assertThat(row.get("field")).isEqualTo("Gst Rate");
                assertThat(row.get("from")).isEqualTo("5");
                assertThat(row.get("to")).isEqualTo("12");
            });
    }

    @Test
    void notifyCouponChanged_includesCouponFields() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/adminConfigChangeEmail"), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(any(), any(), any())).thenReturn("text");

        CouponDTO previous = new CouponDTO();
        previous.setCode("OLD10");
        previous.setDiscountType(DiscountType.PERCENT);
        previous.setDiscountValue(new BigDecimal("10"));
        previous.setMinOrder(BigDecimal.ZERO);
        previous.setActive(true);
        CouponDTO current = new CouponDTO();
        current.setCode("NEW20");
        current.setDiscountType(DiscountType.PERCENT);
        current.setDiscountValue(new BigDecimal("20"));
        current.setMinOrder(BigDecimal.ZERO);
        current.setActive(true);

        service.notifyCouponChanged(previous, current);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/adminConfigChangeEmail"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getVariable("area")).isEqualTo("Coupons");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> changes = (List<Map<String, String>>) contextCaptor.getValue().getVariable("changes");
        assertThat(changes)
            .anySatisfy(row -> {
                assertThat(row.get("field")).isEqualTo("Code");
                assertThat(row.get("from")).isEqualTo("OLD10");
                assertThat(row.get("to")).isEqualTo("NEW20");
            });
        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void notifyZohoSettingsChanged_masksSecretsAndShowsUpdates() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/adminConfigChangeEmail"), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(any(), any(), any())).thenReturn("text");

        ZohoSetting previous = new ZohoSetting()
            .orgId("org-1")
            .clientId("client-1")
            .clientSecret("secret-old")
            .refreshToken("refresh-old")
            .dataCenter("in")
            .connected(true);
        ZohoSetting current = new ZohoSetting()
            .orgId("org-2")
            .clientId("client-1")
            .clientSecret("secret-new")
            .refreshToken("refresh-old")
            .dataCenter("eu")
            .connected(true);

        service.notifyZohoSettingsChanged(previous, current);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/adminConfigChangeEmail"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getVariable("area")).isEqualTo("Zoho settings");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> changes = (List<Map<String, String>>) contextCaptor.getValue().getVariable("changes");
        assertThat(changes)
            .anySatisfy(row -> {
                assertThat(row.get("field")).isEqualTo("Org Id");
                assertThat(row.get("from")).isEqualTo("org-1");
                assertThat(row.get("to")).isEqualTo("org-2");
            })
            .anySatisfy(row -> {
                assertThat(row.get("field")).isEqualTo("Client Secret");
                assertThat(row.get("from")).isEqualTo("(set)");
                assertThat(row.get("to")).isEqualTo("(updated)");
            })
            .anySatisfy(row -> {
                assertThat(row.get("field")).isEqualTo("Data Center");
                assertThat(row.get("from")).isEqualTo("in");
                assertThat(row.get("to")).isEqualTo("eu");
            })
            .noneSatisfy(row -> assertThat(row.get("from")).contains("secret-"))
            .noneSatisfy(row -> assertThat(row.get("to")).contains("secret-"));
    }

    @Test
    void notifySiteSettingsChanged_usesDefaultRecipient() {
        assertThat(applicationProperties.getMail().getConfigChangeTo()).isEqualTo(
            "info@stoltglobal.com,saikedar2003@gmail.com"
        );
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/adminConfigChangeEmail"), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(any(), any(), any())).thenReturn("title");

        SiteSettingDTO current = new SiteSettingDTO();
        current.setBrandName("Mini");
        service.notifySiteSettingsChanged(null, current);

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void notifyConfigChanged_skipsWhenBothMissing() {
        service.notifyCouponChanged(null, null);
        verify(javaMailSender, never()).createMimeMessage();
    }

    @Test
    void notifyCouponChanged_deleteUsesDeletedCopy() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/adminConfigChangeEmail"), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(eq("email.admin.config.title.deleted"), any(), eq(Locale.ENGLISH))).thenReturn(
            "deleted title"
        );
        when(messageSource.getMessage(eq("email.admin.config.intro.deleted"), any(), eq(Locale.ENGLISH))).thenReturn(
            "deleted intro"
        );
        when(messageSource.getMessage(eq("email.admin.config.preheader.deleted"), any(), eq(Locale.ENGLISH))).thenReturn(
            "deleted preheader"
        );

        CouponDTO previous = new CouponDTO();
        previous.setCode("GONE");
        previous.setDiscountType(DiscountType.FLAT);
        previous.setDiscountValue(new BigDecimal("50"));
        previous.setMinOrder(BigDecimal.ZERO);
        previous.setActive(true);

        service.notifyCouponChanged(previous, null);

        verify(javaMailSender).send(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/adminConfigChangeEmail"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getVariable("intro")).isEqualTo("deleted intro");
        assertThat(contextCaptor.getValue().getVariable("subject")).isEqualTo("deleted title");
        assertThat(contextCaptor.getValue().getVariable("hasChanges")).isEqualTo(true);
    }

    @Test
    void notifySiteSettingsChanged_skipsBlankRecipient() {
        applicationProperties.getMail().setConfigChangeTo("  ");
        SiteSettingDTO current = new SiteSettingDTO();
        current.setBrandName("Mini");
        service.notifySiteSettingsChanged(null, current);
        verify(javaMailSender, never()).createMimeMessage();
    }

    @Test
    void notifySiteSettingsChanged_doesNotThrowWhenSendFails() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("mail/adminConfigChangeEmail"), any(Context.class))).thenReturn("content");
        when(messageSource.getMessage(any(), any(), any())).thenReturn("title");
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(mimeMessage);

        SiteSettingDTO current = new SiteSettingDTO();
        current.setBrandName("Mini");
        service.notifySiteSettingsChanged(null, current);

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void parseRecipients_splitsCommaAndSemicolonLists() {
        assertThat(AdminConfigChangeMailService.parseRecipients("info@stoltglobal.com, saikedar2003@gmail.com"))
            .containsExactly("info@stoltglobal.com", "saikedar2003@gmail.com");
        assertThat(AdminConfigChangeMailService.parseRecipients("a@x.com; a@x.com, b@x.com")).containsExactly(
            "a@x.com",
            "b@x.com"
        );
    }

    @Test
    void humanizeAndDisplay_formatValues() {
        assertThat(AdminConfigChangeMailService.humanize("contactEmail")).isEqualTo("Contact Email");
        assertThat(AdminConfigChangeMailService.display(null)).isEqualTo("(empty)");
        assertThat(AdminConfigChangeMailService.display(new BigDecimal("20.00"))).isEqualTo("20");
        assertThat(AdminConfigChangeMailService.display("x".repeat(300))).endsWith("...");
        assertThat(AdminConfigChangeMailService.display(DiscountType.BOGO)).isEqualTo("BOGO");
    }
}
