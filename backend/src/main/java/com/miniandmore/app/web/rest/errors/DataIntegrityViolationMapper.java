package com.miniandmore.app.web.rest.errors;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * Turns database integrity errors into user-facing messages (foreign keys, unique constraints).
 */
public final class DataIntegrityViolationMapper {

    private static final String TITLE_REFERENTIAL = "Linked records exist";
    private static final String TITLE_DUPLICATE = "Duplicate value";

    private static final String GENERIC_REFERENTIAL =
        "This record cannot be removed or updated because other records still depend on it. Update or remove those linked records first, then try again.";

    private static final String GENERIC_DUPLICATE = "This value is already in use. Please choose a different one.";

    private static final String TITLE_INVALID = "Invalid data";

    private static final String GENERIC_NOT_NULL =
        "A required field was missing. Check all required fields and try again.";

    private static final String GENERIC_TOO_LONG =
        "One of the values is too long for its field. Shorten the text and try again.";

    private static final String HOME_SECTION_TOO_LONG =
        "Title (max 180 characters), subtitle (max 260), or section key (max 120) is too long. Shorten the text and try again.";

    private static final Pattern CONSTRAINT_NAME = Pattern.compile(
        "constraint\\s+[\"`\\[]?([\\w]+)[\"`\\]]?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, String> FK_MESSAGES = Map.ofEntries(
        Map.entry(
            "fk_product__category_id",
            "One or more products still use this category. Reassign or remove those products, then try again."
        ),
        Map.entry(
            "fk_order_item__order_id",
            "This order still has line items. Remove those items before deleting the order."
        ),
        Map.entry(
            "fk_customer_order__user_id",
            "This account still has orders and cannot be removed until those orders are handled."
        ),
        Map.entry(
            "fk_customer_profile__user_id",
            "This account still has a customer profile linked to it."
        ),
        Map.entry("fk_user_id", "This user is still linked to other records (roles or permissions)."),
        Map.entry("fk_authority_name", "This role is still assigned to one or more users.")
    );

    private static final Map<String, String> UNIQUE_MESSAGES = Map.ofEntries(
        Map.entry("ux_category__slug", "A category with this URL slug already exists. Choose a different slug."),
        Map.entry("ux_product__slug", "A product with this URL slug already exists. Choose a different slug."),
        Map.entry("ux_product__sku", "This SKU is already used by another product."),
        Map.entry("ux_product__zoho_item_id", "This Zoho item is already linked to another product."),
        Map.entry("ux_blog_post__slug", "A blog post with this URL slug already exists. Choose a different slug."),
        Map.entry("ux_coupon__code", "This coupon code is already in use."),
        Map.entry("ux_customer_order__order_number", "This order number already exists."),
        Map.entry("ux_customer_order__zoho_sales_order_id", "This Zoho sales order is already linked to another order."),
        Map.entry("ux_customer_profile__user_id", "This customer already has a profile."),
        Map.entry(
            "ux_home_section__section_key",
            "A home section with this key already exists. Refresh the page and retry; if it persists, keep one section per key."
        ),
        Map.entry("ux_page_visibility__page_key", "A page visibility entry with this key already exists."),
        Map.entry("ux_policy__slug", "A policy page with this URL slug already exists."),
        Map.entry("ux_user_login", "This login name is already taken."),
        Map.entry("ux_user_email", "This email address is already registered.")
    );

    private DataIntegrityViolationMapper() {}

    public static Optional<ViolationIssue> resolve(Throwable throwable) {
        if (throwable == null) {
            return Optional.empty();
        }
        String combined = collectMessages(throwable);
        if (combined.isBlank()) {
            return Optional.empty();
        }
        String lower = combined.toLowerCase(Locale.ROOT);
        if (isReferentialViolation(lower)) {
            return Optional.of(
                new ViolationIssue(
                    TITLE_REFERENTIAL,
                    messageForConstraint(extractConstraintName(combined, FK_MESSAGES), FK_MESSAGES, GENERIC_REFERENTIAL),
                    HttpStatus.CONFLICT,
                    ErrorConstants.ERR_REFERENTIAL_INTEGRITY
                )
            );
        }
        if (isDuplicateViolation(lower)) {
            return Optional.of(
                new ViolationIssue(
                    TITLE_DUPLICATE,
                    messageForConstraint(extractConstraintName(combined, UNIQUE_MESSAGES), UNIQUE_MESSAGES, GENERIC_DUPLICATE),
                    HttpStatus.CONFLICT,
                    ErrorConstants.ERR_DUPLICATE_KEY
                )
            );
        }
        if (isNotNullViolation(lower)) {
            return Optional.of(
                new ViolationIssue(TITLE_INVALID, GENERIC_NOT_NULL, HttpStatus.CONFLICT, ErrorConstants.ERR_VALIDATION)
            );
        }
        if (isValueTooLongViolation(lower)) {
            return Optional.of(
                new ViolationIssue(
                    TITLE_INVALID,
                    detailForValueTooLong(lower),
                    HttpStatus.CONFLICT,
                    ErrorConstants.ERR_VALIDATION
                )
            );
        }
        return Optional.empty();
    }

