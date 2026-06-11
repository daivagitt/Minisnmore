package com.miniandmore.gateway.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.domain.User;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tech.jhipster.config.JHipsterProperties;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private MessageSource messageSource;

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    private MailService mailService;
    private User user;

    @BeforeEach
    void setUp() {
        JHipsterProperties properties = new JHipsterProperties();
        properties.getMail().setFrom("test@localhost");
        properties.getMail().setBaseUrl("http://localhost");
        mailService = new MailService(properties, javaMailSender, messageSource, templateEngine);
        user = new User();
        user.setLogin("user");
        user.setEmail("user@localhost");
        user.setLangKey("en");
    }

    @Test
    void sendEmail_invokesMailSender() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendEmail("to@localhost", "subject", "body", false, false);

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_swallowsMailException() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("fail")).when(javaMailSender).send(mimeMessage);

        mailService.sendEmail("to@localhost", "subject", "body", false, false);

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void sendActivationEmail_processesTemplate() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any())).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");

        mailService.sendActivationEmail(user);

        verify(templateEngine).process(anyString(), any());
    }

    @Test
    void sendCreationEmail_processesTemplate() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any())).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");

        mailService.sendCreationEmail(user);

        verify(templateEngine).process(anyString(), any());
    }

    @Test
    void sendPasswordResetMail_processesTemplate() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any())).thenReturn("content");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("title");

        mailService.sendPasswordResetMail(user);

        verify(templateEngine).process(anyString(), any());
    }

    @Test
    void sendEmailFromTemplate_skipsWhenEmailMissing() {
        user.setEmail(null);

        mailService.sendActivationEmail(user);

        verify(javaMailSender, org.mockito.Mockito.never()).send(org.mockito.Mockito.any(MimeMessage.class));
    }
}
