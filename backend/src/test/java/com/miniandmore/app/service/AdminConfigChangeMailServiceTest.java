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
            "Minis n More admin configuration changed"
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
    void notifySiteSettingsChanged_skipsWhenCurrentMissing() {
        service.notifySiteSettingsChanged(new SiteSettingDTO(), null);
        verify(javaMailSender, never()).createMimeMessage();
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
    }
}
