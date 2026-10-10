package com.miniandmore.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.security.SecurityUtils;
import com.miniandmore.app.service.dto.CouponDTO;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
 * Notifies the configured inbox when admin configuration changes
 * (site settings, store discount, coupons, Zoho settings).
 */
@Service
public class AdminConfigChangeMailService {

    static final String DEFAULT_RECIPIENT = "info@stoltglobal.com,saikedar2003@gmail.com";
    static final String AREA_SITE_SETTINGS = "Site settings";
    static final String AREA_COUPONS = "Coupons";
    static final String AREA_ZOHO_SETTINGS = "Zoho settings";

    private static final Logger LOG = LoggerFactory.getLogger(AdminConfigChangeMailService.class);
    private static final String VALUE_EMPTY = "(empty)";
    private static final String VALUE_SET = "(set)";
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
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

    public void notifySiteSettingsChangedAfterCommit(SiteSettingDTO previous, SiteSettingDTO current) {
        AfterCommit.run(() -> notifySiteSettingsChanged(previous, current));
    }

    public void notifyCouponChangedAfterCommit(CouponDTO previous, CouponDTO current) {
        AfterCommit.run(() -> notifyCouponChanged(previous, current));
    }

    public void notifyZohoSettingsChangedAfterCommit(ZohoSetting previous, ZohoSetting current) {
        AfterCommit.run(() -> notifyZohoSettingsChanged(previous, current));
    }

    public void notifySiteSettingsChanged(SiteSettingDTO previous, SiteSettingDTO current) {
        notifyConfigChanged(AREA_SITE_SETTINGS, previous, current);
    }

    public void notifyCouponChanged(CouponDTO previous, CouponDTO current) {
        notifyConfigChanged(AREA_COUPONS, previous, current);
    }

    public void notifyZohoSettingsChanged(ZohoSetting previous, ZohoSetting current) {
        Map.Entry<Map<String, Object>, Map<String, Object>> snapshots = zohoEmailSnapshots(previous, current);
        notifyConfigChanged(AREA_ZOHO_SETTINGS, snapshots.getKey(), snapshots.getValue());
    }

    private void notifyConfigChanged(String area, Object previous, Object current) {
        if (previous == null && current == null) {
            return;
        }
        String[] to = resolveRecipients();
        if (to.length == 0) {
            LOG.debug("Skipping admin configuration email; recipient is blank");
            return;
        }
        try {
            boolean deleted = previous != null && current == null;
            List<Map<String, String>> changes = diff(previous, current);
            Locale locale = DEFAULT_LOCALE;
            Context context = new Context(locale);
            String changedBy = SecurityUtils.getCurrentUserLogin().filter(StringUtils::isNotBlank).orElse("unknown");
            String section = StringUtils.defaultIfBlank(area, "Configuration");
            String actionKey = deleted ? "deleted" : "saved";
            String subject = messageSource.getMessage(
                deleted ? "email.admin.config.title.deleted" : "email.admin.config.title",
                new Object[] { section },
                locale
            );
            context.setVariable("area", section);
            context.setVariable("subject", subject);
            context.setVariable("changedBy", changedBy);
            context.setVariable("changedAt", MailTimestamps.now());
            context.setVariable("changes", changes);
            context.setVariable("hasChanges", !changes.isEmpty());
            context.setVariable(
                "intro",
                messageSource.getMessage("email.admin.config.intro." + actionKey, new Object[] { section }, locale)
            );
            context.setVariable(
                "preheader",
                messageSource.getMessage("email.admin.config.preheader." + actionKey, new Object[] { section }, locale)
            );
            String content = templateEngine.process("mail/adminConfigChangeEmail", context);
            sendHtml(to, subject, content);
        } catch (RuntimeException ex) {
            LOG.warn("Admin configuration email could not be prepared", ex);
        }
    }

    List<Map<String, String>> diff(Object previous, Object current) {
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

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return copy;
        }
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private String[] resolveRecipients() {
        String configured = applicationProperties.getMail().getConfigChangeTo();
        if (configured != null && configured.isBlank()) {
            return new String[0];
        }
        return parseRecipients(StringUtils.defaultIfBlank(StringUtils.trim(configured), DEFAULT_RECIPIENT));
    }