    private static String collectMessages(Throwable throwable) {
        var builder = new StringBuilder();
        var current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(' ');
            }
            appendSqlExceptionMessages(current, builder);
            var className = current.getClass().getName();
            if (className.contains("ConstraintViolationException")) {
                builder.append("constraint violation ");
            }
            current = current.getCause();
        }
        return builder.toString().trim();
    }

    private static void appendSqlExceptionMessages(Throwable throwable, StringBuilder builder) {
        if (!(throwable instanceof SQLException sql)) {
            return;
        }
        var chained = sql;
        while (chained != null) {
            if (chained.getSQLState() != null) {
                builder.append(chained.getSQLState()).append(' ');
            }
            if (chained.getMessage() != null) {
                builder.append(chained.getMessage()).append(' ');
            }
            chained = chained.getNextException();
        }
    }

    private static boolean isReferentialViolation(String lower) {
        return (
            FK_MESSAGES.keySet().stream().anyMatch(lower::contains) ||
            lower.contains("foreign key") ||
            lower.contains("referential integrity") ||
            lower.contains("violates foreign key constraint") ||
            lower.contains("still referenced") ||
            lower.contains("delete restrict") ||
            lower.contains("23503")
        );
    }

    private static boolean isDuplicateViolation(String lower) {
        return (
            UNIQUE_MESSAGES.keySet().stream().anyMatch(lower::contains) ||
            lower.contains("unique constraint") ||
            lower.contains("duplicate key") ||
            lower.contains("unique index") ||
            lower.contains("already exists") ||
            lower.contains("23505") ||
            lower.contains("duplicate entry")
        );
    }

    private static boolean isNotNullViolation(String lower) {
        return (
            lower.contains("23502") ||
            lower.contains("not-null constraint") ||
            lower.contains("null value in column") ||
            lower.contains("cannot be null") ||
            lower.contains("may not be null")
        );
    }

    private static boolean isValueTooLongViolation(String lower) {
        return (
            lower.contains("22001") ||
            lower.contains("value too long") ||
            lower.contains("string data, right truncated") ||
            lower.contains("data too long for column") ||
            lower.contains("length must be between")
        );
    }

    private static String detailForValueTooLong(String lower) {
        if (
            lower.contains("home_section") ||
            lower.contains("section_key") ||
            lower.contains("character varying(180)") ||
            lower.contains("character varying(260)") ||
            lower.contains("varchar(180)") ||
            lower.contains("varchar(260)")
        ) {
            return HOME_SECTION_TOO_LONG;
        }
        return GENERIC_TOO_LONG;
    }

    private static String extractConstraintName(String combined, Map<String, String> known) {
        String lower = combined.toLowerCase(Locale.ROOT);
        for (String key : known.keySet()) {
            if (lower.contains(key)) {
                return key;
            }
        }
        var matcher = CONSTRAINT_NAME.matcher(combined);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static String messageForConstraint(String constraint, Map<String, String> known, String fallback) {
        if (!constraint.isBlank()) {
            var exact = known.get(constraint);
            if (exact != null) {
                return exact;
            }
            for (var entry : known.entrySet()) {
                if (constraint.contains(entry.getKey()) || entry.getKey().contains(constraint)) {
                    return entry.getValue();
                }
            }
        }
        return fallback;
    }

    public record ViolationIssue(String title, String detail, HttpStatus status, String messageKey) {}

    public static boolean applies(Throwable throwable) {
        if (throwable instanceof DataIntegrityViolationException) {
            return true;
        }
        return resolve(throwable).isPresent();
    }
}
