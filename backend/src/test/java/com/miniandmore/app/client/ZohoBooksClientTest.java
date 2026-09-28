package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
    void sanitizeMultipartFilename_stripsUnsafeCharactersAndFallsBackToDefault() {
        assertThat(ZohoBooksClient.sanitizeMultipartFilename(null)).isEqualTo("product.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("   ")).isEqualTo("product.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("path/to/photo.jpg")).isEqualTo("path/to/photo.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("path\\to\\photo.jpg")).isEqualTo("path\\to\\photo.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("\"bad\"\nname.jpg")).isEqualTo("badname.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("safe\u0001name.jpg")).isEqualTo("safename.jpg");
    }
}
