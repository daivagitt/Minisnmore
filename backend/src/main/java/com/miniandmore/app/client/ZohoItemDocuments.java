package com.miniandmore.app.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Extracts Zoho Books item document identifiers from API responses. Plural-gallery delete falls back
 * to {@code image_document_id} when {@code documents} is empty; pull-import uses the same fallback.
 */
public final class ZohoItemDocuments {

    private ZohoItemDocuments() {}

    public static List<String> documentIdsFromItemResponse(Map<String, Object> response) {
        return documentIdsFromItem(unwrapItem(response), false);
    }

    /**
     * Gallery documents visible before the dedicated front image is deleted. When Zoho includes
     * that front image in {@code documents}, exclude it so the plural delete only receives ids that
     * still exist after {@code DELETE /image}.
     */
    public static List<String> pluralDocumentIdsBeforeFrontImageDelete(Map<String, Object> response) {
        Map<?, ?> item = unwrapItem(response);
        List<String> ids = documentIdsFromItem(item, false);
        String frontDocumentId = text(item.get("image_document_id"));
        if (StringUtils.isNotBlank(frontDocumentId)) {
            ids.remove(frontDocumentId);
        }
        return ids;
    }

    public static boolean exposesDocuments(Map<String, Object> response) {
        return unwrapItem(response).get("documents") instanceof List<?>;
    }

    /** After the single front image is cleared, remaining gallery ids may only be in {@code documents} or {@code image_document_id}. */
    public static List<String> documentIdsRemainingAfterFrontImageDelete(Map<String, Object> itemResponse) {
        return documentIdsFromItem(unwrapItem(itemResponse), true);
    }

    public static List<String> documentIdsForImport(Map<String, Object> item) {
        return documentIdsFromItem(item, true);
    }

    private static Map<?, ?> unwrapItem(Map<String, Object> responseOrItem) {
        if (responseOrItem == null || responseOrItem.isEmpty()) {
            return Map.of();
        }
        Object itemValue = responseOrItem.get("item");
        return itemValue instanceof Map<?, ?> nested ? nested : responseOrItem;
    }

    private static List<String> documentIdsFromItem(Map<?, ?> item, boolean includeStandaloneImageDocumentId) {
        if (item == null || item.isEmpty()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        Object documentsValue = item.get("documents");
        if (documentsValue instanceof List<?> documents) {
            for (Object entry : documents) {
                if (entry instanceof Map<?, ?> document && document.get("document_id") != null) {
                    String id = document.get("document_id").toString().trim();
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
            }
        }
        if (ids.isEmpty() && includeStandaloneImageDocumentId) {
            Object imageDocumentId = item.get("image_document_id");
            if (imageDocumentId != null) {
                String id = imageDocumentId.toString().trim();
                if (
                    StringUtils.isNotBlank(id) &&
                    !"0".equals(id) &&
                    !"null".equalsIgnoreCase(id)
                ) {
                    ids.add(id);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().trim();
        return "0".equals(text) || "null".equalsIgnoreCase(text) ? "" : text;
    }
}
