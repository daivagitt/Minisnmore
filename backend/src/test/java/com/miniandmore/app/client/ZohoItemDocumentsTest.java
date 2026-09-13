package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZohoItemDocumentsTest {

    @Test
    void documentIdsFromItemResponse_readsNestedItemDocumentsAndRemovesDuplicates() {
        Map<String, Object> response = Map.of(
            "item",
            Map.of(
                "documents",
                List.of(
                    Map.of("document_id", "doc-1"),
                    Map.of("document_id", 2002L),
                    Map.of("document_id", "doc-1"),
                    Map.of("file_name", "missing-id.jpg")
                )
            )
        );

        assertThat(ZohoItemDocuments.documentIdsFromItemResponse(response)).containsExactly("doc-1", "2002");
    }

    @Test
    void documentIdsForImport_fallsBackToImageDocumentIdWhenDocumentsEmpty() {
        Map<String, Object> item = Map.of("image_document_id", "front-99");

        assertThat(ZohoItemDocuments.documentIdsForImport(item)).containsExactly("front-99");
    }

    @Test
    void documentIdsForImport_includesStandaloneFrontImageWhenMissingFromDocuments() {
        Map<String, Object> item = Map.of(
            "image_document_id",
            "front-99",
            "documents",
            List.of(Map.of("document_id", "doc-gallery"))
        );

        assertThat(ZohoItemDocuments.documentIdsForImport(item)).containsExactly("front-99", "doc-gallery");
    }

    @Test
    void documentIdsForImport_doesNotDuplicateFrontAlreadyListedInDocuments() {
        Map<String, Object> item = Map.of(
            "image_document_id",
            "front-1",
            "documents",
            List.of(
                Map.of("document_id", "front-1", "attachment_order", 1),
                Map.of("document_id", "gallery-2", "attachment_order", 2)
            )
        );

        assertThat(ZohoItemDocuments.documentIdsForImport(item)).containsExactly("front-1", "gallery-2");
    }

    @Test
    void documentIdsRemainingAfterFrontImageDelete_fallsBackToImageDocumentId() {
        Map<String, Object> response = Map.of("item", Map.of("image_document_id", "gallery-only-7"));

        assertThat(ZohoItemDocuments.documentIdsRemainingAfterFrontImageDelete(response)).containsExactly("gallery-only-7");
    }

    @Test
    void pluralDocumentIdsBeforeFrontImageDelete_excludesDedicatedFrontDocument() {
        Map<String, Object> response = Map.of(
            "item",
            Map.of(
                "image_document_id",
                "front-1",
                "documents",
                List.of(Map.of("document_id", "front-1"), Map.of("document_id", "gallery-2"))
            )
        );

        assertThat(ZohoItemDocuments.exposesDocuments(response)).isTrue();
        assertThat(ZohoItemDocuments.pluralDocumentIdsBeforeFrontImageDelete(response)).containsExactly("gallery-2");
    }

    @Test
    void documentIdsForImport_sortsByAttachmentOrderWhenListIsReversed() {
        Map<String, Object> item = Map.of(
            "documents",
            List.of(
                Map.of("document_id", "doc-3", "attachment_order", 3),
                Map.of("document_id", "doc-1", "attachment_order", 1),
                Map.of("document_id", "doc-2", "attachment_order", 2)
            )
        );

        assertThat(ZohoItemDocuments.documentIdsForImport(item)).containsExactly("doc-1", "doc-2", "doc-3");
    }

    @Test
    void documentIdsForImport_keepsEncounterOrderWhenAttachmentOrderMissing() {
        Map<String, Object> item = Map.of(
            "documents",
            List.of(Map.of("document_id", "later"), Map.of("document_id", "earlier"))
        );

        assertThat(ZohoItemDocuments.documentIdsForImport(item)).containsExactly("later", "earlier");
    }
}
