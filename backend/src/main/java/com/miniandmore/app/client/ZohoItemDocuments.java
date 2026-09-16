package com.miniandmore.app.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Extracts Zoho Books item document identifiers from API responses. Plural-gallery delete falls back
 * to {@code image_document_id} when {@code documents} is empty. Pull-import always includes that
 * dedicated front image, even when leftover gallery {@code documents} remain after a Zoho replace.
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
            List<OrderedDocument> ordered = new ArrayList<>();
            int index = 0;
            for (Object entry : documents) {
                if (entry instanceof Map<?, ?> document && document.get("document_id") != null) {
                    String id = document.get("document_id").toString().trim();
                    if (!id.isEmpty()) {
                        ordered.add(new OrderedDocument(id, attachmentOrder(document), index));
                    }
                }
                index++;
            }
            ordered.sort(
                Comparator.comparing(
                    (OrderedDocument document) -> document.order() == null ? Integer.MAX_VALUE : document.order()
                ).thenComparingInt(OrderedDocument::index)
            );
            for (OrderedDocument document : ordered) {
                ids.add(document.id());
            }
        }
        if (includeStandaloneImageDocumentId) {
            String frontId = standaloneImageDocumentId(item);
            if (frontId != null && !ids.contains(frontId)) {
                if (ids.isEmpty()) {
                    ids.add(frontId);
                } else {
                    LinkedHashSet<String> withFront = new LinkedHashSet<>();
                    withFront.add(frontId);
                    withFront.addAll(ids);
                    return new ArrayList<>(withFront);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private static String standaloneImageDocumentId(Map<?, ?> item) {
        Object imageDocumentId = item.get("image_document_id");
        if (imageDocumentId == null) {
            return null;
        }
        String id = imageDocumentId.toString().trim();
        if (StringUtils.isBlank(id) || "0".equals(id) || "null".equalsIgnoreCase(id)) {
            return null;
        }
        return id;
    }

    private static Integer attachmentOrder(Map<?, ?> document) {
        Object raw = document.get("attachment_order");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().trim();
        return "0".equals(text) || "null".equalsIgnoreCase(text) ? "" : text;
    }

    private record OrderedDocument(String id, Integer order, int index) {}
}
