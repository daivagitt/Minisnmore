package com.miniandmore.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.security.SecurityUtils;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tech.jhipster.config.JHipsterProperties;

/**
 * Notifies Stolt Global when admin site configuration (site settings) changes.
 */
@Service
public class AdminConfigChangeMailService {

    static final String DEFAULT_RECIPIENT = "info@stoltglobal.com";
    private static final Logger LOG = LoggerFactory.getLogger(AdminConfigChangeMailService.class);
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter CHANGED_AT_FORMAT = DateTimeFormatter.ofPattern(
        "EEE, d MMM yyyy, h:mm a z",
        Locale.ENGLISH
    );
    private static final int MAX_VALUE_LENGTH = 280;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ApplicationProperties applicationProperties;
    private final JHipsterProperties jHipsterProperties;
    private final JavaMailSender javaMailSender;
    private final MessageSource messageSource;
    private final SpringTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    public AdminConfigChangeMailService(
        ApplicationProperties applicationProperties,
        JHipsterProperties jHipsterProperties,
        JavaMailSender javaMailSender,
        MessageSource messageSource,
        SpringTemplateEngine templateEngine,
        ObjectMapper objectMapper
    ) {
        this.applicationProperties = applicationProperties;
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
        this.objectMapper = objectMapper;
    }

    public void notifySiteSettingsChanged(SiteSettingDTO previous, SiteSettingDTO current) {
        if (current == null) {
            return;
        }
        String to = resolveRecipient();
        if (StringUtils.isBlank(to)) {
            LOG.debug("Skipping admin configuration email; recipient is blank");
            return;
        }
        try {
            List<Map<String, String>> changes = diff(previous, current);
            Locale locale = DEFAULT_LOCALE;
            Context context = new Context(locale);
            String changedBy = SecurityUtils.getCurrentUserLogin().filter(StringUtils::isNotBlank).orElse("unknown");
            context.setVariable("changedBy", changedBy);
            context.setVariable("changedAt", ZonedDateTime.now(IST).format(CHANGED_AT_FORMAT));
            context.setVariable("changes", changes);
            context.setVariable("hasChanges", !changes.isEmpty());
            String content = templateEngine.process("mail/adminConfigChangeEmail", context);
            String subject = messageSource.getMessage("email.admin.config.title", null, locale);
            sendHtml(to, subject, content);
        } catch (RuntimeException ex) {
            LOG.warn("Admin configuration email could not be prepared", ex);
        }
    }

    List<Map<String, String>> diff(SiteSettingDTO previous, SiteSettingDTO current) {
        Map<String, Object> before = toMap(previous);
        Map<String, Object> after = toMap(current);
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        List<Map<String, String>> rows = new ArrayList<>();
        for (String key : keys) {
            if ("id".equals(key)) {
                continue;
            }
            String from = display(before.get(key));
            String to = display(after.get(key));
            if (Objects.equals(from, to)) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("field", humanize(key));
            row.put("from", from);
            row.put("to", to);
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> toMap(SiteSettingDTO dto) {
        if (dto == null) {
            return Map.of();
        }
        return objectMapper.convertValue(dto, MAP_TYPE);
    }

    private String resolveRecipient() {
        String configured = applicationProperties.getMail().getConfigChangeTo();
        if (configured != null && configured.isBlank()) {
            return "";
        }
        return StringUtils.defaultIfBlank(StringUtils.trim(configured), DEFAULT_RECIPIENT);
    }

    private void sendHtml(String to, String subject, String content) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            message.setTo(to);
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, true);
            javaMailSender.send(mimeMessage);
            LOG.info("Sent admin configuration change email to '{}'", to);
        } catch (MailException | MessagingException e) {
            LOG.warn("Admin configuration email could not be sent to '{}'", to, e);
        }
    }

    static String display(Object value) {
        if (value == null) {
            return "(empty)";
        }
        String text;
        if (value instanceof BigDecimal decimal) {
            text = decimal.stripTrailingZeros().toPlainString();
        } else if (value instanceof Number number) {
            text = new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        } else {
            text = String.valueOf(value).trim();
        }
        if (text.isEmpty()) {
            return "(empty)";
        }
        if (text.length() > MAX_VALUE_LENGTH) {
            return text.substring(0, MAX_VALUE_LENGTH - 3) + "...";
        }
        return text;
    }

    static String humanize(String key) {
        if (StringUtils.isBlank(key)) {
            return key;
        }
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
