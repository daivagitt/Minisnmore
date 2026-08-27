package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.miniandmore.app.service.ZohoOAuthService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ZohoBooksClientTest {

    @Mock
    private ZohoOAuthService oauthService;

    private MockRestServiceServer server;
    private ZohoBooksClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ZohoBooksClient(oauthService, builder);
        when(oauthService.dataCenter()).thenReturn("in");
        when(oauthService.apiHost("in")).thenReturn("https://www.zohoapis.in");
        when(oauthService.orgId()).thenReturn("org-1");
        when(oauthService.accessToken()).thenReturn("token-1");
    }

    @Test
    void fullItemList_requestsOnlyActiveItems() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/books/v3/items?organization_id=org-1&page=1&per_page=200&filter_by=Status.Active"
                )
            )
            .andExpect(header("Authorization", "Zoho-oauthtoken token-1"))
            .andRespond(withSuccess("{\"items\":[{\"sku\":\"ACTIVE\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.listItems(1)).extracting(item -> item.get("sku")).containsExactly("ACTIVE");
        server.verify();
    }

    @Test
    void incrementalItemList_doesNotFilterOutInactiveChanges() {
        server
            .expect(
                requestTo(
                    "https://www.zohoapis.in/books/v3/items?organization_id=org-1&page=2&per_page=200&last_modified_time=2026-08-27T01:02:03Z"
                )
            )
            .andRespond(withSuccess("{\"items\":[{\"sku\":\"INACTIVE\",\"status\":\"inactive\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.listItems(2, Instant.parse("2026-08-27T01:02:03Z")))
            .singleElement()
            .satisfies(item -> assertThat(item.get("status")).isEqualTo("inactive"));
        server.verify();
    }
}