    static String[] parseRecipients(String raw) {
        if (StringUtils.isBlank(raw)) {
            return new String[0];
        }
        return java.util.Arrays.stream(raw.split("[,;]"))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toArray(String[]::new);
    }

    private void sendHtml(String[] to, String subject, String content) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            message.setTo(to);
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, true);
            MailTimestamps.stampSentDate(mimeMessage, LOG);
            javaMailSender.send(mimeMessage);
            LOG.info("Sent admin configuration change email to {}", String.join(", ", to));
        } catch (MailException | MessagingException e) {
            LOG.warn("Admin configuration email could not be sent to {}", String.join(", ", to), e);
        }
    }

    static String display(Object value) {
        if (value == null) {
            return VALUE_EMPTY;
        }
        String formattedInstant = MailTimestamps.formatIfInstant(value);
        String text;
        if (formattedInstant != null) {
            text = formattedInstant;
        } else if (value instanceof BigDecimal decimal) {
            text = decimal.stripTrailingZeros().toPlainString();
        } else if (value instanceof Number number) {
            text = new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        } else if (value instanceof Enum<?> enumerated) {
            text = enumerated.name();
        } else {
            text = String.valueOf(value).trim();
        }
        if (text.isEmpty()) {
            return VALUE_EMPTY;
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

    /**
     * Builds before/after Zoho snapshots safe for email (secrets never leave as plaintext).
     */
    static Map.Entry<Map<String, Object>, Map<String, Object>> zohoEmailSnapshots(ZohoSetting previous, ZohoSetting current) {
        LinkedHashMap<String, Object> before = new LinkedHashMap<>();
        LinkedHashMap<String, Object> after = new LinkedHashMap<>();
        before.put("orgId", previous != null ? previous.getOrgId() : null);
        after.put("orgId", current != null ? current.getOrgId() : null);
        before.put("clientId", previous != null ? previous.getClientId() : null);
        after.put("clientId", current != null ? current.getClientId() : null);
        putSecretDiff(
            before,
            after,
            "clientSecret",
            previous != null ? previous.getClientSecret() : null,
            current != null ? current.getClientSecret() : null
        );
        putSecretDiff(
            before,
            after,
            "refreshToken",
            previous != null ? previous.getRefreshToken() : null,
            current != null ? current.getRefreshToken() : null
        );
        before.put("dataCenter", previous != null ? previous.getDataCenter() : null);
        after.put("dataCenter", current != null ? current.getDataCenter() : null);
        before.put("connected", previous != null ? previous.getConnected() : null);
        after.put("connected", current != null ? current.getConnected() : null);
        before.put(
            "pullProductsAutoCreateCategory",
            previous != null ? previous.getPullProductsAutoCreateCategory() : null
        );
        after.put(
            "pullProductsAutoCreateCategory",
            current != null ? current.getPullProductsAutoCreateCategory() : null
        );
        return Map.entry(before, after);
    }

    private static void putSecretDiff(
        Map<String, Object> before,
        Map<String, Object> after,
        String key,
        String previousSecret,
        String currentSecret
    ) {
        boolean wasSet = StringUtils.isNotBlank(previousSecret);
        boolean isSet = StringUtils.isNotBlank(currentSecret);
        if (!wasSet && !isSet) {
            before.put(key, VALUE_EMPTY);
            after.put(key, VALUE_EMPTY);
            return;
        }
        if (!wasSet) {
            before.put(key, VALUE_EMPTY);
            after.put(key, VALUE_SET);
            return;
        }
        if (!isSet) {
            before.put(key, VALUE_SET);
            after.put(key, VALUE_EMPTY);
            return;
        }
        if (Objects.equals(previousSecret, currentSecret)) {
            before.put(key, VALUE_SET);
            after.put(key, VALUE_SET);
            return;
        }
        before.put(key, VALUE_SET);
        after.put(key, "(updated)");
    }
}
