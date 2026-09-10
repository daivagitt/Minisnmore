package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class XpressBeesClientTest {

    private static final String SERVICEABILITY_BODY =
        """
        {
          "status": true,
          "data": [
            {
              "eddDays": 3,
              "destination_city": "Mumbai",
              "destination_state": "Maharashtra",
              "destination_pincode": "400067"
            }
          ]
        }
        """;

    private XpressBeesClient client;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.XpressBees config = properties.getXpressbees();
        config.setEmail("info@example.com");
        config.setPassword("secret");
        config.setOriginPincode("560074");
        config.setBaseUrl("https://xpressbees.test");
        client = new XpressBeesClient(properties, new ObjectMapper());
        httpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", httpClient);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void isConfigured_requiresEmailPasswordAndOrigin() {
        assertThat(client.isConfigured()).isTrue();

        ApplicationProperties empty = new ApplicationProperties();
        XpressBeesClient disabled = new XpressBeesClient(empty, new ObjectMapper());
        assertThat(disabled.isConfigured()).isFalse();
    }

    @Test
    void parseServiceabilityResponse_success_returnsBestOption() {
        String body =
            """
            {
              "status": true,
              "data": [
                {
                  "id": "4",
                  "name": "Xpressbees 10 K.G",
                  "eddDays": 7,
                  "destination_city": "Mumbai",
                  "destination_state": "Maharashtra",
                  "destination_pincode": "400067"
                },
                {
                  "id": "1",
                  "name": "Surface Xpressbees 0.5 K.G",
                  "eddDays": 2,
                  "destination_city": "Mumbai",
                  "destination_state": "Maharashtra",
                  "destination_pincode": "400067"
                }
              ]
            }
            """;

        Optional<XpressBeesClient.ServiceabilityResult> result = client.parseServiceabilityResponse(body, "400067");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().city()).isEqualTo("Mumbai");
        assertThat(result.orElseThrow().state()).isEqualTo("Maharashtra");
        assertThat(result.orElseThrow().eddDays()).isEqualTo(2);
        assertThat(result.orElseThrow().eta()).isEqualTo("2 days");
    }

    @Test
    void parseServiceabilityResponse_notServiceable_returnsEmpty() {
        String body =
            """
            {"status": false, "data": "Destination Pincode Not Servicable."}
            """;

        assertThat(client.parseServiceabilityResponse(body, "522235")).isEmpty();
    }

    @Test
    void formatEta_handlesSameDayAndPlural() {
        assertThat(XpressBeesClient.formatEta(0)).isEqualTo("Same day");
        assertThat(XpressBeesClient.formatEta(1)).isEqualTo("1 day");
        assertThat(XpressBeesClient.formatEta(4)).isEqualTo("4 days");
    }

    @Test
    void checkServiceability_whenNotConfigured_returnsEmpty() {
        ApplicationProperties empty = new ApplicationProperties();
        XpressBeesClient disabled = new XpressBeesClient(empty, new ObjectMapper());
        ReflectionTestUtils.setField(disabled, "httpClient", httpClient);

        assertThat(disabled.checkServiceability("400067")).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "056001", "abc", "12345", "1234567" })
    void checkServiceability_invalidPincode_returnsEmpty(String pincode) {
        assertThat(client.checkServiceability(pincode)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkServiceability_loginThenServiceabilitySuccess_returnsResult() throws Exception {
        HttpResponse<String> loginResponse = mock(HttpResponse.class);
        when(loginResponse.statusCode()).thenReturn(200);
        when(loginResponse.body()).thenReturn("{\"status\":true,\"data\":\"plain-login-token\"}");

        HttpResponse<String> serviceabilityResponse = mock(HttpResponse.class);
        when(serviceabilityResponse.statusCode()).thenReturn(200);
        when(serviceabilityResponse.body()).thenReturn(SERVICEABILITY_BODY);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/api/users/login")) {
                    return loginResponse;
                }
                if (uri.equals("https://xpressbees.test/api/courier/serviceability/pincode/400067")) {
                    return serviceabilityResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        Optional<XpressBeesClient.ServiceabilityResult> result = client.checkServiceability("400067");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().city()).isEqualTo("Mumbai");
        assertThat(result.orElseThrow().state()).isEqualTo("Maharashtra");
        assertThat(result.orElseThrow().eddDays()).isEqualTo(3);
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkServiceability_loginHttpFailure_returnsEmpty() throws Exception {
        HttpResponse<String> loginResponse = mock(HttpResponse.class);
        when(loginResponse.statusCode()).thenReturn(401);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(loginResponse);

        assertThat(client.checkServiceability("400067")).isEmpty();
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkServiceability_loginRejected_returnsEmpty() throws Exception {
        HttpResponse<String> loginResponse = mock(HttpResponse.class);
        when(loginResponse.statusCode()).thenReturn(200);
        when(loginResponse.body()).thenReturn("{\"status\":false,\"message\":\"Invalid credentials\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(loginResponse);

        assertThat(client.checkServiceability("400067")).isEmpty();
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkServiceability_reusesCachedAccessToken() throws Exception {
        HttpResponse<String> loginResponse = mock(HttpResponse.class);
        when(loginResponse.statusCode()).thenReturn(200);
        when(loginResponse.body()).thenReturn("{\"status\":true,\"data\":\"" + jwtWithExpiry(Instant.now().plusSeconds(7200)) + "\"}");

        HttpResponse<String> serviceabilityResponse = mock(HttpResponse.class);
        when(serviceabilityResponse.statusCode()).thenReturn(200);
        when(serviceabilityResponse.body()).thenReturn(SERVICEABILITY_BODY);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/api/users/login")) {
                    return loginResponse;
                }
                if (uri.contains("/api/courier/serviceability/pincode/")) {
                    return serviceabilityResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        assertThat(client.checkServiceability("400067")).isPresent();
        assertThat(client.checkServiceability("400067")).isPresent();

        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkServiceability_serviceabilityHttpError_returnsEmpty() throws Exception {
        HttpResponse<String> loginResponse = mock(HttpResponse.class);
        when(loginResponse.statusCode()).thenReturn(200);
        when(loginResponse.body()).thenReturn("{\"status\":true,\"data\":\"plain-login-token\"}");

        HttpResponse<String> serviceabilityResponse = mock(HttpResponse.class);
        when(serviceabilityResponse.statusCode()).thenReturn(503);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/api/users/login")) {
                    return loginResponse;
                }
                if (uri.contains("/api/courier/serviceability/pincode/")) {
                    return serviceabilityResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        assertThat(client.checkServiceability("400067")).isEmpty();
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkServiceability_interrupted_restoresInterruptStatus() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stopped"));

        assertThat(client.checkServiceability("400067")).isEmpty();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void parseServiceabilityResponse_emptyData_returnsEmpty() {
        assertThat(client.parseServiceabilityResponse("{\"status\":true,\"data\":[]}", "400067")).isEmpty();
    }

    @Test
    void parseServiceabilityResponse_blankCityOrState_returnsEmpty() {
        String missingCity =
            """
            {
              "status": true,
              "data": [
                {"eddDays": 2, "destination_city": "", "destination_state": "Maharashtra"}
              ]
            }
            """;
        String missingState =
            """
            {
              "status": true,
              "data": [
                {"eddDays": 2, "destination_city": "Mumbai"}
              ]
            }
            """;

        assertThat(client.parseServiceabilityResponse(missingCity, "400067")).isEmpty();
        assertThat(client.parseServiceabilityResponse(missingState, "400067")).isEmpty();
    }

    @Test
    void parseServiceabilityResponse_malformedJson_returnsEmpty() {
        assertThat(client.parseServiceabilityResponse("{not-json", "400067")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkServiceability_jwtExpiryClaim_allowsTokenReuseWithoutRelogin() throws Exception {
        String jwt = jwtWithExpiry(Instant.now().plusSeconds(7200));
        HttpResponse<String> loginResponse = mock(HttpResponse.class);
        when(loginResponse.statusCode()).thenReturn(200);
        when(loginResponse.body()).thenReturn("{\"status\":true,\"data\":\"" + jwt + "\"}");

        HttpResponse<String> serviceabilityResponse = mock(HttpResponse.class);
        when(serviceabilityResponse.statusCode()).thenReturn(200);
        when(serviceabilityResponse.body()).thenReturn(SERVICEABILITY_BODY);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/api/users/login")) {
                    return loginResponse;
                }
                if (uri.contains("/api/courier/serviceability/pincode/")) {
                    return serviceabilityResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        assertThat(client.checkServiceability("400067")).isPresent();
        assertThat(client.checkServiceability("411001")).isPresent();

        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static String jwtWithExpiry(Instant expiresAt) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = "{\"exp\":" + expiresAt.getEpochSecond() + "}";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
