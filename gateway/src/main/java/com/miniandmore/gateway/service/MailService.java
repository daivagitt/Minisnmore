package com.miniandmore.gateway.service;

import com.miniandmore.gateway.config.Constants;
import com.miniandmore.gateway.domain.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import reactor.core.publisher.Mono;
import tech.jhipster.config.JHipsterProperties;

/**
 * Service for sending emails asynchronously.
 */
@Service
public class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    private static final String USER = "user";

    private static final String BASE_URL = "baseUrl";
    // Keep in sync with catalog MailTimestamps (same pattern + Asia/Kolkata); gateway cannot depend on backend module.
    private static final DateTimeFormatter IST_RFC_2822 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JHipsterProperties jHipsterProperties;

    private final JavaMailSender javaMailSender;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    public MailService(
        JHipsterProperties jHipsterProperties,
        JavaMailSender javaMailSender,
        MessageSource messageSource,
        SpringTemplateEngine templateEngine
    ) {
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
    }

    public void sendEmail(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        Mono.defer(() -> {
            sendEmailSync(to, subject, content, isMultipart, isHtml);
            return Mono.empty();
        }).subscribe();
    }

    private void sendEmailSync(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        LOG.debug(
            "Send email[multipart '{}' and html '{}'] to '{}' with subject '{}' and content={}",
            isMultipart,
            isHtml,
            to,
            subject,
            content
        );

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setTo(to);
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, isHtml);
            stampIstSentDate(mimeMessage);
            javaMailSender.send(mimeMessage);
            LOG.info("Sent email to '{}'", to);
        } catch (MailException | MessagingException e) {
            LOG.warn("Email could not be sent to user '{}'", to, e);
        }
    }

    public void sendEmailFromTemplate(User user, String templateName, String titleKey) {
        sendEmailFromTemplateSync(user, templateName, titleKey, Map.of());
    }

    private void sendEmailFromTemplateSync(User user, String templateName, String titleKey, Map<String, Object> extra) {
        if (user.getEmail() == null) {
            LOG.debug("Email doesn't exist for user '{}'", user.getLogin());
            return;
        }
        String langKey = user.getLangKey() != null ? user.getLangKey() : Constants.DEFAULT_LANGUAGE;
        Locale locale = Locale.forLanguageTag(langKey);
        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(BASE_URL, jHipsterProperties.getMail().getBaseUrl());
        if (extra != null) {
            extra.forEach(context::setVariable);
        }
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, locale);
        sendEmailSync(user.getEmail(), subject, content, false, true);
    }

    public void sendActivationEmail(User user) {
        LOG.info("Sending activation email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/activationEmail", "email.activation.title");
    }

    public void sendCreationEmail(User user) {
        LOG.debug("Sending creation email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/creationEmail", "email.activation.title");
    }

    public void sendInvitationEmail(User user, String assignedRole) {
        LOG.info("Sending invitation email to '{}'", user.getEmail());
        sendEmailFromTemplateSync(
            user,
            "mail/invitationEmail",
            "email.invitation.title",
            Map.of("assignedRole", assignedRole == null ? "" : assignedRole)
        );
    }

    public void sendPasswordResetMail(User user) {
        LOG.debug("Sending password reset email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/passwordResetEmail", "email.reset.title");
    }

    private static void stampIstSentDate(MimeMessage mimeMessage) {
        try {
            mimeMessage.setHeader("Date", IST_RFC_2822.format(ZonedDateTime.now(IST)));
        } catch (MessagingException e) {
            LOG.debug("Could not stamp IST Date header", e);
        }
    }
}
