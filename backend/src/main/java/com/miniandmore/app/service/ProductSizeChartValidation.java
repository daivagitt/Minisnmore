package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validates local chart writes only. Charts are deliberately not part of the Zoho contract. */
final class ProductSizeChartValidation {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SECTIONS = "sections";
    private static final String BANDS = "bands";
    private static final String ROWS = "rows";
    private static final String LABEL = "label";
    private static final Pattern NUMBER = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:cm|in(?:ch(?:es)?)?|[\"″])?$", Pattern.CASE_INSENSITIVE);
    @SuppressWarnings("java:S5843")
    private static final Pattern RANGE = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:[-–—]|to)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:cm|in(?:ch(?:es)?)?|[\"″])?$", Pattern.CASE_INSENSITIVE);

    private ProductSizeChartValidation() {}

    static void validate(String json) {
        if (json == null || json.isBlank()) return;
        try {
            JsonNode chart = JSON.readTree(json);
            if (chart == null || !chart.isObject() || !chart.path(SECTIONS).isArray() || chart.path(SECTIONS).isEmpty()) {
                throw new IllegalArgumentException("Chart needs sections");
            }
            boolean numeric = false;
            for (JsonNode section : chart.path(SECTIONS)) {
                if (!section.path("title").isTextual() || section.path("title").asText().isBlank() ||
                    !section.path(BANDS).isArray() || section.path(BANDS).isEmpty() ||
                    !section.path(ROWS).isArray() || section.path(ROWS).isEmpty()) {
                    throw new IllegalArgumentException("Every section needs a title, size bands, and rows");
                }
                Set<String> bands = new HashSet<>();
                for (JsonNode band : section.path(BANDS)) {
                    if (!band.isTextual() || band.asText().isBlank() || !bands.add(normalizeBand(band.asText()))) {
                        throw new IllegalArgumentException("Empty or duplicate size band");
                    }
                }
                Set<String> labels = new HashSet<>();
                for (JsonNode row : section.path(ROWS)) {
                    if (!row.path(LABEL).isTextual() || row.path(LABEL).asText().isBlank() ||
                        !labels.add(row.path(LABEL).asText().trim().toLowerCase(Locale.ROOT)) || !row.path("values").isObject()) {
                        throw new IllegalArgumentException("Invalid or duplicate measurement row");
                    }
                    for (JsonNode band : section.path(BANDS)) {
                        JsonNode value = row.path("values").path(band.asText());
                        if (value.isMissingNode() || value.isNull()) continue;
                        if (value.isNumber()) {
                            if (!Double.isFinite(value.asDouble()) || value.decimalValue().compareTo(BigDecimal.ZERO) < 0) {
                                throw new IllegalArgumentException("Measurement must be finite and non-negative");
                            }
                            numeric = true;
                        } else if (value.isTextual()) {
                            String text = value.asText().trim();
                            if (text.matches("(?i)^(?:|[-–—]|n/?a)$")) continue;
                            var range = RANGE.matcher(text);
                            var number = NUMBER.matcher(text);
                            if (number.matches() && Double.isFinite(Double.parseDouble(number.group(1)))) {
                                numeric = true;
                            } else if (range.matches() && Double.isFinite(Double.parseDouble(range.group(1))) &&
                                Double.isFinite(Double.parseDouble(range.group(2)))) {
                                numeric = true;
                            } else {
                                throw new IllegalArgumentException("Invalid measurement number or range");
                            }
                        } else {
                            throw new IllegalArgumentException("Measurement must be a number or text, not a boolean/object/array");
                        }
                    }
                }
            }
            if (!numeric) throw new IllegalArgumentException("Chart needs at least one numeric measurement");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid size chart: " + e.getMessage(), e);
        }
    }

    private static String normalizeBand(String band) {
        String key = band.trim().toUpperCase(Locale.ROOT).replace('–', '-').replace('—', '-')
            .replaceAll("\\s+", "").replaceAll("MONTHS?", "M").replaceAll("MOS?", "M");
        return key.matches("^(ONE(?:SIZE)?|COMMON(?:SIZE)?|SINGLE(?:SIZE)?|OS)$") ? "__COMMON__" : key;
    }
}
