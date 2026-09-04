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

import com.miniandmore.app.service.ZohoOAuthService;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.test.web.client.ExpectedCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
            .andRespond(withSuccess("{\"items\":[{\"sku\":\"ACTIVE\"}]}", MediaType.APPLICATION_JSON));

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
            .andRespond(withSuccess("{\"items\":[{\"sku\":\"INACTIVE\",\"status\":\"inactive\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.listAllStatusItems(2))
            .singleElement()
            .satisfies(item -> assertThat(item.get("status")).isEqualTo("inactive"));
        server.verify();
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
                    "{\"item\":{\"documents\":[{\"document_id\":\"doc-1\"},{\"document_id\":\"doc-2\"}]}}",
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
            .andRespond(withSuccess("{\"item\":{\"image_document_id\":\"gallery-only-7\"}}", MediaType.APPLICATION_JSON));

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
                    "{\"item\":{\"documents\":[{\"document_id\":\"doc-1\"},{\"document_id\":\"doc-2\"}]}}",
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
                    "{\"item\":{\"documents\":[{\"document_id\":\"stale-doc\"}]}}",
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
}
