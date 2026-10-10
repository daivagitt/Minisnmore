package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class XpressBeesClientResidualBranchMatrix20261008Test {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidForwardRequests")
    void validateForwardShipmentRequest_reachesResidualInvalidBranches(
        String scenario,
        XpressBeesClient.ForwardShipmentRequest request,
        String message
    ) {
        assertThatThrownBy(
            () -> ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "validateForwardShipmentRequest", request)
        )
            .hasRootCauseInstanceOf(XpressBeesClient.XpressBeesApiException.class)
            .hasRootCauseMessage(message);
    }

    private static Stream<Arguments> invalidForwardRequests() {
        XpressBeesClient.ReversePickupAddress warehouse = address("Warehouse", "560074", "9876500000");
        XpressBeesClient.ReversePickupAddress customer = address("Customer", "560001", "9876543210");
        List<XpressBeesClient.ReversePickupLine> lines = List.of(line(1, BigDecimal.TEN));
        return Stream.of(
            Arguments.of(
                "missing destination",
                new XpressBeesClient.ForwardShipmentRequest("CASE", "ORD", warehouse, null, lines, null),
                "Warehouse origin, customer delivery address and shipment lines are required"
            ),
            Arguments.of(
                "empty lines after valid addresses",
                new XpressBeesClient.ForwardShipmentRequest("CASE", "ORD", warehouse, customer, List.of(), null),
                "Warehouse origin, customer delivery address and shipment lines are required"
            ),
            Arguments.of(
                "blank destination name",
                new XpressBeesClient.ForwardShipmentRequest(
                    "CASE",
                    "ORD",
                    warehouse,
                    new XpressBeesClient.ReversePickupAddress(" ", "9876543210", null, "Road", null, "City", "State", "560001"),
                    lines,
                    null
                ),
                "Valid customer delivery address is required"
            )
        );
    }

    @Test
    void validateForwardShipmentRequest_acceptsCompleteRequest() {
        assertThatCode(
            () ->
                ReflectionTestUtils.invokeMethod(
                    XpressBeesClient.class,
                    "validateForwardShipmentRequest",
                    forwardRequest(address("Customer", "560001", "9876543210"), List.of(line(1, BigDecimal.TEN)))
                )
        )
            .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidReverseRequests")
    void validateReversePickupRequest_reachesResidualInvalidBranches(
        String scenario,
        XpressBeesClient.ReversePickupRequest request,
        String message
    ) {
        assertThatThrownBy(
            () -> ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "validateReversePickupRequest", request)
        )
            .hasRootCauseInstanceOf(XpressBeesClient.XpressBeesApiException.class)
            .hasRootCauseMessage(message);
    }

    private static Stream<Arguments> invalidReverseRequests() {
        XpressBeesClient.ReversePickupAddress warehouse = address("Warehouse", "560074", "9876500000");
        XpressBeesClient.ReversePickupAddress customer = address("Customer", "560001", "9876543210");
        List<XpressBeesClient.ReversePickupLine> lines = List.of(line(1, BigDecimal.TEN));
        return Stream.of(
            Arguments.of("null request", null, "Return case reference is required"),
            Arguments.of(
                "missing destination",
                new XpressBeesClient.ReversePickupRequest("RET", "ORD", customer, null, lines, null),
                "Pickup address and return lines are required"
            ),
            Arguments.of(
                "empty lines after valid addresses",
                new XpressBeesClient.ReversePickupRequest("RET", "ORD", customer, warehouse, List.of(), null),
                "Pickup address and return lines are required"
            ),
            Arguments.of(
                "blank pickup city",
                new XpressBeesClient.ReversePickupRequest(
                    "RET",
                    "ORD",
                    new XpressBeesClient.ReversePickupAddress("Customer", "9876543210", null, "Road", null, " ", "State", "560001"),
                    warehouse,
                    lines,
                    null
                ),
                "Valid customer pickup address is required"
            ),
            Arguments.of(
                "invalid pickup phone",
                new XpressBeesClient.ReversePickupRequest(
                    "RET",
                    "ORD",
                    address("Customer", "560001", "123"),
                    warehouse,
                    lines,
                    null
                ),
                "Customer phone must be a 10-digit Indian mobile number"
            )
        );
    }

    @Test
    void validateReversePickupRequest_acceptsCompleteRequest() {
        assertThatCode(
            () ->
                ReflectionTestUtils.invokeMethod(
                    XpressBeesClient.class,
                    "validateReversePickupRequest",
                    reverseRequest(List.of(line(1, BigDecimal.TEN)))
                )
        )
            .doesNotThrowAnyException();
    }

    @Test
    void resolveForwardCourier_invalidPincodeFailsBeforeLogin() {
        XpressBeesClient client = client(configuredProperties());
        XpressBeesClient.ForwardShipmentRequest request = new XpressBeesClient.ForwardShipmentRequest(
            "CASE-1",
            "ORD-1",
            address("Warehouse", "560074", "9876500000"),
            address("Customer", "000000", "9876543210"),
            List.of(line(1, BigDecimal.TEN)),
            null
        );
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(client, "resolveForwardCourier", request))
            .hasRootCauseInstanceOf(XpressBeesClient.XpressBeesApiException.class)
            .hasRootCauseMessage("ExpressBees courier serviceability failed for pincode 000000: invalid pincode");
    }

    @ParameterizedTest(name = "forward serviceability HTTP {0}")
    @MethodSource("forwardCourierFailures")
    @SuppressWarnings("unchecked")
    void resolveForwardCourier_reportsLoginAndHttpFailures(int status, String loginBody, String expected) throws Exception {
        XpressBeesClient client = client(configuredProperties());
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", http);
        HttpResponse<String> first = response(status, loginBody);
        if (status == 200 && loginBody.contains("\"status\":true")) {
            HttpResponse<String> serviceability = response(503, "unavailable");
            when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(first, serviceability);
        } else {
            when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(first);
        }

        assertThatThrownBy(
            () -> ReflectionTestUtils.invokeMethod(client, "resolveForwardCourier", forwardRequest(address("Customer", "560001", "9876543210"), List.of(line(1, BigDecimal.TEN))))
        )
            .hasRootCauseInstanceOf(XpressBeesClient.XpressBeesApiException.class)
            .hasRootCauseMessage(expected);
    }

    private static Stream<Arguments> forwardCourierFailures() {
        return Stream.of(
            Arguments.of(401, "denied", "ExpressBees courier serviceability failed for pincode 560001: login failed"),
            Arguments.of(
                200,
                "{\"status\":true,\"data\":\"token\"}",
                "ExpressBees courier serviceability failed for pincode 560001: serviceability HTTP 503: unavailable"
            )
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveForwardCourier_fallsBackToFirstForwardCourier() throws Exception {
        XpressBeesClient client = client(configuredProperties());
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", http);
        HttpResponse<String> login = response(200, "{\"status\":true,\"data\":\"token\"}");
        HttpResponse<String> serviceability = response(
            200,
            "{\"data\":[{\"id\":\"\",\"name\":\"broken\"},{\"id\":\"r\",\"name\":\"Reverse\"},{\"id\":\"fallback\",\"name\":\"Economy\"}]}"
        );
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(login, serviceability);

        XpressBeesClient.CourierOption courier = ReflectionTestUtils.invokeMethod(
            client,
            "resolveForwardCourier",
            forwardRequest(address("Customer", "560001", "9876543210"), List.of(line(1, BigDecimal.TEN)))
        );
        assertThat(courier.id()).isEqualTo("fallback");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveForwardCourier_rejectsNonArrayServiceabilityData() throws Exception {
        XpressBeesClient client = client(configuredProperties());
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", http);
        HttpResponse<String> login = response(200, "{\"status\":true,\"data\":\"token\"}");
        HttpResponse<String> serviceability = response(200, "{\"status\":true,\"data\":{}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(login, serviceability);

        assertThatThrownBy(
            () -> ReflectionTestUtils.invokeMethod(
                client,
                "resolveForwardCourier",
                forwardRequest(address("Customer", "560001", "9876543210"), List.of(line(1, BigDecimal.TEN)))
            )
        )
            .hasRootCauseInstanceOf(XpressBeesClient.XpressBeesApiException.class)
            .hasRootCauseMessage("ExpressBees courier serviceability failed for pincode 560001: no serviceable couriers");
    }

    @ParameterizedTest(name = "cancel accepted by {0}")
    @MethodSource("acceptedCancelResponses")
    @SuppressWarnings("unchecked")
    void cancelForwardShipment_acceptsEveryProviderSuccessShape(String scenario, String body) throws Exception {
        ApplicationProperties properties = configuredProperties();
        properties.getXpressbees().setCancelShipmentPath("/api/shipments2/cancel");
        XpressBeesClient client = client(properties);
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", http);
        HttpResponse<String> login = response(200, "{\"status\":true,\"data\":\"token\"}");
        HttpResponse<String> cancellation = response(200, body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(login, cancellation);

        XpressBeesClient.CancelShipmentResult result = client.cancelForwardShipment(" AWB-9 ", null);

        assertThat(result.message()).isNotBlank();
    }

    private static Stream<Arguments> acceptedCancelResponses() {
        return Stream.of(
            Arguments.of("boolean status", "{\"status\":true,\"data\":{\"id\":\"1\"}}"),
            Arguments.of("text status", "{\"status\":\"success\",\"data\":{\"request_id\":\"2\"}}"),
            Arguments.of("legacy return code", "{\"status\":false,\"ReturnCode\":\"100\"}"),
            Arguments.of("British cancelled code", "{\"status\":false,\"data\":{\"code\":\"cancelled\"}}")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void accessToken_usesJwtExpiryAndRejectsHttpFailure() throws Exception {
        XpressBeesClient client = client(configuredProperties());
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", http);
        long expiry = Instant.now().plusSeconds(7200).getEpochSecond();
        String token =
            "header." +
            Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"exp\":" + expiry + "}").getBytes(StandardCharsets.UTF_8)) +
            ".signature";
        HttpResponse<String> login = response(200, "{\"status\":true,\"data\":\"" + token + "\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(login);

        assertThat((String) ReflectionTestUtils.invokeMethod(client, "accessToken")).isEqualTo(token);
        assertThat((Instant) ReflectionTestUtils.getField(client, "tokenExpiresAt")).isEqualTo(Instant.ofEpochSecond(expiry));

        XpressBeesClient denied = client(configuredProperties());
        HttpClient deniedHttp = mock(HttpClient.class);
        ReflectionTestUtils.setField(denied, "httpClient", deniedHttp);
        ReflectionTestUtils.setField(denied, "cachedToken", "expired-token");
        ReflectionTestUtils.setField(denied, "tokenExpiresAt", Instant.EPOCH);
        HttpResponse<String> deniedResponse = response(429, "rate limited");
        when(deniedHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(deniedResponse);
        assertThat((String) ReflectionTestUtils.invokeMethod(denied, "accessToken")).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("payloadLines")
    void tokenPayloadBuilders_coverOptionalLineAndDefaultBranches(
        String scenario,
        XpressBeesClient.ReversePickupLine line,
        boolean hasPrice
    ) throws Exception {
        XpressBeesClient client = client(configuredProperties());
        JsonNode forward = MAPPER.readTree(
            client.buildTokenForwardManifestBody(
                forwardRequest(address("Customer", "560001", "9876543210"), List.of(line)),
                " "
            )
        );
        JsonNode reverse = MAPPER.readTree(client.buildTokenReverseManifestBody(reverseRequest(List.of(line)), "reverse-1"));

        assertThat(forward.path("courier_id").asText()).isEqualTo("1");
        assertThat(forward.path("order_items").get(0).path("qty").asText()).isEqualTo("1");
        assertThat(forward.path("order_items").get(0).has("price")).isEqualTo(hasPrice);
        assertThat(reverse.path("order_items").get(0).path("qty").asText()).isEqualTo("1");
        assertThat(reverse.path("order_items").get(0).has("price")).isEqualTo(hasPrice);
    }

    private static Stream<Arguments> payloadLines() {
        return Stream.of(
            Arguments.of("zero quantity and null price", line(0, null), false),
            Arguments.of("negative quantity and positive price", line(-2, BigDecimal.ONE), true)
        );
    }

    private static XpressBeesClient client(ApplicationProperties properties) {
        return new XpressBeesClient(properties, MAPPER);
    }

    private static ApplicationProperties configuredProperties() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.XpressBees config = properties.getXpressbees();
        config.setEmail("carrier@example.com");
        config.setPassword("secret");
        config.setOriginPincode("560074");
        config.setBaseUrl("https://xpressbees.test");
        config.setBusinessAccountName("Mini and More");
        config.setWarehouseLine1("Warehouse road");
        config.setForwardManifestPath("/api/shipments2");
        config.setReverseManifestPath("/api/reverse");
        return properties;
    }

    private static XpressBeesClient.ForwardShipmentRequest forwardRequest(
        XpressBeesClient.ReversePickupAddress customer,
        List<XpressBeesClient.ReversePickupLine> lines
    ) {
        return new XpressBeesClient.ForwardShipmentRequest(
            "CASE-1",
            "ORD-1",
            address("Warehouse", "560074", "9876500000"),
            customer,
            lines,
            null,
            null,
            null,
            null
        );
    }

    private static XpressBeesClient.ReversePickupRequest reverseRequest(List<XpressBeesClient.ReversePickupLine> lines) {
        return new XpressBeesClient.ReversePickupRequest(
            "RET-1",
            "ORD-1",
            address("Customer", "560001", "9876543210"),
            address("Warehouse", "560074", "9876500000"),
            lines,
            null
        );
    }

    private static XpressBeesClient.ReversePickupAddress address(String name, String pincode, String phone) {
        return new XpressBeesClient.ReversePickupAddress(name, phone, null, "Road 1", null, "City", "State", pincode);
    }

    private static XpressBeesClient.ReversePickupLine line(int quantity, BigDecimal price) {
        return new XpressBeesClient.ReversePickupLine("Product", "SKU", quantity, price, 500);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
