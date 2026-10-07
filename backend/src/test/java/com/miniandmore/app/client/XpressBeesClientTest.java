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
import java.util.List;
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

    @Test
    void parseReversePickupResponse_tokenSuccess_returnsAwb() throws Exception {
        String body =
            """
            {"status":true,"data":{"awb":"1234567890","tracking_url":"https://track/123","pickup_window":"Tomorrow","request_id":"req-9"}}
            """;
        XpressBeesClient.ReversePickupRequest request = new XpressBeesClient.ReversePickupRequest(
            "RET-1",
            "ORD-1",
            new XpressBeesClient.ReversePickupAddress("Jane", "9876543210", null, "Line 1", null, "City", "State", "560001"),
            new XpressBeesClient.ReversePickupAddress("WH", "9876500000", null, "WH Line", null, "City", "State", "560074"),
            List.of(new XpressBeesClient.ReversePickupLine("Tee", "SKU", 1, null, 500)),
            null
        );

        XpressBeesClient.ReversePickupResult result = client.parseReversePickupResponse(body, request);

        assertThat(result.awb()).isEqualTo("1234567890");
        assertThat(result.trackingUrl()).isEqualTo("https://track/123");
        assertThat(result.pickupWindow()).isEqualTo("Tomorrow");
        assertThat(result.providerRequestId()).isEqualTo("req-9");
    }

    @Test
    void parseReversePickupResponse_legacySuccess_returnsAwb() throws Exception {
        String body =
            """
            {"ReverseManifestDetails":[{"ReturnCode":"100","ReturnMessage":"Success","AWBNumber":"9988776655","PickupDate":"2026-09-11"}]}
            """;
        XpressBeesClient.ReversePickupRequest request = new XpressBeesClient.ReversePickupRequest(
            "RET-1",
            "ORD-1",
            new XpressBeesClient.ReversePickupAddress("Jane", "9876543210", null, "Line 1", null, "City", "State", "560001"),
            new XpressBeesClient.ReversePickupAddress("WH", "9876500000", null, "WH Line", null, "City", "State", "560074"),
            List.of(new XpressBeesClient.ReversePickupLine("Tee", "SKU", 1, null, 500)),
            null
        );

        XpressBeesClient.ReversePickupResult result = client.parseReversePickupResponse(body, request);

        assertThat(result.awb()).isEqualTo("9988776655");
        assertThat(result.pickupWindow()).isEqualTo("2026-09-11");
    }

    @Test
    void buildTokenReverseManifestBody_usesShipments2Contract() throws Exception {
        var node = new ObjectMapper().readTree(client.buildTokenReverseManifestBody(sampleReverseRequest(), "8"));

        assertThat(node.path("order_number").asText()).isEqualTo("312CCA7BFFAF-662135");
        assertThat(node.path("order_number").asText()).hasSizeLessThanOrEqualTo(20);
        assertThat(node.path("payment_type").asText()).isEqualTo("reverse");
        assertThat(node.path("courier_id").asText()).isEqualTo("8");
        assertThat(node.path("collectable_amount").decimalValue()).isEqualByComparingTo("0");
        assertThat(node.path("pickup").path("name").asText()).isEqualTo("WH");
        assertThat(node.path("pickup").path("pincode").asText()).isEqualTo("560074");
        assertThat(node.path("consignee").path("name").asText()).isEqualTo("Jane");
        assertThat(node.path("consignee").path("pincode").asText()).isEqualTo("560001");
        assertThat(node.path("order_items").get(0).path("qty").asText()).isEqualTo("1");
        assertThat(node.path("package_weight").asInt()).isEqualTo(500);
        assertThat(node.has("delivery")).isFalse();
        assertThat(node.has("products")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createReversePickup_tokenAuth_replacesLegacyRouteWithShipments2() throws Exception {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.XpressBees config = properties.getXpressbees();
        config.setEmail("info@example.com");
        config.setPassword("secret");
        config.setOriginPincode("560074");
        config.setBaseUrl("https://xpressbees.test");
        config.setBusinessAccountName("MiniSnMore");
        config.setWarehouseLine1("Warehouse 1");
        // This is still present in UAT's environment. Token auth must not call this legacy API.
        config.setReverseManifestPath("/api/courier/reverse/order");
        XpressBeesClient shippingClient = new XpressBeesClient(properties, new ObjectMapper());
        HttpClient shippingHttp = mock(HttpClient.class);
        ReflectionTestUtils.setField(shippingClient, "httpClient", shippingHttp);

        HttpResponse<String> loginResponse = mock(HttpResponse.class);
        when(loginResponse.statusCode()).thenReturn(200);
        when(loginResponse.body()).thenReturn("{\"status\":true,\"data\":\"plain-login-token\"}");

        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(200);
        when(createResponse.body()).thenReturn("{\"status\":true,\"data\":{\"awb_number\":\"XB-REV-1\",\"shipment_id\":99}}");

        HttpResponse<String> serviceabilityResponse = mock(HttpResponse.class);
        when(serviceabilityResponse.statusCode()).thenReturn(200);
        when(serviceabilityResponse.body())
            .thenReturn("{\"status\":true,\"data\":[{\"id\":\"1\",\"name\":\"Surface Xpressbees 0.5 K.G\"},{\"id\":\"8\",\"name\":\"Express Reverse\"}]}");

        when(shippingHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/api/users/login")) {
                    return loginResponse;
                }
                if (uri.endsWith("/api/courier/serviceability/pincode/560001")) {
                    assertThat(request.method()).isEqualTo("GET");
                    return serviceabilityResponse;
                }
                if (uri.equals("https://xpressbees.test/api/shipments2")) {
                    assertThat(request.method()).isEqualTo("POST");
                    return createResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        XpressBeesClient.ReversePickupResult result = shippingClient.createReversePickup(sampleReverseRequest());

        assertThat(result.awb()).isEqualTo("XB-REV-1");
        verify(shippingHttp, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void parseForwardShipmentResponse_tokenSuccess_returnsAwbAndLabel() throws Exception {
        String body =
            """
            {"status":true,"data":{"awb":"5544332211","tracking_url":"https://track/out","label_url":"https://label/out","request_id":"out-req-1"}}
            """;
        XpressBeesClient.ForwardShipmentRequest request = new XpressBeesClient.ForwardShipmentRequest(
            "RET-1-OUT",
            "ORD-1",
            new XpressBeesClient.ReversePickupAddress("WH", "9876500000", null, "WH Line", null, "City", "State", "560074"),
            new XpressBeesClient.ReversePickupAddress("Jane", "9876543210", null, "Line 1", null, "City", "State", "560001"),
            List.of(new XpressBeesClient.ReversePickupLine("Tee", "SKU", 1, null, 500)),
            null
        );

        XpressBeesClient.ForwardShipmentResult result = client.parseForwardShipmentResponse(body, request);

        assertThat(result.awb()).isEqualTo("5544332211");
        assertThat(result.trackingUrl()).isEqualTo("https://track/out");
        assertThat(result.labelUrl()).isEqualTo("https://label/out");
        assertThat(result.providerRequestId()).isEqualTo("out-req-1");
    }

    @Test
    void parseForwardShipmentResponse_tokenSuccessWithAwbNumberAndLabel_returnsAwbAndLabel() throws Exception {
        String body =
            """
            {"status":true,"data":{"awb_number":"143449610567099","shipment_id":28100828,"label":"https://label/cod","payment_type":"cod"}}
            """;
        XpressBeesClient.ForwardShipmentRequest request = sampleForwardRequest("cod", new java.math.BigDecimal("100"));

        XpressBeesClient.ForwardShipmentResult result = client.parseForwardShipmentResponse(body, request);

        assertThat(result.awb()).isEqualTo("143449610567099");
        assertThat(result.labelUrl()).isEqualTo("https://label/cod");
        assertThat(result.providerRequestId()).isEqualTo("28100828");
    }

    @Test
    void buildTokenForwardManifestBody_cod_includesCollectableAmountConsigneeAndCourier() throws Exception {
        String json = client.buildTokenForwardManifestBody(
            sampleForwardRequest("cod", new java.math.BigDecimal("499.00")),
            "1"
        );

        var node = new ObjectMapper().readTree(json);
        assertThat(node.path("payment_type").asText()).isEqualTo("cod");
        assertThat(node.path("order_amount").decimalValue()).isEqualByComparingTo("499.00");
        assertThat(node.path("collectable_amount").decimalValue()).isEqualByComparingTo("499.00");
        assertThat(node.path("courier_id").asText()).isEqualTo("1");
        assertThat(node.path("consignee").path("name").asText()).isEqualTo("Jane");
        assertThat(node.path("consignee").path("pincode").asText()).isEqualTo("560001");
        assertThat(node.path("consignee").path("phone").asText()).isEqualTo("9876543210");
        assertThat(node.path("pickup").path("warehouse_name").asText()).isEqualTo("Mini's n More");
        assertThat(node.path("order_items").isArray()).isTrue();
        assertThat(node.path("order_items").get(0).path("qty").asText()).isEqualTo("1");
        assertThat(node.path("package_weight").asInt()).isEqualTo(500);
        assertThat(node.path("package_length").asInt()).isEqualTo(10);
        assertThat(node.path("package_breadth").asInt()).isEqualTo(10);
        assertThat(node.path("package_height").asInt()).isEqualTo(10);
        assertThat(node.has("delivery")).isFalse();
        assertThat(node.has("products")).isFalse();
    }

    @Test
    void buildTokenForwardManifestBody_prepaid_collectableAmountIsZero() throws Exception {
        String json = client.buildTokenForwardManifestBody(
            sampleForwardRequest("prepaid", new java.math.BigDecimal("250.00")),
            "1"
        );

        var node = new ObjectMapper().readTree(json);
        assertThat(node.path("payment_type").asText()).isEqualTo("prepaid");
        assertThat(node.path("order_amount").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(node.path("collectable_amount").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createForwardShipment_postsToShipments2() throws Exception {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.XpressBees config = properties.getXpressbees();
        config.setEmail("info@example.com");
        config.setPassword("secret");
        config.setOriginPincode("560074");
        config.setBaseUrl("https://xpressbees.test");
        config.setBusinessAccountName("MiniSnMore");
        config.setWarehouseName("Mini's n More");
        config.setWarehouseLine1("Warehouse 1");
        XpressBeesClient shippingClient = new XpressBeesClient(properties, new ObjectMapper());
        HttpClient shippingHttp = mock(HttpClient.class);
        ReflectionTestUtils.setField(shippingClient, "httpClient", shippingHttp);

        HttpResponse<String> loginResponse = mock(HttpResponse.class);
        when(loginResponse.statusCode()).thenReturn(200);
        when(loginResponse.body()).thenReturn("{\"status\":true,\"data\":\"plain-login-token\"}");

        HttpResponse<String> serviceabilityResponse = mock(HttpResponse.class);
        when(serviceabilityResponse.statusCode()).thenReturn(200);
        when(serviceabilityResponse.body()).thenReturn(
            """
            {"status":true,"data":[{"id":"8","name":"Express Reverse"},{"id":"1","name":"Surface Xpressbees 0.5 K.G"}]}
            """
        );

        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(200);
        when(createResponse.body()).thenReturn(
            """
            {"status":true,"data":{"awb_number":"XB-COD-1","label":"https://label/1","shipment_id":99}}
            """
        );

        when(shippingHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/api/users/login")) {
                    return loginResponse;
                }
                if (uri.contains("/api/courier/serviceability/pincode/")) {
                    assertThat(request.method()).isEqualTo("GET");
                    return serviceabilityResponse;
                }
                if (uri.equals("https://xpressbees.test/api/shipments2")) {
                    assertThat(request.method()).isEqualTo("POST");
                    return createResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        XpressBeesClient.ForwardShipmentResult result = shippingClient.createForwardShipment(
            sampleForwardRequest("cod", new java.math.BigDecimal("100"))
        );

        assertThat(result.awb()).isEqualTo("XB-COD-1");
        assertThat(result.labelUrl()).isEqualTo("https://label/1");
        verify(shippingHttp, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void indianMobile10_stripsCountryCodeAndSeparators() {
        assertThat(XpressBeesClient.indianMobile10("+91 98765 43210")).isEqualTo("9876543210");
        assertThat(XpressBeesClient.indianMobile10("919876543210")).isEqualTo("9876543210");
        assertThat(XpressBeesClient.indianMobile10("09876543210")).isEqualTo("9876543210");
        assertThat(XpressBeesClient.indianMobile10("9876543210")).isEqualTo("9876543210");
        assertThat(XpressBeesClient.indianMobile10("12345")).isNull();
        assertThat(XpressBeesClient.indianMobile10(null)).isNull();
    }

    @Test
    void buildTokenForwardManifestBody_normalizesConsigneePhoneToTenDigits() throws Exception {
        XpressBeesClient.ForwardShipmentRequest request = new XpressBeesClient.ForwardShipmentRequest(
            "MNM-COD-1",
            "MNM-COD-1",
            new XpressBeesClient.ReversePickupAddress("WH", "+91-6361945139", null, "WH Line", null, "City", "State", "560074"),
            new XpressBeesClient.ReversePickupAddress("Jane", "+919876543210", null, "Line 1", null, "City", "State", "560001"),
            List.of(new XpressBeesClient.ReversePickupLine("Tee", "SKU", 1, new java.math.BigDecimal("100"), 500)),
            null,
            "cod",
            new java.math.BigDecimal("100"),
            new java.math.BigDecimal("100")
        );

        var node = new ObjectMapper().readTree(client.buildTokenForwardManifestBody(request, "1"));
        assertThat(node.path("consignee").path("phone").asText()).isEqualTo("9876543210");
        assertThat(node.path("pickup").path("phone").asText()).isEqualTo("6361945139");
    }

    @Test
    void preferredForwardCourierName_matchesWeightSlab() {
        assertThat(XpressBeesClient.preferredForwardCourierName(500)).isEqualTo("Surface Xpressbees 0.5 K.G");
        assertThat(XpressBeesClient.preferredForwardCourierName(1000)).isEqualTo("Xpressbees 1 K.G");
        assertThat(XpressBeesClient.preferredForwardCourierName(2000)).isEqualTo("Xpressbees 2 K.G");
        assertThat(XpressBeesClient.preferredForwardCourierName(5000)).isEqualTo("Xpressbees 5 K.G");
        assertThat(XpressBeesClient.preferredForwardCourierName(5001)).isEqualTo("Xpressbees 10 K.G");
    }

    @Test
    void parseForwardCourierOptions_skipsReverseAndSurfacesRate() {
        String body =
            """
            {
              "status": true,
              "data": [
                {"id":"8","name":"Express Reverse","eddDays":4},
                {"id":"1","name":"Surface Xpressbees 0.5 K.G","eddDays":3,"rate":42},
                {"id":"2","name":"Xpressbees 1 K.G","eddDays":2,"freight":"55.50"}
              ]
            }
            """;

        List<XpressBeesClient.CourierOption> options = client.parseForwardCourierOptions(body, 500);

        assertThat(options).hasSize(2);
        assertThat(options.get(0).id()).isEqualTo("1");
        assertThat(options.get(0).preferred()).isTrue();
        assertThat(options.get(0).rate()).isEqualByComparingTo("42");
        assertThat(options.get(1).preferred()).isFalse();
        assertThat(options.get(1).rate()).isEqualByComparingTo("55.50");
    }

    @Test
    void parseForwardCourierOptions_prefersNestedFreightOverZeroRate() {
        String body =
            """
            {
              "status": true,
              "data": [
                {"id":"1","name":"Surface Xpressbees 0.5 K.G","eddDays":3,"rate":0,"freight_charges":48.5},
                {"id":"2","name":"Xpressbees 1 K.G","eddDays":2,"pricing":{"total_charges":"72"}}
              ]
            }
            """;

        List<XpressBeesClient.CourierOption> options = client.parseForwardCourierOptions(body, 500);

        assertThat(options).hasSize(2);
        assertThat(options.get(0).rate()).isEqualByComparingTo("48.5");
        assertThat(options.get(1).rate()).isEqualByComparingTo("72");
    }

    @Test
    void parseForwardCourierOptions_prefersTotalChargesOverFreightForCod() {
        String body =
            """
            {
              "status": true,
              "data": [
                {"id":"1","name":"Surface Xpressbees 0.5 K.G","freight_charges":50,"cod_charges":34,"total_charges":84,"eddDays":7}
              ]
            }
            """;

        List<XpressBeesClient.CourierOption> options = client.parseForwardCourierOptions(body, 500);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).rate()).isEqualByComparingTo("84");
    }

    @Test
    void parseForwardCourierOptions_readsLiveFreightChargesWithoutRateField() {
        String body =
            """
            {
              "status": true,
              "data": [
                {
                  "id":"1",
                  "name":"Surface Xpressbees 0.5 K.G",
                  "freight_charges":50,
                  "cod_charges":0,
                  "total_charges":50,
                  "eddDays":7
                }
              ]
            }
            """;

        List<XpressBeesClient.CourierOption> options = client.parseForwardCourierOptions(body, 500);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).rate()).isEqualByComparingTo("50");
        assertThat(options.get(0).eta()).isEqualTo("7 days");
    }

    @Test
    void buildCourierRatePayload_postsIntegerGramsDimensionsAndCodAmount() {
        java.util.Map<String, Object> prepaid = client.buildCourierRatePayload("400067", 500, 20, 15, 10, "prepaid", null);
        assertThat(prepaid.get("origin")).isEqualTo("560074");
        assertThat(prepaid.get("destination")).isEqualTo("400067");
        assertThat(prepaid.get("payment_type")).isEqualTo("prepaid");
        assertThat(prepaid.get("weight")).isEqualTo(500);
        assertThat(prepaid.get("length")).isEqualTo(20);
        assertThat(prepaid.get("breadth")).isEqualTo(15);
        assertThat(prepaid.get("height")).isEqualTo(10);
        assertThat(prepaid).doesNotContainKey("order_amount");

        java.util.Map<String, Object> cod = client.buildCourierRatePayload(
            "400067",
            500,
            0,
            0,
            0,
            "cod",
            new java.math.BigDecimal("1499")
        );
        assertThat(cod.get("payment_type")).isEqualTo("cod");
        assertThat((java.math.BigDecimal) cod.get("order_amount")).isEqualByComparingTo("1499");
        assertThat(cod).doesNotContainKey("length");

        java.util.Map<String, Object> missingCodAmount = client.buildCourierRatePayload("400067", 0, 10, 8, 4, "cod", null);
        assertThat(missingCodAmount.get("payment_type")).isEqualTo("cod");
        assertThat(missingCodAmount.get("weight")).isEqualTo(1);
        assertThat(missingCodAmount).doesNotContainKey("order_amount");
    }

    @Test
    void courierSelectionWeightGrams_usesChargeableWeightForVolumetricBox() {
        int weight = ReflectionTestUtils.invokeMethod(client, "courierSelectionWeightGrams", 400, 40, 30, 25);

        assertThat(weight).isEqualTo(6000);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listForwardCourierOptions_postsServiceabilityBodyInsteadOfGet() throws Exception {
        HttpResponse<String> loginResponse = mock(HttpResponse.class);
        when(loginResponse.statusCode()).thenReturn(200);
        when(loginResponse.body()).thenReturn("{\"status\":true,\"data\":\"plain-login-token\"}");

        HttpResponse<String> ratesResponse = mock(HttpResponse.class);
        when(ratesResponse.statusCode()).thenReturn(200);
        when(ratesResponse.body())
            .thenReturn(
                """
                {"status":true,"data":[{"id":"1","name":"Surface Xpressbees 0.5 K.G","freight_charges":50,"eddDays":7}]}
                """
            );

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/api/users/login")) {
                    return loginResponse;
                }
                if (uri.equals("https://xpressbees.test/api/courier/serviceability/pincode/400067")) {
                    assertThat(request.method()).isEqualTo("POST");
                    return ratesResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + request.method() + " " + uri);
            });

        List<XpressBeesClient.CourierOption> options = client.listForwardCourierOptions(
            "400067",
            500,
            20,
            15,
            10,
            "prepaid",
            null
        );

        assertThat(options).hasSize(1);
        assertThat(options.get(0).rate()).isEqualByComparingTo("50");
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void courierSelectionWeightGrams_usesChargeableWeightFromDimensions() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getXpressbees().setVolumetricDivisor(5000);
        XpressBeesClient shippingClient = new XpressBeesClient(properties, new ObjectMapper());
        XpressBeesClient.ForwardShipmentRequest request = new XpressBeesClient.ForwardShipmentRequest(
            "MNM-VOL-1",
            "MNM-VOL-1",
            new XpressBeesClient.ReversePickupAddress("WH", "9876500000", null, "WH Line", null, "City", "State", "560074"),
            new XpressBeesClient.ReversePickupAddress("Jane", "9876543210", null, "Line 1", null, "City", "State", "560001"),
            List.of(new XpressBeesClient.ReversePickupLine("Tee", "SKU", 1, new java.math.BigDecimal("100"), 400)),
            null,
            "prepaid",
            new java.math.BigDecimal("100"),
            java.math.BigDecimal.ZERO,
            20,
            20,
            20,
            400,
            null
        );

        int weight = ReflectionTestUtils.invokeMethod(shippingClient, "courierSelectionWeightGrams", request);

        assertThat(weight).isEqualTo(1600);
        assertThat(XpressBeesClient.preferredForwardCourierName(weight)).isEqualTo("Xpressbees 2 K.G");
    }

    @Test
    void buildTokenForwardManifestBody_usesSavedPackageDimensionsAndWeight() throws Exception {
        XpressBeesClient.ForwardShipmentRequest request = new XpressBeesClient.ForwardShipmentRequest(
            "MNM-1",
            "MNM-1",
            new XpressBeesClient.ReversePickupAddress("WH", "9876500000", null, "WH Line", null, "City", "State", "560074"),
            new XpressBeesClient.ReversePickupAddress("Jane", "9876543210", null, "Line 1", null, "City", "State", "560001"),
            List.of(new XpressBeesClient.ReversePickupLine("Tee", "SKU", 1, new java.math.BigDecimal("100"), 500)),
            null,
            "prepaid",
            new java.math.BigDecimal("100"),
            java.math.BigDecimal.ZERO,
            20,
            15,
            12,
            750,
            "2"
        );

        var node = new ObjectMapper().readTree(client.buildTokenForwardManifestBody(request, "2"));
        assertThat(node.path("package_weight").asInt()).isEqualTo(750);
        assertThat(node.path("package_length").asInt()).isEqualTo(20);
        assertThat(node.path("package_breadth").asInt()).isEqualTo(15);
        assertThat(node.path("package_height").asInt()).isEqualTo(12);
        assertThat(node.path("courier_id").asText()).isEqualTo("2");
    }

    @Test
    void buildTokenForwardManifestBody_leavesShortOrderNumberUnchanged() throws Exception {
        var node = new ObjectMapper().readTree(client.buildTokenForwardManifestBody(sampleForwardRequest("prepaid", new java.math.BigDecimal("100")), "1"));

        assertThat(node.path("order_number").asText()).isEqualTo("MNM-COD-1");
        assertThat(node.path("order_id").asText()).isEqualTo("MNM-COD-1");
    }

    @Test
    void buildTokenForwardManifestBody_truncatesExchangeOutboundOrderNumberTo30() throws Exception {
        // Store order numbers are well under 30. Exchange outbound appends "-OUT" to the
        // return number, which is what ExpressBees rejected for RET-MNM-F1942C01991F-2BFB42.
        String storeOrderNumber = "MNM-F1942C01991F";
        String caseReference = "RET-MNM-F1942C01991F-2BFB42-OUT";
        assertThat(storeOrderNumber.length()).isLessThan(30);
        assertThat(caseReference.length()).isGreaterThan(30);

        XpressBeesClient.ForwardShipmentRequest request = new XpressBeesClient.ForwardShipmentRequest(
            caseReference,
            storeOrderNumber,
            new XpressBeesClient.ReversePickupAddress("WH", "9876500000", null, "WH Line", null, "City", "State", "560074"),
            new XpressBeesClient.ReversePickupAddress("Jane", "9876543210", null, "Line 1", null, "City", "State", "560001"),
            List.of(new XpressBeesClient.ReversePickupLine("Tee", "SKU", 1, new java.math.BigDecimal("100"), 500)),
            null
        );

        var node = new ObjectMapper().readTree(client.buildTokenForwardManifestBody(request, "1"));
        String providerOrderNumber = node.path("order_number").asText();

        assertThat(providerOrderNumber).isEqualTo("ET-MNM-F1942C01991F-2BFB42-OUT");
        assertThat(providerOrderNumber.length()).isLessThanOrEqualTo(XpressBeesClient.FORWARD_PROVIDER_ORDER_NUMBER_MAX_LEN);
        assertThat(node.path("order_id").asText()).isEqualTo(providerOrderNumber);
        assertThat(providerOrderNumber).contains("2BFB42");
        assertThat(providerOrderNumber).endsWith("-OUT");
    }

    @Test
    void truncateProviderOrderNumber_keepsUniqueTail() {
        assertThat(XpressBeesClient.truncateProviderOrderNumber("MNM-F1942C01991F", 30)).isEqualTo("MNM-F1942C01991F");
        assertThat(XpressBeesClient.truncateProviderOrderNumber("RET-MNM-F1942C01991F-2BFB42-OUT", 30))
            .isEqualTo("ET-MNM-F1942C01991F-2BFB42-OUT")
            .hasSize(30);
        assertThat(XpressBeesClient.truncateProviderOrderNumber("-ABCDEFGHIJKLMNOPQRSTUVWXYZ", 20))
            .doesNotStartWith("-")
            .hasSizeLessThanOrEqualTo(20);
    }

    private static XpressBeesClient.ForwardShipmentRequest sampleForwardRequest(String paymentType, java.math.BigDecimal amount) {
        return new XpressBeesClient.ForwardShipmentRequest(
            "MNM-COD-1",
            "MNM-COD-1",
            new XpressBeesClient.ReversePickupAddress("WH", "9876500000", null, "WH Line", null, "City", "State", "560074"),
            new XpressBeesClient.ReversePickupAddress("Jane", "9876543210", null, "Line 1", null, "City", "State", "560001"),
            List.of(new XpressBeesClient.ReversePickupLine("Tee", "SKU", 1, amount, 500)),
            null,
            paymentType,
            amount,
            "cod".equals(paymentType) ? amount : java.math.BigDecimal.ZERO
        );
    }

    private static XpressBeesClient.ReversePickupRequest sampleReverseRequest() {
        return new XpressBeesClient.ReversePickupRequest(
            "RET-MNM-312CCA7BFFAF-662135",
            "MNM-312CCA7BFFAF",
            new XpressBeesClient.ReversePickupAddress(
                "Jane",
                "9876543210",
                "jane@example.com",
                "Line 1",
                null,
                "City",
                "State",
                "560001"
            ),
            new XpressBeesClient.ReversePickupAddress(
                "WH",
                "9876500000",
                null,
                "WH Line",
                null,
                "City",
                "State",
                "560074"
            ),
            List.of(new XpressBeesClient.ReversePickupLine("Tee", "SKU", 1, new java.math.BigDecimal("100"), 500)),
            null
        );
    }

    @Test
    void isForwardShipmentConfigured_requiresBusinessAccountWarehouseAndCredentials() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.XpressBees config = properties.getXpressbees();
        config.setEmail("info@example.com");
        config.setPassword("secret");
        config.setOriginPincode("560074");
        config.setBusinessAccountName("MiniSnMore");
        config.setWarehouseLine1("Warehouse 1");
        XpressBeesClient configured = new XpressBeesClient(properties, new ObjectMapper());
        assertThat(configured.isForwardShipmentConfigured()).isTrue();

        ApplicationProperties empty = new ApplicationProperties();
        XpressBeesClient disabled = new XpressBeesClient(empty, new ObjectMapper());
        assertThat(disabled.isForwardShipmentConfigured()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchForwardTracking_readsCarrierStatusFromShipments2() throws Exception {
        String jwt = jwtWithExpiry(Instant.now().plusSeconds(7200));
        HttpResponse<String> loginResponse = mock(HttpResponse.class);
        when(loginResponse.statusCode()).thenReturn(200);
        when(loginResponse.body()).thenReturn("{\"status\":true,\"data\":\"" + jwt + "\"}");

        HttpResponse<String> trackResponse = mock(HttpResponse.class);
        when(trackResponse.statusCode()).thenReturn(200);
        when(trackResponse.body()).thenReturn(
            "{\"status\":true,\"data\":{\"awb_number\":\"XB-1\",\"status\":\"Picked Up\",\"shipment_info\":\"Collected\"}}"
        );

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String uri = request.uri().toString();
                if (uri.endsWith("/api/users/login")) {
                    return loginResponse;
                }
                if (uri.endsWith("/api/shipments2/track/XB-1")) {
                    return trackResponse;
                }
                throw new AssertionError("Unexpected HTTP request: " + uri);
            });

        Optional<XpressBeesClient.ForwardTrackingResult> result = client.fetchForwardTracking("XB-1");

        assertThat(result).isPresent();
        XpressBeesClient.ForwardTrackingResult tracking = result.orElseThrow();
        assertThat(tracking.awb()).isEqualTo("XB-1");
        assertThat(tracking.status()).isEqualTo("Picked Up");
    }

    @Test
    void isReversePickupConfigured_requiresBusinessAccountWarehouseAndCredentials() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.XpressBees config = properties.getXpressbees();
        config.setEmail("info@example.com");
        config.setPassword("secret");
        config.setOriginPincode("560074");
        config.setBusinessAccountName("MiniSnMore");
        config.setWarehouseLine1("Warehouse 1");
        XpressBeesClient configured = new XpressBeesClient(properties, new ObjectMapper());
        assertThat(configured.isReversePickupConfigured()).isTrue();

        ApplicationProperties empty = new ApplicationProperties();
        XpressBeesClient disabled = new XpressBeesClient(empty, new ObjectMapper());
        assertThat(disabled.isReversePickupConfigured()).isFalse();
    }

    private static String jwtWithExpiry(Instant expiresAt) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = "{\"exp\":" + expiresAt.getEpochSecond() + "}";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
