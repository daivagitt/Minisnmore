package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validates local size-callout writes. Notes are not part of the Zoho contract. */
final class ProductSizeNotesValidation {
    static final int MAX_NOTE_LEN = 120;
    static final int MAX_NOTES = 40;
    private static final ObjectMapper JSON = new ObjectMapper();

    private ProductSizeNotesValidation() {}

    static void validate(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode root = JSON.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("notes must be a JSON object of size → text");
            }
            if (root.size() > MAX_NOTES) {
                throw new IllegalArgumentException("too many size notes");
            }
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String key = entry.getKey() == null ? "" : entry.getKey().trim();
                if (key.isEmpty()) {
                    throw new IllegalArgumentException("empty size label");
                }
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    continue;
                }
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("note for " + key + " must be text");
                }
                if (value.asText().trim().length() > MAX_NOTE_LEN) {
                    throw new IllegalArgumentException("note for " + key + " is too long");
                }
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid size notes: " + e.getMessage(), e);
        }
    }
}
