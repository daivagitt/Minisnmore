package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.miniandmore.app.client.ZohoBooksClient.BinaryDownload;
import com.miniandmore.app.client.ZohoBooksClient.ImageUpload;
import com.miniandmore.app.service.ZohoOAuthService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.client.ExpectedCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ZohoBooksClientTest {

    @Mock
    private ZohoOAuthService oauthService;

    @Mock
    private HttpClient httpClient;

    private MockRestServiceServer server;
    private ZohoBooksClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ZohoBooksClient(oauthService, builder);
        ReflectionTestUtils.setField(client, "httpClient", httpClient);
        lenient().when(oauthService.dataCenter()).thenReturn("in");
        lenient().when(oauthService.apiHost("in")).thenReturn("https://www.zohoapis.in");
        lenient().when(oauthService.orgId()).thenReturn("org-1");
        lenient().when(oauthService.accessToken()).thenReturn("token-1");
    }

    @Test
    void fullItemList_requestsOnlyActiveItems() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/books/v3/items?organization_id=org-1&page=1&per_page=200&filter_by=Status.Active"
                )
            )
            .andRespond(withSuccess("{\"items\":[{\"sku\":\"ACTIVE\"}],\"page_context\":{\"has_more_page\":false}}", MediaType.APPLICATION_JSON));

        assertThat(client.listItems(1)).extracting(item -> item.get("sku")).containsExactly("ACTIVE");
        server.verify();
    }

    @Test
    void incrementalItemList_requestsAllStatusesWithoutLastModifiedFilter() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/books/v3/items?organization_id=org-1&page=2&per_page=200&filter_by=Status.All"
                )
            )
            .andRespond(withSuccess("{\"items\":[{\"sku\":\"INACTIVE\",\"status\":\"inactive\"}],\"page_context\":{\"has_more_page\":true}}", MediaType.APPLICATION_JSON));

        assertThat(client.listAllStatusItems(2))
            .singleElement()
            .satisfies(item -> assertThat(item.get("status")).isEqualTo("inactive"));
        server.verify();
    }

    @Test
    void itemListRetainsAuthoritativeLastPage() {
        server.expect(requestTo("https://www.zohoapis.in/books/v3/items?organization_id=org-1&page=1&per_page=200&filter_by=Status.Active"))
            .andRespond(withSuccess("{\"code\":0,\"items\":[{\"item_id\":\"1\",\"sku\":\"SKU\"}],\"page_context\":{\"page\":1,\"has_more_page\":false}}", MediaType.APPLICATION_JSON));
        assertThat(((ZohoBooksClient.ItemPage) client.listItems(1)).hasMorePage()).isFalse();
        server.verify();
    }

    @Test
    void getItemGroup_returnsAllVariantsFromBooksEndpoint() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/itemgroups/3624052000000394593?organization_id=org-1"))
            .andRespond(
                withSuccess(
                    "{\"code\":0,\"group_id\":\"3624052000000394593\",\"group_name\":\"Jabla\",\"items\":[{\"item_id\":\"1\",\"sku\":\"JABLA-S\"},{\"item_id\":\"2\",\"sku\":\"JABLA-M\"}]}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.getItemGroup("3624052000000394593").get("items"))
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
            .hasSize(2);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"code\":0,\"page_context\":{\"has_more_page\":true}}",
        "{\"items\":null}", "{\"items\":{}}", "{\"items\":[null]}", "{\"items\":[1]}",
        "{\"items\":[]}", "{\"code\":57,\"items\":[],\"page_context\":{\"has_more_page\":false}}",
        "{\"items\":[],\"page_context\":{\"has_more_page\":true}}",
        "{\"items\":[],\"page_context\":{\"has_more_page\":\"false\"}}",
        "{\"items\":[],\"page_context\":{\"page\":2,\"has_more_page\":false}}"
    })
    void malformedItemPagesFailClosed(String body) {
        server.expect(requestTo("https://www.zohoapis.in/books/v3/items?organization_id=org-1&page=1&per_page=200&filter_by=Status.Active"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.listItems(1)).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"items\":[{\"sku\":\"SKU-10\",\"item_id\":\"wrong\"}],\"page_context\":{\"has_more_page\":false}}",
        "{\"items\":[{\"sku\":\"SKU-1\",\"item_id\":\"1\"},{\"sku\":\"SKU-1\",\"item_id\":\"2\"}],\"page_context\":{\"has_more_page\":false}}",
        "{\"items\":[{\"sku\":\"SKU-1\"}],\"page_context\":{\"has_more_page\":false}}",
        "{\"items\":[],\"page_context\":{\"has_more_page\":true}}", "{}"
    })
    void skuLookupRejectsUnsafeMatches(String body) {
        server.expect(requestTo("https://www.zohoapis.in/books/v3/items?organization_id=org-1&sku=SKU-1&filter_by=Status.All"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.findItemBySku("SKU-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void skuLookupSelectsExactMatchInsteadOfFirstRow() {
        server.expect(requestTo("https://www.zohoapis.in/books/v3/items?organization_id=org-1&sku=SKU-1&filter_by=Status.All"))
            .andRespond(withSuccess("{\"items\":[{\"sku\":\"SKU-10\",\"item_id\":\"wrong\"},{\"sku\":\"SKU-1\",\"item_id\":\"right\"}],\"page_context\":{\"has_more_page\":false}}", MediaType.APPLICATION_JSON));
        assertThat(client.findItemBySku("SKU-1")).containsEntry("item_id", "right");
    }

    @Test
    void itemUpdateRejectsBusinessErrorEvenWithHttp200() {
        server.expect(requestTo("https://www.zohoapis.in/books/v3/items/1?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":57,\"message\":\"Not authorized\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.updateItem("1", java.util.Map.of())).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"code\":0}", "{\"item\":null}", "{\"item\":{}}", "{\"item\":{\"item_id\":\"wrong\"}}"})
    void itemDetailRejectsMalformedOrMismatchedIdentity(String body) {
        server.expect(requestTo("https://www.zohoapis.in/books/v3/items/1?organization_id=org-1"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getItem("1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void itemDetailAcceptsNumericIdsFromPublishedZohoResponse() {
        server.expect(requestTo("https://www.zohoapis.in/books/v3/items/45667789900?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"item\":{\"item_id\":45667789900,\"sku\":\"SKU-1\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.getItem("45667789900")).containsKey("item");
    }

    @Test
    void listPackagesForSalesOrder_usesInventoryApiAndMatchesImmutableSalesOrderId() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/inventory/v1/packages?organization_id=org-1&per_page=200&salesorder_number_contains=SO-42"
                )
            )
            .andRespond(
                withSuccess(
                    "{\"packages\":[{\"package_id\":\"pkg-wrong\",\"salesorder_id\":\"so-4\"},{\"package_id\":\"pkg-42\",\"salesorder_id\":\"so-42\"}]}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.listPackagesForSalesOrder("so-42", "SO-42"))
            .singleElement()
            .satisfies(pkg -> assertThat(pkg.get("package_id")).isEqualTo("pkg-42"));
        server.verify();
    }

    @Test
    void findSalesOrderByReferenceNumber_prefersClosedOverConfirmed() {
        server
            .expect(
                requestTo("https://www.zohoapis.in/books/v3/salesorders?organization_id=org-1&reference_number=MNM-DUP-SO")
            )
            .andRespond(
                withSuccess(
                    "{\"salesorders\":[" +
                    "{\"salesorder_id\":\"so-confirmed\",\"reference_number\":\"MNM-DUP-SO\",\"status\":\"confirmed\"}," +
                    "{\"salesorder_id\":\"so-closed\",\"reference_number\":\"MNM-DUP-SO\",\"status\":\"closed\"}" +
                    "]}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.findSalesOrderByReferenceNumber("MNM-DUP-SO"))
            .containsEntry("salesorder_id", "so-closed")
            .containsEntry("status", "closed");
        server.verify();
    }

    @Test
    void findCreditNoteByReferenceNumber_prefersOpenOverDraft() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/creditnotes?organization_id=org-1&reference_number=CNCL-MNM-1"))
            .andRespond(
                withSuccess(
                    "{\"creditnotes\":[" +
                    "{\"creditnote_id\":\"cn-draft\",\"reference_number\":\"CNCL-MNM-1\",\"status\":\"draft\"}," +
                    "{\"creditnote_id\":\"cn-open\",\"reference_number\":\"CNCL-MNM-1\",\"status\":\"open\"}" +
                    "]}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.findCreditNoteByReferenceNumber("CNCL-MNM-1"))
            .containsEntry("creditnote_id", "cn-open")
            .containsEntry("status", "open");
        server.verify();
    }

    @Test
    void listPackagesForSalesOrder_acceptsZohoPublishedPackageaKeyWithoutAnOrderNumber() {
        server
            .expect(requestTo("https://www.zohoapis.in/inventory/v1/packages?organization_id=org-1&per_page=200"))
            .andRespond(
                withSuccess(
                    "{\"packagea\":[{\"package_id\":\"pkg-43\",\"salesorder_id\":\"so-43\"}]}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.listPackagesForSalesOrder("so-43", null))
            .singleElement()
            .satisfies(pkg -> assertThat(pkg.get("package_id")).isEqualTo("pkg-43"));
        server.verify();
    }

    @Test
    void listPackagesForSalesOrder_returnsEmptyForAnUnexpectedResponseShape() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/inventory/v1/packages?organization_id=org-1&per_page=200&salesorder_number_contains=SO-44"
                )
            )
            .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.listPackagesForSalesOrder("so-44", "SO-44")).isEmpty();
        server.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImages_deletesFrontImageThenPluralDocuments() throws Exception {
        HttpResponse<String> frontDeleteResponse = mock(HttpResponse.class);
        when(frontDeleteResponse.statusCode()).thenReturn(204);
        HttpResponse<String> pluralDeleteResponse = mock(HttpResponse.class);
        when(pluralDeleteResponse.statusCode()).thenReturn(200);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/items/item-1/image?organization_id=org-1")) {
                    return frontDeleteResponse;
                }
                if (uri.contains("/items/item-1/images?organization_id=org-1&document_ids=doc-1%2Cdoc-2")) {
                    return pluralDeleteResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/item-1?organization_id=org-1"))
            .andRespond(
                withSuccess(
                    "{\"item\":{\"item_id\":\"item-1\",\"documents\":[{\"document_id\":\"doc-1\"},{\"document_id\":\"doc-2\"}]}}",
                    MediaType.APPLICATION_JSON
                )
            );

        client.deleteItemImages("item-1");

        server.verify();
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImages_fallsBackToImageDocumentIdWhenDocumentsMissing() throws Exception {
        HttpResponse<String> frontDeleteResponse = mock(HttpResponse.class);
        when(frontDeleteResponse.statusCode()).thenReturn(204);
        HttpResponse<String> pluralDeleteResponse = mock(HttpResponse.class);
        when(pluralDeleteResponse.statusCode()).thenReturn(200);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/items/item-2/image?organization_id=org-1")) {
                    return frontDeleteResponse;
                }
                if (uri.contains("/items/item-2/images?organization_id=org-1&document_ids=gallery-only-7")) {
                    return pluralDeleteResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        server
            .expect(ExpectedCount.times(2), requestTo("https://www.zohoapis.in/books/v3/items/item-2?organization_id=org-1"))
            .andRespond(withSuccess("{\"item\":{\"item_id\":\"item-2\",\"image_document_id\":\"gallery-only-7\"}}", MediaType.APPLICATION_JSON));

        client.deleteItemImages("item-2");

        server.verify();
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImages_treatsAlreadyGonePluralDocumentsAsCleared() throws Exception {
        HttpResponse<String> frontDeleteResponse = mock(HttpResponse.class);
        when(frontDeleteResponse.statusCode()).thenReturn(204);
        HttpResponse<String> pluralDeleteResponse = mock(HttpResponse.class);
        when(pluralDeleteResponse.statusCode()).thenReturn(400);
        when(pluralDeleteResponse.body()).thenReturn("{\"code\":57,\"message\":\"The document id does not exist.\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/items/item-4/image?organization_id=org-1")) {
                    return frontDeleteResponse;
                }
                if (uri.contains("/items/item-4/images?organization_id=org-1&document_ids=doc-1%2Cdoc-2")) {
                    return pluralDeleteResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/item-4?organization_id=org-1"))
            .andRespond(
                withSuccess(
                    "{\"item\":{\"item_id\":\"item-4\",\"documents\":[{\"document_id\":\"doc-1\"},{\"document_id\":\"doc-2\"}]}}",
                    MediaType.APPLICATION_JSON
                )
            );

        client.deleteItemImages("item-4");

        server.verify();
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImages_treatsMissingFileResponseAsCleared() throws Exception {
        HttpResponse<String> frontDeleteResponse = mock(HttpResponse.class);
        when(frontDeleteResponse.statusCode()).thenReturn(204);
        HttpResponse<String> pluralDeleteResponse = mock(HttpResponse.class);
        when(pluralDeleteResponse.statusCode()).thenReturn(400);
        when(pluralDeleteResponse.body())
            .thenReturn("{\"code\":33008,\"message\":\"Sorry, this file does not exist.\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/items/item-5/image?organization_id=org-1")) {
                    return frontDeleteResponse;
                }
                if (uri.contains("/items/item-5/images?organization_id=org-1&document_ids=stale-doc")) {
                    return pluralDeleteResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/item-5?organization_id=org-1"))
            .andRespond(
                withSuccess(
                    "{\"item\":{\"item_id\":\"item-5\",\"documents\":[{\"document_id\":\"stale-doc\"}]}}",
                    MediaType.APPLICATION_JSON
                )
            );

        client.deleteItemImages("item-5");

        server.verify();
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void deleteItemImages_doesNotDeleteAnythingWhenPreflightItemFetchFails() throws Exception {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/item-3?organization_id=org-1"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> client.deleteItemImages("item-3"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("fetch failed while clearing images");

        server.verify();
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadItemImage_success_returnsBytesAndContentType() throws Exception {
        byte[] imageBytes = "jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/jpeg")), (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(imageBytes));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Optional<BinaryDownload> result = client.downloadItemImage("item-img-1", 8192);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().bytes()).isEqualTo(imageBytes);
        assertThat(result.orElseThrow().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadItemImage_notFound_returnsEmpty() throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        InputStream body = mock(InputStream.class);
        when(response.body()).thenReturn(body);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThat(client.downloadItemImage("item-img-2", 8192)).isEmpty();
        verify(body).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadItemImage_unauthorized_retriesAfterTokenRefresh() throws Exception {
        byte[] imageBytes = "refreshed-bytes".getBytes(StandardCharsets.UTF_8);

        HttpResponse<InputStream> unauthorized = mock(HttpResponse.class);
        when(unauthorized.statusCode()).thenReturn(401);
        InputStream unauthorizedBody = mock(InputStream.class);
        when(unauthorized.body()).thenReturn(unauthorizedBody);

        HttpResponse<InputStream> success = mock(HttpResponse.class);
        when(success.statusCode()).thenReturn(200);
        when(success.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/png")), (name, value) -> true));
        when(success.body()).thenReturn(new ByteArrayInputStream(imageBytes));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(unauthorized, success);
        when(oauthService.accessToken()).thenReturn("token-1", "token-refreshed");

        Optional<BinaryDownload> result = client.downloadItemImage("item-img-3", 8192);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().bytes()).isEqualTo(imageBytes);
        assertThat(result.orElseThrow().contentType()).isEqualTo("image/png");
        verify(unauthorizedBody).close();
        verify(oauthService, times(3)).accessToken();
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadItemImage_jsonResponse_returnsEmpty() throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(
            HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (name, value) -> true)
        );
        InputStream body = mock(InputStream.class);
        when(response.body()).thenReturn(body);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThat(client.downloadItemImage("item-img-4", 8192)).isEmpty();
        verify(body).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadItemImage_exceedsMaxBytes_throws() throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/jpeg")), (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream("0123456789".getBytes(StandardCharsets.UTF_8)));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> client.downloadItemImage("item-img-5", 5))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("download failed")
            .hasCauseInstanceOf(java.io.IOException.class)
            .cause()
            .hasMessageContaining("exceeds max size (5 bytes)");
    }

    @Test
    void uploadItemImage_rejectsEmptyBytes() {
        assertThatThrownBy(() -> client.uploadItemImage("item-up-1", new byte[0], "photo.jpg", "image/jpeg"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Item image bytes are empty");

        assertThatThrownBy(() -> client.uploadItemImage("item-up-1", null, "photo.jpg", "image/jpeg"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Item image bytes are empty");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadItemImage_success_postsMultipartToImageEndpoint() throws Exception {
        HttpResponse<String> uploadResponse = mock(HttpResponse.class);
        when(uploadResponse.statusCode()).thenReturn(200);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(uploadResponse);

        byte[] imageBytes = "upload-bytes".getBytes(StandardCharsets.UTF_8);
        client.uploadItemImage("item-up-2", imageBytes, "photo.jpg", "image/jpeg");

        HttpRequest request = requestCaptor.getValue();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString()).isEqualTo("https://www.zohoapis.in/books/v3/items/item-up-2/image?organization_id=org-1");
        assertThat(request.headers().firstValue("Authorization")).contains("Zoho-oauthtoken token-1");
        assertThat(request.headers().firstValue("Content-Type").orElse("")).contains("multipart/form-data");
        assertThat(request.headers().firstValue("Content-Type").orElse("")).contains("boundary=");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadItemImages_clearsExistingThenUploadsEachImage() throws Exception {
        HttpResponse<String> frontDeleteResponse = mock(HttpResponse.class);
        when(frontDeleteResponse.statusCode()).thenReturn(204);
        HttpResponse<String> pluralDeleteResponse = mock(HttpResponse.class);
        when(pluralDeleteResponse.statusCode()).thenReturn(200);
        HttpResponse<String> uploadResponse = mock(HttpResponse.class);
        when(uploadResponse.statusCode()).thenReturn(200);

        List<String> uploadUris = new ArrayList<>();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/items/gallery-1/image?organization_id=org-1") && "DELETE".equals(request.method())) {
                    return frontDeleteResponse;
                }
                if (uri.contains("/items/gallery-1/images?organization_id=org-1&document_ids=doc-1%2Cdoc-2")) {
                    return pluralDeleteResponse;
                }
                if (uri.endsWith("/items/gallery-1/images?organization_id=org-1") && "POST".equals(request.method())) {
                    uploadUris.add(uri);
                    return uploadResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + request.method() + " " + uri);
            });

        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/gallery-1?organization_id=org-1"))
            .andRespond(
                withSuccess(
                    "{\"item\":{\"item_id\":\"gallery-1\",\"documents\":[{\"document_id\":\"doc-1\"},{\"document_id\":\"doc-2\"}]}}",
                    MediaType.APPLICATION_JSON
                )
            );

        client.uploadItemImages(
            "gallery-1",
            List.of(
                new ImageUpload("front".getBytes(StandardCharsets.UTF_8), "front.jpg", "image/jpeg"),
                new ImageUpload("rear".getBytes(StandardCharsets.UTF_8), "rear.jpg", "image/jpeg")
            )
        );

        server.verify();
        assertThat(uploadUris).hasSize(2);
        verify(httpClient, times(4)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void listLocations_returnsLocationsFromResponse() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/locations?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"locations\":[{\"location_id\":\"loc-1\",\"location_name\":\"Main\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.listLocations())
            .singleElement()
            .satisfies(row -> assertThat(row.get("location_id")).isEqualTo("loc-1"));
        server.verify();
    }

    @Test
    void listAssetAccounts_returnsChartOfAccounts() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/books/v3/chartofaccounts?organization_id=org-1&filter_by=AccountType.Asset"
                )
            )
            .andRespond(withSuccess("{\"code\":0,\"chartofaccounts\":[{\"account_id\":\"a-1\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.listAssetAccounts())
            .singleElement()
            .satisfies(row -> assertThat(row.get("account_id")).isEqualTo("a-1"));
        server.verify();
    }

    @Test
    void listExpenseAccounts_returnsChartOfAccounts() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/books/v3/chartofaccounts?organization_id=org-1&filter_by=AccountType.Expense"
                )
            )
            .andRespond(withSuccess("{\"code\":0,\"chartofaccounts\":[{\"account_id\":\"e-1\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.listExpenseAccounts())
            .singleElement()
            .satisfies(row -> assertThat(row.get("account_id")).isEqualTo("e-1"));
        server.verify();
    }

    @Test
    void listItemFields_requiresFieldsList() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/settings/fields?organization_id=org-1&entity=item"))
            .andRespond(withSuccess("{\"code\":0,\"fields\":[{\"field_id\":\"f-1\",\"label\":\"Color\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.listItemFields())
            .singleElement()
            .satisfies(row -> assertThat(row.get("field_id")).isEqualTo("f-1"));
        server.verify();
    }

    @Test
    void listItemFields_rejectsMalformedPayload() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/settings/fields?organization_id=org-1&entity=item"))
            .andRespond(withSuccess("{\"code\":0,\"fields\":[1]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.listItemFields()).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void uploadItemImages_rejectsEmptyList() {
        assertThatThrownBy(() -> client.uploadItemImages("item-1", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Item images are empty");
        assertThatThrownBy(() -> client.uploadItemImages("item-1", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Item images are empty");
    }

    @Test
    void createItem_rejectsSkuMismatchInResponse() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items?organization_id=org-1"))
            .andRespond(
                withSuccess(
                    "{\"code\":0,\"item\":{\"item_id\":\"new-1\",\"sku\":\"OTHER-SKU\"}}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThatThrownBy(() -> client.createItem(Map.of("sku", "SKU-1", "name", "Dress")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SKU mismatch");
        server.verify();
    }

    @Test
    void deleteItemImages_blankItemId_isNoOp() {
        client.deleteItemImages("  ");
        server.verify();
    }

    @Test
    void downloadItemImage_blankItemId_returnsEmpty() {
        assertThat(client.downloadItemImage(" ", 1024)).isEmpty();
    }

    @Test
    void sanitizeMultipartFilename_stripsUnsafeCharactersAndFallsBackToDefault() {
        assertThat(ZohoBooksClient.sanitizeMultipartFilename(null)).isEqualTo("product.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("   ")).isEqualTo("product.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("path/to/photo.jpg")).isEqualTo("path/to/photo.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("path\\to\\photo.jpg")).isEqualTo("path\\to\\photo.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("\"bad\"\nname.jpg")).isEqualTo("badname.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("safe\u0001name.jpg")).isEqualTo("safename.jpg");
    }

    @Test
    void testConnection_returnsOrganizationsPayload() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/organizations?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"organizations\":[{\"organization_id\":\"org-1\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.testConnection()).containsKey("organizations");
        server.verify();
    }

    @Test
    void itemFieldCacheScope_includesApiHostAndOrgId() {
        assertThat(client.itemFieldCacheScope()).isEqualTo("https://www.zohoapis.in/books/v3/org-1");
    }

    @Test
    void createItem_success_returnsMatchedItem() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items?organization_id=org-1"))
            .andRespond(
                withSuccess("{\"code\":0,\"item\":{\"item_id\":\"new-99\",\"sku\":\"SKU-NEW\",\"name\":\"Dress\"}}", MediaType.APPLICATION_JSON)
            );

        Map<String, Object> item = client.createItem(Map.of("sku", "SKU-NEW", "name", "Dress"));

        assertThat(item.get("item"))
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("item_id", "new-99")
            .containsEntry("sku", "SKU-NEW");
        server.verify();
    }

    @Test
    void updateItem_success_returnsItem() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/item-55?organization_id=org-1"))
            .andRespond(
                withSuccess("{\"code\":0,\"item\":{\"item_id\":\"item-55\",\"sku\":\"SKU-55\"}}", MediaType.APPLICATION_JSON)
            );

        assertThat(client.updateItem("item-55", Map.of("sku", "SKU-55", "name", "Updated")).get("item"))
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("item_id", "item-55");
        server.verify();
    }

    @Test
    void markItemActiveAndInactive_postStatusEndpoints() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/item-77/active?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"message\":\"activated\"}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/item-77/inactive?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"message\":\"deactivated\"}", MediaType.APPLICATION_JSON));

        assertThat(client.markItemActive("item-77")).containsEntry("code", 0);
        assertThat(client.markItemInactive("item-77")).containsEntry("code", 0);
        server.verify();
    }

    @Test
    void getItemGroup_blankId_throws() {
        assertThatThrownBy(() -> client.getItemGroup(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("group id is required");
    }

    @Test
    void getItemGroup_identityMismatch_throws() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/itemgroups/group-a?organization_id=org-1"))
            .andRespond(
                withSuccess("{\"code\":0,\"item_group\":{\"group_id\":\"group-b\",\"items\":[{\"item_id\":\"1\",\"sku\":\"A\"}]}}", MediaType.APPLICATION_JSON)
            );

        assertThatThrownBy(() -> client.getItemGroup("group-a"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity mismatch");
        server.verify();
    }

    @Test
    void getItemGroup_missingGroupId_throws() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/itemgroups/group-a?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"item_group\":{\"items\":[{\"item_id\":\"1\",\"sku\":\"A\"}]}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getItemGroup("group-a"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("valid group_id");
        server.verify();
    }

    @Test
    void getItemGroup_invalidItemsList_throws() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/itemgroups/group-a?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"item_group\":{\"group_id\":\"group-a\",\"items\":\"bad\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getItemGroup("group-a"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("invalid item group items");
        server.verify();
    }

    @Test
    void itemFieldCacheScope_includesOrgAndDataCenter() {
        assertThat(client.itemFieldCacheScope()).isEqualTo("https://www.zohoapis.in/books/v3/org-1");
    }

    @Test
    void findContactByEmail_returnsFirstMatch() {
        server
            .expect(requestTo(containsString("/contacts?organization_id=org-1&email=buyer")))
            .andRespond(
                withSuccess(
                    "{\"contacts\":[{\"contact_id\":\"c-1\",\"email\":\"buyer@example.com\"},{\"contact_id\":\"c-2\"}]}",
                    MediaType.APPLICATION_JSON
                )
            );
        assertThat(client.findContactByEmail("buyer@example.com")).containsEntry("contact_id", "c-1");
        server.verify();
    }

    @Test
    void findContactByEmail_emptyList_returnsNull() {
        server
            .expect(requestTo(containsString("/contacts?organization_id=org-1&email=missing")))
            .andRespond(withSuccess("{\"contacts\":[]}", MediaType.APPLICATION_JSON));
        assertThat(client.findContactByEmail("missing@example.com")).isNull();
        server.verify();
    }

    @Test
    void findContactByName_returnsExactCaseInsensitiveMatch() {
        server
            .expect(requestTo(containsString("contact_name_contains=Jane")))
            .andRespond(
                withSuccess(
                    "{\"contacts\":[{\"contact_id\":\"c-3\",\"contact_name\":\"jane doe\"},{\"contact_id\":\"c-4\",\"contact_name\":\"Jane Doe Jr\"}]}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.findContactByName("Jane Doe")).containsEntry("contact_id", "c-3");
        server.verify();
    }

    @Test
    void createContact_postsPayload() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/contacts?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"contact\":{\"contact_id\":\"c-new\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.createContact(Map.of("contact_name", "New Buyer"))).containsKey("contact");
        server.verify();
    }

    @Test
    void salesOrderAndInvoiceEndpoints_delegateToBooksApi() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/salesorders?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"salesorder\":{\"salesorder_id\":\"so-1\"}}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/salesorders/so-1?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"salesorder\":{\"salesorder_id\":\"so-1\",\"status\":\"confirmed\"}}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/salesorders/so-1?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"salesorder\":{\"salesorder_id\":\"so-1\",\"status\":\"open\"}}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/invoices/fromsalesorder?organization_id=org-1&salesorder_id=so-1"))
            .andRespond(withSuccess("{\"code\":0,\"invoice\":{\"invoice_id\":\"inv-1\"}}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/invoices/inv-1/status/sent?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/invoices/inv-1/status/void?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/salesorders/so-1/status/void?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/salesorders/so-1/status/open?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.createSalesOrder(Map.of("reference_number", "MNM-1"))).containsKey("salesorder");
        assertThat(client.getSalesOrder("so-1")).containsKey("salesorder");
        assertThat(client.updateSalesOrder("so-1", Map.of("notes", "updated"))).containsKey("salesorder");
        assertThat(client.createInvoiceFromSalesOrder("so-1")).containsKey("invoice");
        assertThat(client.markInvoiceAsSent("inv-1")).containsEntry("code", 0);
        assertThat(client.voidInvoice("inv-1")).containsEntry("code", 0);
        assertThat(client.voidSalesOrder("so-1", "customer cancelled")).containsEntry("code", 0);
        assertThat(client.markSalesOrderAsOpen("so-1")).containsEntry("code", 0);
        server.verify();
    }

    @Test
    void invoicePaymentsAndCreditNoteEndpoints_delegateToBooksApi() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/invoices/inv-2?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"invoice\":{\"invoice_id\":\"inv-2\"}}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/invoices/inv-2/payments?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"payments\":[{\"payment_id\":\"pay-1\"}]}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/invoices/inv-2/payments/pay-1?organization_id=org-1"))
            .andRespond(withSuccess());
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/creditnotes/cn-1?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"creditnote\":{\"creditnote_id\":\"cn-1\"}}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/creditnotes/cn-1/status/open?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/creditnotes?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"creditnote\":{\"creditnote_id\":\"cn-2\"}}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/creditnotes/cn-2/invoices?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/customerpayments?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"payment\":{\"payment_id\":\"cp-1\"}}", MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/inventoryadjustments?organization_id=org-1"))
            .andRespond(withSuccess("{\"code\":0,\"inventory_adjustment\":{\"adjustment_id\":\"adj-1\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.getInvoice("inv-2")).containsKey("invoice");
        assertThat(client.listInvoicePayments("inv-2")).containsKey("payments");
        client.deleteInvoicePayment("inv-2", "pay-1");
        assertThat(client.getCreditNote("cn-1")).containsKey("creditnote");
        assertThat(client.markCreditNoteAsOpen("cn-1")).containsEntry("code", 0);
        assertThat(client.createCreditNote(Map.of("reference_number", "CN-1"))).containsKey("creditnote");
        assertThat(client.applyCreditNoteToInvoices("cn-2", Map.of("invoices", List.of()))).containsEntry("code", 0);
        assertThat(client.createCustomerPayment(Map.of("amount", 100))).containsKey("payment");
        assertThat(client.createInventoryAdjustment(Map.of("reason", "sync"))).containsKey("inventory_adjustment");
        server.verify();
    }

    @Test
    void findSalesOrderByReferenceNumber_nullOrVoid_skipsUnusableRows() {
        assertThat(client.findSalesOrderByReferenceNumber(null)).isNull();
        assertThat(client.findSalesOrderByReferenceNumber("  ")).isNull();

        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/salesorders?organization_id=org-1&reference_number=MNM-VOID"))
            .andRespond(
                withSuccess(
                    "{\"salesorders\":[" +
                    "{\"salesorder_id\":\"so-void\",\"reference_number\":\"MNM-VOID\",\"status\":\"void\"}," +
                    "{\"salesorder_id\":\"so-draft\",\"reference_number\":\"MNM-VOID\",\"status\":\"draft\"}" +
                    "]}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.findSalesOrderByReferenceNumber("MNM-VOID")).containsEntry("salesorder_id", "so-draft");
        server.verify();
    }

    @Test
    void findInvoiceByReferenceNumber_returnsExactMatch() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/invoices?organization_id=org-1&reference_number=INV-REF-1"))
            .andRespond(
                withSuccess(
                    "{\"invoices\":[{\"invoice_id\":\"inv-a\",\"reference_number\":\"INV-REF-1\"},{\"invoice_id\":\"inv-b\",\"reference_number\":\"other\"}]}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.findInvoiceByReferenceNumber("INV-REF-1")).containsEntry("invoice_id", "inv-a");
        assertThat(client.findInvoiceByReferenceNumber(null)).isNull();
        server.verify();
    }

    @Test
    void downloadInvoicePdf_blankInvoiceId_returnsEmptyWithoutHttp() throws Exception {
        assertThat(client.downloadInvoicePdf(" ", 4096)).isEmpty();
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadInvoicePdf_success_returnsPdfBytes() throws Exception {
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/pdf")), (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(pdf));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Optional<BinaryDownload> result = client.downloadInvoicePdf("inv-pdf-1", 8192);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().bytes()).isEqualTo(pdf);
        assertThat(result.orElseThrow().contentType()).isEqualTo("application/pdf");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadItemImage_interrupted_wrapsAndSetsInterruptFlag() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stopped"));

        assertThatThrownBy(() -> client.uploadItemImage("item-int", "x".getBytes(StandardCharsets.UTF_8), "a.jpg", "image/jpeg"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("upload interrupted")
            .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadItemImage_ioFailure_wrapsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new java.io.IOException("broken pipe"));

        assertThatThrownBy(() -> client.uploadItemImage("item-io", "x".getBytes(StandardCharsets.UTF_8), "a.jpg", "image/jpeg"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("upload failed")
            .hasCauseInstanceOf(java.io.IOException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadItemImages_interrupted_wrapsAndSetsInterruptFlag() throws Exception {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/gi-1?organization_id=org-1"))
            .andRespond(
                withSuccess("{\"item\":{\"item_id\":\"gi-1\",\"documents\":[]}}", MediaType.APPLICATION_JSON)
            );
        HttpResponse<String> deleteOk = mock(HttpResponse.class);
        when(deleteOk.statusCode()).thenReturn(204);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(deleteOk)
            .thenThrow(new InterruptedException("stopped"));

        assertThatThrownBy(() ->
                client.uploadItemImages("gi-1", List.of(new ImageUpload("x".getBytes(StandardCharsets.UTF_8), "a.jpg", "image/jpeg")))
            )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("images upload interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImages_interrupted_wrapsAndSetsInterruptFlag() throws Exception {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/del-int?organization_id=org-1"))
            .andRespond(
                withSuccess("{\"item\":{\"item_id\":\"del-int\",\"documents\":[]}}", MediaType.APPLICATION_JSON)
            );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stopped"));

        assertThatThrownBy(() -> client.deleteItemImages("del-int"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("images delete interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImage_interrupted_wrapsAndSetsInterruptFlag() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stopped"));

        assertThatThrownBy(() -> client.deleteItemImage("del-one"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("image delete interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadItemImage_interrupted_wrapsAndSetsInterruptFlag() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stopped"));

        assertThatThrownBy(() -> client.downloadItemImage("dl-int", 4096))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("download interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void downloadDocument_blankId_returnsEmpty() {
        assertThat(client.downloadDocument(" ", 1024)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadDocument_interrupted_wrapsAndSetsInterruptFlag() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stopped"));

        assertThatThrownBy(() -> client.downloadDocument("doc-1", 4096))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("document download interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImages_fetchAfterFrontDeleteFailure_wrapsIllegalState() throws Exception {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/del-fail?organization_id=org-1"))
            .andRespond(
                withSuccess("{\"item\":{\"item_id\":\"del-fail\"}}", MediaType.APPLICATION_JSON)
            );
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/del-fail?organization_id=org-1"))
            .andRespond(withServerError());
        HttpResponse<String> deleteOk = mock(HttpResponse.class);
        when(deleteOk.statusCode()).thenReturn(204);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(deleteOk);

        assertThatThrownBy(() -> client.deleteItemImages("del-fail"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("fetch failed after clearing the front image");
    }

    @Test
    void uploadItemImage_emptyBytes_throwsBeforeHttp() throws Exception {
        assertThatThrownBy(() -> client.uploadItemImage("item-empty", new byte[0], "a.jpg", "image/jpeg"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadItemImages_ioFailure_wrapsIOException() throws Exception {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/gi-io?organization_id=org-1"))
            .andRespond(
                withSuccess("{\"item\":{\"item_id\":\"gi-io\",\"documents\":[]}}", MediaType.APPLICATION_JSON)
            );
        HttpResponse<String> deleteOk = mock(HttpResponse.class);
        when(deleteOk.statusCode()).thenReturn(204);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(deleteOk)
            .thenThrow(new java.io.IOException("broken pipe"));

        assertThatThrownBy(() ->
                client.uploadItemImages("gi-io", List.of(new ImageUpload("x".getBytes(StandardCharsets.UTF_8), "a.jpg", "image/jpeg")))
            )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("images upload failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadInvoicePdf_interrupted_wrapsAndSetsInterruptFlag() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stopped"));

        assertThatThrownBy(() -> client.downloadInvoicePdf("inv-int", 4096))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PDF download interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadInvoicePdf_ioFailure_wrapsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new java.io.IOException("connection reset"));

        assertThatThrownBy(() -> client.downloadInvoicePdf("inv-io", 4096))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PDF download failed");
    }

    @Test
    void getItemGroup_acceptsNumericGroupIdFromZohoPayload() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/itemgroups/3624052000000394593?organization_id=org-1"))
            .andRespond(
                withSuccess(
                    """
                    {"item_group":{"group_id":3624052000000394593,"items":[{"sku":"SKU-A","item_id":"1"}]}}
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        Map<String, Object> group = client.getItemGroup("3624052000000394593");

        assertThat(group.get("group_id")).isEqualTo(3624052000000394593L);
        assertThat(group.get("items")).isInstanceOf(List.class);
        server.verify();
    }

    @Test
    void listActiveItems_delegatesToActiveFilter() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/books/v3/items?organization_id=org-1&page=3&per_page=200&filter_by=Status.Active"
                )
            )
            .andRespond(withSuccess("{\"items\":[{\"sku\":\"P3\"}],\"page_context\":{\"has_more_page\":false}}", MediaType.APPLICATION_JSON));

        assertThat(client.listActiveItems(3)).extracting(item -> item.get("sku")).containsExactly("P3");
        server.verify();
    }

    @Test
    void findItemBySku_returnsSingleMatch() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items?organization_id=org-1&sku=SKU-OK&filter_by=Status.All"))
            .andRespond(
                withSuccess(
                    "{\"items\":[{\"item_id\":\"item-ok\",\"sku\":\"SKU-OK\"}],\"page_context\":{\"has_more_page\":false}}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.findItemBySku("SKU-OK")).containsEntry("item_id", "item-ok");
        server.verify();
    }

    @Test
    void findItemBySku_matchWithoutItemId_throws() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items?organization_id=org-1&sku=SKU-NOID&filter_by=Status.All"))
            .andRespond(
                withSuccess(
                    "{\"items\":[{\"sku\":\"SKU-NOID\"}],\"page_context\":{\"has_more_page\":false}}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThatThrownBy(() -> client.findItemBySku("SKU-NOID"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("without an item_id");
        server.verify();
    }

    @Test
    void getItemGroup_rejectsNonMapVariantRows() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/itemgroups/bad-group?organization_id=org-1"))
            .andRespond(
                withSuccess(
                    "{\"item_group\":{\"group_id\":\"bad-group\",\"items\":[\"not-a-map\"]}}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThatThrownBy(() -> client.getItemGroup("bad-group")).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void updateSalesOrderBillingAddress_postsOneOffAddress() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/books/v3/salesorders/so-55/address/billing?organization_id=org-1"
                )
            )
            .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertThat(client.updateSalesOrderBillingAddress("so-55", Map.of("city", "Mumbai")))
            .containsEntry("code", 0);
        server.verify();
    }

    @Test
    void updateSalesOrderShippingAddress_postsOneOffAddress() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/books/v3/salesorders/so-56/address/shipping?organization_id=org-1"
                )
            )
            .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertThat(client.updateSalesOrderShippingAddress("so-56", Map.of("city", "Pune")))
            .containsEntry("code", 0);
        server.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadItemImage_httpError_wrapsIOException() throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn(new ByteArrayInputStream("error".getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> client.downloadItemImage("item-err", 4096))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("download failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadDocument_httpError_wrapsIOException() throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(502);
        when(response.body()).thenReturn(new ByteArrayInputStream("bad".getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> client.downloadDocument("doc-err", 4096))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("download failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadItemImage_jsonContentType_returnsEmpty() throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThat(client.downloadItemImage("item-json", 4096)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadItemImage_httpError_wrapsIOException() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("rejected");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> client.uploadItemImage("item-up", "bytes".getBytes(StandardCharsets.UTF_8), "a.jpg", "image/jpeg"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("upload failed");
    }

    @Test
    void deleteItemImage_blankId_isNoOp() throws Exception {
        client.deleteItemImage("  ");
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImage_ioFailure_wrapsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new java.io.IOException("broken pipe"));

        assertThatThrownBy(() -> client.deleteItemImage("del-io"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("delete failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadItemImage_retriesOnceAfterUnauthorized() throws Exception {
        HttpResponse<String> unauthorized = mock(HttpResponse.class);
        when(unauthorized.statusCode()).thenReturn(401);
        lenient().when(unauthorized.body()).thenReturn("unauthorized");
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        lenient().when(ok.body()).thenReturn("{\"code\":0}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(unauthorized, ok);

        client.uploadItemImage("item-retry", "bytes".getBytes(StandardCharsets.UTF_8), "a.jpg", "image/jpeg");

        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(oauthService, atLeastOnce()).accessToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadDocument_jsonContentType_returnsEmpty() throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThat(client.downloadDocument("doc-json", 4096)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImages_retriesOnceAfterUnauthorized() throws Exception {
        HttpResponse<String> unauthorized = mock(HttpResponse.class);
        when(unauthorized.statusCode()).thenReturn(401);
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(204);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(unauthorized, ok);

        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/item-unauth-del?organization_id=org-1"))
            .andRespond(withSuccess("{\"item\":{\"item_id\":\"item-unauth-del\",\"documents\":[]}}", MediaType.APPLICATION_JSON));

        client.deleteItemImages("item-unauth-del");

        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(oauthService, atLeastOnce()).accessToken();
        server.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImage_treats404AsAlreadyDeleted() throws Exception {
        HttpResponse<String> notFound = mock(HttpResponse.class);
        when(notFound.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(notFound);

        client.deleteItemImage("item-gone");

        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void findItemBySku_emptySearchResult_returnsNull() {
        server
            .expect(requestTo(containsString("/items?organization_id=org-1&sku=MISSING")))
            .andRespond(
                withSuccess("{\"items\":[],\"page_context\":{\"has_more_page\":false}}", MediaType.APPLICATION_JSON)
            );

        assertThat(client.findItemBySku("MISSING")).isNull();
        server.verify();
    }

    @Test
    void itemIdentifier_treatsNullStringAndZeroAsMissing() {
        String nullLiteral = ReflectionTestUtils.invokeMethod(ZohoBooksClient.class, "itemIdentifier", "null");
        String zero = ReflectionTestUtils.invokeMethod(ZohoBooksClient.class, "itemIdentifier", "0");
        String numeric = ReflectionTestUtils.invokeMethod(ZohoBooksClient.class, "itemIdentifier", 42L);
        String unsupported = ReflectionTestUtils.invokeMethod(ZohoBooksClient.class, "itemIdentifier", new Object());
        assertThat(nullLiteral).isNull();
        assertThat(zero).isNull();
        assertThat(numeric).isEqualTo("42");
        assertThat(unsupported).isNull();
    }

    @Test
    void findContactByName_noExactMatch_returnsNull() {
        server
            .expect(requestTo(containsString("/contacts?organization_id=org-1&contact_name_contains=Jane")))
            .andRespond(
                withSuccess("{\"contacts\":[{\"contact_id\":\"c-1\",\"contact_name\":\"Janet Doe\"}]}", MediaType.APPLICATION_JSON)
            );

        assertThat(client.findContactByName("Jane Doe")).isNull();
        server.verify();
    }

    @Test
    void listDeliveryChallans_returnsListFromResponse() {
        server
            .expect(requestTo(containsString("/deliverychallans?organization_id=org-1&salesorder_id=so-9")))
            .andRespond(
                withSuccess("{\"deliverychallans\":[{\"deliverychallan_id\":\"dc-1\"}]}", MediaType.APPLICATION_JSON)
            );

        assertThat(client.listDeliveryChallans("so-9")).hasSize(1);
        server.verify();
    }

    @Test
    void createInvoice_postsPayload() {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/invoices?organization_id=org-1"))
            .andRespond(withSuccess("{\"invoice\":{\"invoice_id\":\"inv-new\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.createInvoice(Map.of("customer_id", "c-1"))).containsKey("invoice");
        server.verify();
    }

    @Test
    void findInvoiceByReferenceNumber_noMatch_returnsNull() {
        server
            .expect(requestTo(containsString("/invoices?organization_id=org-1&reference_number=INV-MISS")))
            .andRespond(withSuccess("{\"invoices\":[{\"invoice_id\":\"inv-x\",\"reference_number\":\"OTHER\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.findInvoiceByReferenceNumber("INV-MISS")).isNull();
        server.verify();
    }

    @Test
    void findCreditNoteByReferenceNumber_unknownStatusUsesDefaultRank() {
        server
            .expect(requestTo(containsString("/creditnotes?organization_id=org-1&reference_number=CN-UNK")))
            .andRespond(
                withSuccess(
                    "{\"creditnotes\":[{\"creditnote_id\":\"cn-1\",\"reference_number\":\"CN-UNK\",\"status\":\"weird\"}]}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.findCreditNoteByReferenceNumber("CN-UNK")).containsEntry("creditnote_id", "cn-1");
        server.verify();
    }

    @Test
    void findSalesOrderByReferenceNumber_unknownStatusStillMatches() {
        server
            .expect(requestTo(containsString("/salesorders?organization_id=org-1&reference_number=MNM-UNK")))
            .andRespond(
                withSuccess(
                    "{\"salesorders\":[{\"salesorder_id\":\"so-unk\",\"reference_number\":\"MNM-UNK\",\"status\":\"custom\"}]}",
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(client.findSalesOrderByReferenceNumber("MNM-UNK")).containsEntry("salesorder_id", "so-unk");
        server.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadItemImage_emptyBody_returnsEmpty() throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/jpeg")), (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThat(client.downloadItemImage("item-empty", 1024)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImages_ioFailure_wrapsIOException() throws Exception {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/item-io?organization_id=org-1"))
            .andRespond(withSuccess("{\"item\":{\"item_id\":\"item-io\",\"documents\":[]}}", MediaType.APPLICATION_JSON));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new java.io.IOException("broken pipe"));

        assertThatThrownBy(() -> client.deleteItemImages("item-io"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("delete failed");
    }

    @Test
    void isBenignItemImagesDeleteFailure_recognizesZohoMissingDocumentCode() {
        Boolean benign = ReflectionTestUtils.invokeMethod(
            ZohoBooksClient.class,
            "isBenignItemImagesDeleteFailure",
            400,
            "{\"code\":33008,\"message\":\"document missing\"}"
        );
        assertThat(benign).isTrue();
    }

    @Test
    void isBenignItemImagesDeleteFailure_recognizesBenignNotFoundWording() {
        Boolean benign = ReflectionTestUtils.invokeMethod(
            ZohoBooksClient.class,
            "isBenignItemImagesDeleteFailure",
            400,
            "The document does not exist"
        );
        assertThat(benign).isTrue();
        Boolean notBenign = ReflectionTestUtils.invokeMethod(
            ZohoBooksClient.class,
            "isBenignItemImagesDeleteFailure",
            500,
            "The document does not exist"
        );
        assertThat(notBenign).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteItemImages_benign400MissingDocument_treatedAsCleared() throws Exception {
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/items/item-benign?organization_id=org-1"))
            .andRespond(withSuccess("{\"item\":{\"item_id\":\"item-benign\",\"documents\":[{\"document_id\":\"doc-1\"}]}}", MediaType.APPLICATION_JSON));
        HttpResponse<String> deleteResponse = mock(HttpResponse.class);
        when(deleteResponse.statusCode()).thenReturn(400);
        when(deleteResponse.body()).thenReturn("{\"message\":\"The document does not exist\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(deleteResponse);

        client.deleteItemImages("item-benign");

        server.verify();
    }
}
