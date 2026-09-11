package com.miniandmore.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.app.config.ApplicationProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class XpressBeesClient {

    private static final Logger LOG = LoggerFactory.getLogger(XpressBeesClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String USER_AGENT = "MiniSnMore/1.0 (+https://minisnmore.com)";

    private final ApplicationProperties.XpressBees config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public XpressBeesClient(ApplicationProperties applicationProperties, ObjectMapper objectMapper) {
        this.config = applicationProperties.getXpressbees();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient
            .newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    public boolean isConfigured() {
        return (
            StringUtils.isNotBlank(config.getEmail()) &&
            StringUtils.isNotBlank(config.getPassword()) &&
            StringUtils.isNotBlank(config.getOriginPincode())
        );
    }

    public boolean isReversePickupConfigured() {
        if (StringUtils.isBlank(config.getReverseManifestPath()) || StringUtils.isBlank(config.getBusinessAccountName())) {
            return false;
        }
        if (StringUtils.isBlank(config.getOriginPincode()) || StringUtils.isBlank(config.getWarehouseLine1())) {
            return false;
        }
        if (config.usesLegacyAuth()) {
            return StringUtils.isNotBlank(config.getXbKey());
        }
        return isConfigured();
    }

    public Optional<ServiceabilityResult> checkServiceability(String destinationPincode) {
        if (!isConfigured() || destinationPincode == null || !destinationPincode.matches("^[1-9]\\d{5}$")) {
            return Optional.empty();
        }
        try {
            String token = accessToken();
            if (token == null) {
                return Optional.empty();
            }
            String body = objectMapper.writeValueAsString(
                Map.of(
                    "origin",
                    config.getOriginPincode(),
                    "destination",
                    destinationPincode,
                    "payment_type",
                    StringUtils.defaultIfBlank(config.getPaymentType(), "prepaid")
                )
            );
            String url =
                config.getBaseUrl().replaceAll("/$", "") +
                "/api/courier/serviceability/pincode/" +
                destinationPincode;
            HttpResponse<String> response = postJson(url, body, token);
            if (response.statusCode() != 200) {
                LOG.debug("XpressBees serviceability HTTP {} for pincode {}", response.statusCode(), destinationPincode);
                return Optional.empty();
            }
            return parseServiceabilityResponse(response.body(), destinationPincode);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            LOG.debug("XpressBees serviceability failed for pincode {}: {}", destinationPincode, ex.getMessage());
            return Optional.empty();
        }
    }

    public boolean isForwardShipmentConfigured() {
        if (StringUtils.isBlank(config.getForwardManifestPath()) || StringUtils.isBlank(config.getBusinessAccountName())) {
            return false;
        }
        if (StringUtils.isBlank(config.getOriginPincode()) || StringUtils.isBlank(config.getWarehouseLine1())) {
            return false;
        }
        if (config.usesLegacyAuth()) {
            return StringUtils.isNotBlank(config.getXbKey());
        }
        return isConfigured();
    }

    public ForwardShipmentResult createForwardShipment(ForwardShipmentRequest request) throws XpressBeesApiException {
        if (!isForwardShipmentConfigured()) {
            throw new XpressBeesApiException("ExpressBees forward shipment is not configured");
        }
        validateForwardShipmentRequest(request);
        try {
            String body = config.usesLegacyAuth() ? buildLegacyForwardManifestBody(request) : buildTokenForwardManifestBody(request);
            String token = config.usesLegacyAuth() ? null : accessToken();
            if (!config.usesLegacyAuth() && token == null) {
                throw new XpressBeesApiException("ExpressBees login failed");
            }
            HttpResponse<String> response = postJson(resolveForwardManifestUrl(), body, token);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new XpressBeesApiException(
                    "ExpressBees forward shipment HTTP " + response.statusCode() + ": " + abbreviate(response.body(), 400)
                );
            }
            return parseForwardShipmentResponse(response.body(), request);
        } catch (XpressBeesApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new XpressBeesApiException("ExpressBees forward shipment interrupted");
        } catch (Exception ex) {
            throw new XpressBeesApiException("ExpressBees forward shipment failed: " + ex.getMessage(), ex);
        }
    }

    public ReversePickupResult createReversePickup(ReversePickupRequest request) throws XpressBeesApiException {
        if (!isReversePickupConfigured()) {
            throw new XpressBeesApiException("ExpressBees reverse pickup is not configured");
        }
        validateReversePickupRequest(request);
        try {
            String body = config.usesLegacyAuth() ? buildLegacyReverseManifestBody(request) : buildTokenReverseManifestBody(request);
            String token = config.usesLegacyAuth() ? null : accessToken();
            if (!config.usesLegacyAuth() && token == null) {
                throw new XpressBeesApiException("ExpressBees login failed");
            }
            HttpResponse<String> response = postJson(resolveReverseManifestUrl(), body, token);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new XpressBeesApiException(
                    "ExpressBees reverse pickup HTTP " + response.statusCode() + ": " + abbreviate(response.body(), 400)
                );
            }
            return parseReversePickupResponse(response.body(), request);
        } catch (XpressBeesApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new XpressBeesApiException("ExpressBees reverse pickup interrupted");
        } catch (Exception ex) {
            throw new XpressBeesApiException("ExpressBees reverse pickup failed: " + ex.getMessage(), ex);
        }
    }

    public CancelShipmentResult cancelForwardShipment(String awb, String orderNumber) throws XpressBeesApiException {
        if (!isForwardShipmentConfigured() || StringUtils.isBlank(config.getCancelShipmentPath())) {
            throw new XpressBeesApiException("ExpressBees shipment cancellation is not configured");
        }
        if (StringUtils.isBlank(awb)) {
            throw new XpressBeesApiException("AWB is required to cancel a shipment");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (config.usesLegacyAuth()) {
                payload.put("XBkey", config.getXbKey());
                payload.put("AWBNumber", awb.trim());
                payload.put("OrderNo", StringUtils.defaultString(orderNumber));
            } else {
                payload.put("awb", awb.trim());
                payload.put("order_number", StringUtils.defaultString(orderNumber));
            }
            String token = config.usesLegacyAuth() ? null : accessToken();
            if (!config.usesLegacyAuth() && token == null) {
                throw new XpressBeesApiException("ExpressBees login failed");
            }
            HttpResponse<String> response = postJson(resolveConfiguredUrl(config.getCancelShipmentPath()), objectMapper.writeValueAsString(payload), token);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new XpressBeesApiException(
                    "ExpressBees shipment cancellation HTTP " + response.statusCode() + ": " + abbreviate(response.body(), 400)
                );
            }
            JsonNode root = objectMapper.readTree(response.body());
            String code = firstText(root.path("data"), "status", "code", "return_code");
            boolean accepted =
                root.path("status").asBoolean(false) ||
                "success".equalsIgnoreCase(text(root, "status")) ||
                "100".equals(text(root, "ReturnCode")) ||
                "cancelled".equalsIgnoreCase(code) ||
                "canceled".equalsIgnoreCase(code);
            if (!accepted) {
                String message = firstText(root, "message", "error", "ReturnMessage");
                throw new XpressBeesApiException(
                    StringUtils.defaultIfBlank(message, "ExpressBees rejected shipment cancellation")
                );
            }
            return new CancelShipmentResult(
                firstText(root.path("data"), "request_id", "id", "cancellation_id"),
                StringUtils.defaultIfBlank(firstText(root, "message", "ReturnMessage"), "Cancellation accepted")
            );
        } catch (XpressBeesApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new XpressBeesApiException("ExpressBees shipment cancellation interrupted");
        } catch (Exception ex) {
            throw new XpressBeesApiException("ExpressBees shipment cancellation failed: " + ex.getMessage(), ex);
        }
    }

    Optional<ServiceabilityResult> parseServiceabilityResponse(String body, String destinationPincode) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("status").asBoolean(false)) {
                return Optional.empty();
            }
            JsonNode data = root.path("data");
            if (data.isTextual()) {
                return Optional.empty();
            }
            if (!data.isArray() || data.isEmpty()) {
                return Optional.empty();
            }
            JsonNode best = data.get(0);
            int minEddDays = Integer.MAX_VALUE;
            for (JsonNode option : data) {
                int eddDays = option.path("eddDays").asInt(Integer.MAX_VALUE);
                if (eddDays < minEddDays) {
                    minEddDays = eddDays;
                    best = option;
                }
            }
            String city = text(best, "destination_city");
            String state = text(best, "destination_state");
            if (city.isBlank() || state.isBlank()) {
                return Optional.empty();
            }
            int eddDays = best.path("eddDays").asInt(-1);
            return Optional.of(new ServiceabilityResult(city, state, formatEta(eddDays), eddDays, destinationPincode));
        } catch (Exception ex) {
            LOG.debug("Could not parse XpressBees serviceability response for {}: {}", destinationPincode, ex.getMessage());
            return Optional.empty();
        }
    }

    ForwardShipmentResult parseForwardShipmentResponse(String body, ForwardShipmentRequest request) throws XpressBeesApiException {
        try {
            JsonNode root = objectMapper.readTree(body);
            Optional<ForwardShipmentResult> legacy = parseLegacyForwardManifestResponse(root, request);
            if (legacy.isPresent()) {
                return legacy.orElseThrow();
            }
            if (root.path("status").asBoolean(false)) {
                JsonNode data = root.path("data");
                String awb = firstText(data, "awb", "awb_number", "AWBNumber", "AirWayBillNO", "airwaybill");
                if (StringUtils.isBlank(awb)) {
                    awb = request.preAssignedAwb();
                }
                if (StringUtils.isBlank(awb)) {
                    throw new XpressBeesApiException("ExpressBees forward shipment succeeded but AWB was missing");
                }
                return new ForwardShipmentResult(
                    awb,
                    firstText(data, "tracking_url", "trackingUrl", "TrackingUrl"),
                    firstText(data, "label_url", "labelUrl", "LabelUrl", "shipping_label"),
                    firstText(data, "provider_request_id", "request_id", "shipment_id", "id"),
                    text(root, "message")
                );
            }
            String message = firstText(root, "message", "error", "ReturnMessage");
            throw new XpressBeesApiException(
                StringUtils.defaultIfBlank(message, "ExpressBees forward shipment rejected: " + abbreviate(body, 400))
            );
        } catch (XpressBeesApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new XpressBeesApiException("Could not parse ExpressBees forward shipment response", ex);
        }
    }

    ReversePickupResult parseReversePickupResponse(String body, ReversePickupRequest request) throws XpressBeesApiException {
        try {
            JsonNode root = objectMapper.readTree(body);
            Optional<ReversePickupResult> legacy = parseLegacyReversePickupResponse(root, request);
            if (legacy.isPresent()) {
                return legacy.orElseThrow();
            }
            if (root.path("status").asBoolean(false)) {
                JsonNode data = root.path("data");
                String awb = firstText(data, "awb", "awb_number", "AWBNumber", "AirWayBillNO", "airwaybill");
                if (StringUtils.isBlank(awb)) {
                    awb = request.preAssignedAwb();
                }
                if (StringUtils.isBlank(awb)) {
                    throw new XpressBeesApiException("ExpressBees reverse pickup succeeded but AWB was missing");
                }
                return new ReversePickupResult(
                    awb,
                    firstText(data, "tracking_url", "trackingUrl", "TrackingUrl"),
                    firstText(data, "pickup_window", "pickupWindow", "PickupDate", "scheduled_pickup"),
                    firstText(data, "provider_request_id", "request_id", "shipment_id", "id"),
                    text(root, "message")
                );
            }
            String message = firstText(root, "message", "error", "ReturnMessage");
            throw new XpressBeesApiException(
                StringUtils.defaultIfBlank(message, "ExpressBees reverse pickup rejected: " + abbreviate(body, 400))
            );
        } catch (XpressBeesApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new XpressBeesApiException("Could not parse ExpressBees reverse pickup response", ex);
        }
    }

    static String formatEta(int eddDays) {
        if (eddDays <= 0) {
            return "Same day";
        }
        if (eddDays == 1) {
            return "1 day";
        }
        return eddDays + " days";
    }

    public String buildTrackingUrl(String awb) {
        if (StringUtils.isBlank(awb)) {
            return null;
        }
        String template = StringUtils.defaultIfBlank(config.getTrackingUrlTemplate(), "https://www.xpressbees.com/track/?awb={awb}");
        return template.replace("{awb}", awb.trim());
    }

    private Optional<ForwardShipmentResult> parseLegacyForwardManifestResponse(JsonNode root, ForwardShipmentRequest request)
        throws XpressBeesApiException {
        JsonNode details = root.path("ForwardManifestDetails");
        if (!details.isArray() || details.isEmpty()) {
            details = root.path("ShipmentManifestDetails");
        }
        if (!details.isArray() || details.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = details.get(0);
        String returnCode = text(first, "ReturnCode");
        if (!"100".equals(returnCode)) {
            throw new XpressBeesApiException(
                StringUtils.defaultIfBlank(text(first, "ReturnMessage"), "ExpressBees forward shipment failed with code " + returnCode)
            );
        }
        String awb = firstText(first, "AWBNumber", "AirWayBillNO", "awb");
        if (StringUtils.isBlank(awb)) {
            awb = request.preAssignedAwb();
        }
        if (StringUtils.isBlank(awb)) {
            throw new XpressBeesApiException("ExpressBees forward shipment succeeded but AWB was missing");
        }
        return Optional.of(
            new ForwardShipmentResult(
                awb,
                buildTrackingUrl(awb),
                firstText(first, "LabelUrl", "ShippingLabel", "label_url"),
                firstText(first, "RequestId", "ProviderRequestId", "OrderNo"),
                text(first, "ReturnMessage")
            )
        );
    }

    private Optional<ReversePickupResult> parseLegacyReversePickupResponse(JsonNode root, ReversePickupRequest request)
        throws XpressBeesApiException {
        JsonNode details = root.path("ReverseManifestDetails");
        if (!details.isArray() || details.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = details.get(0);
        String returnCode = text(first, "ReturnCode");
        if (!"100".equals(returnCode)) {
            throw new XpressBeesApiException(
                StringUtils.defaultIfBlank(text(first, "ReturnMessage"), "ExpressBees reverse pickup failed with code " + returnCode)
            );
        }
        String awb = firstText(first, "AWBNumber", "AirWayBillNO", "awb");
        if (StringUtils.isBlank(awb)) {
            awb = request.preAssignedAwb();
        }
        if (StringUtils.isBlank(awb)) {
            throw new XpressBeesApiException("ExpressBees reverse pickup succeeded but AWB was missing");
        }
        return Optional.of(
            new ReversePickupResult(
                awb,
                buildTrackingUrl(awb),
                firstText(first, "PickupDate", "PickupWindow", "ScheduledPickupDate"),
                firstText(first, "RequestId", "ProviderRequestId", "OrderNo"),
                text(first, "ReturnMessage")
            )
        );
    }

    private String buildTokenForwardManifestBody(ForwardShipmentRequest request) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("order_id", request.caseReference());
        root.put("order_number", request.caseReference());
        root.put("business_account_name", config.getBusinessAccountName());
        root.put("payment_type", resolvePaymentType(request));
        if (StringUtils.isNotBlank(request.preAssignedAwb())) {
            root.put("awb", request.preAssignedAwb());
        }

        ObjectNode pickup = root.putObject("pickup");
        writeAddress(pickup, request.shipFrom());

        ObjectNode delivery = root.putObject("delivery");
        writeAddress(delivery, request.deliverTo());

        ArrayNode products = root.putArray("products");
        for (ReversePickupLine line : request.lines()) {
            ObjectNode product = products.addObject();
            product.put("name", line.productName());
            product.put("sku", line.sku());
            product.put("quantity", line.quantity());
            if (line.price() != null) {
                product.put("price", line.price());
            }
            product.put("weight_grams", line.weightGrams());
        }
        root.put("weight_grams", totalWeightGrams(request.lines()));
        return objectMapper.writeValueAsString(root);
    }

    private String buildLegacyForwardManifestBody(ForwardShipmentRequest request) throws Exception {
        ReversePickupLine primary = request.lines().get(0);
        ReversePickupAddress warehouse = request.shipFrom();
        ReversePickupAddress customer = request.deliverTo();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("XBkey", config.getXbKey());
        body.put("BusinessAccountName", config.getBusinessAccountName());
        body.put("PaymentType", resolvePaymentType(request));
        if (StringUtils.isNotBlank(request.preAssignedAwb())) {
            body.put("AirWayBillNO", request.preAssignedAwb());
        }
        body.put("OrderNo", request.caseReference());
        body.put("SubOrderNo", request.caseReference());
        body.put("CustomerName", customer.name());
        body.put("CustomerAddress", joinAddress(customer.line1(), customer.line2()));
        body.put("CustomerCity", customer.city());
        body.put("CustomerState", customer.state());
        body.put("CustomerPincode", customer.pincode());
        body.put("CustomerMobile", customer.phone());
        if (StringUtils.isNotBlank(customer.email())) {
            body.put("CustomerEmail", customer.email());
        }
        body.put("ProductName", primary.productName());
        body.put("ProductQty", String.valueOf(primary.quantity()));
        if (primary.price() != null) {
            body.put("ProductPrice", primary.price().toPlainString());
        }
        body.put("ProductWeight", String.valueOf(primary.weightGrams()));
        body.put("PickupName", warehouse.name());
        body.put("PickupAddress", joinAddress(warehouse.line1(), warehouse.line2()));
        body.put("PickupCity", warehouse.city());
        body.put("PickupState", warehouse.state());
        body.put("PickupPincode", warehouse.pincode());
        if (StringUtils.isNotBlank(warehouse.phone())) {
            body.put("PickupMobile", warehouse.phone());
        }
        return objectMapper.writeValueAsString(body);
    }

    private String buildTokenReverseManifestBody(ReversePickupRequest request) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("order_id", request.caseReference());
        root.put("order_number", request.caseReference());
        root.put("business_account_name", config.getBusinessAccountName());
        root.put("payment_type", StringUtils.defaultIfBlank(config.getPaymentType(), "prepaid"));
        if (StringUtils.isNotBlank(request.preAssignedAwb())) {
            root.put("awb", request.preAssignedAwb());
        }
        if (StringUtils.isNotBlank(config.getPickupVendorCode())) {
            root.put("pickup_vendor_code", config.getPickupVendorCode());
        }

        ObjectNode pickup = root.putObject("pickup");
        writeAddress(pickup, request.pickupFrom());

        ObjectNode delivery = root.putObject("delivery");
        writeAddress(delivery, request.deliverTo());

        ArrayNode products = root.putArray("products");
        for (ReversePickupLine line : request.lines()) {
            ObjectNode product = products.addObject();
            product.put("name", line.productName());
            product.put("sku", line.sku());
            product.put("quantity", line.quantity());
            if (line.price() != null) {
                product.put("price", line.price());
            }
            product.put("weight_grams", line.weightGrams());
        }
        root.put("weight_grams", totalWeightGrams(request.lines()));
        return objectMapper.writeValueAsString(root);
    }

    private String buildLegacyReverseManifestBody(ReversePickupRequest request) throws Exception {
        ReversePickupLine primary = request.lines().get(0);
        ReversePickupAddress pickup = request.pickupFrom();
        ReversePickupAddress warehouse = request.deliverTo();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("XBkey", config.getXbKey());
        body.put("BusinessAccountName", config.getBusinessAccountName());
        if (StringUtils.isNotBlank(request.preAssignedAwb())) {
            body.put("AirWayBillNO", request.preAssignedAwb());
        }
        body.put("OrderNo", request.caseReference());
        body.put("SubOrderNo", request.caseReference());
        body.put("CustomerName", pickup.name());
        body.put("CustomerAddress", joinAddress(pickup.line1(), pickup.line2()));
        body.put("CustomerCity", pickup.city());
        body.put("CustomerState", pickup.state());
        body.put("CustomerPincode", pickup.pincode());
        body.put("CustomerMobile", pickup.phone());
        if (StringUtils.isNotBlank(pickup.email())) {
            body.put("CustomerEmail", pickup.email());
        }
        body.put("ProductName", primary.productName());
        body.put("ProductQty", String.valueOf(primary.quantity()));
        if (primary.price() != null) {
            body.put("ProductPrice", primary.price().toPlainString());
        }
        body.put("ProductWeight", String.valueOf(primary.weightGrams()));
        body.put("PickupType", "Vendor");
        body.put("RTOName", warehouse.name());
        body.put("RTOAddress", joinAddress(warehouse.line1(), warehouse.line2()));
        body.put("RTOCity", warehouse.city());
        body.put("RTOState", warehouse.state());
        body.put("RTOPincode", warehouse.pincode());
        if (StringUtils.isNotBlank(warehouse.phone())) {
            body.put("RTOMobile", warehouse.phone());
        }
        return objectMapper.writeValueAsString(body);
    }

    private static void writeAddress(ObjectNode node, ReversePickupAddress address) {
        node.put("name", address.name());
        node.put("phone", address.phone());
        if (StringUtils.isNotBlank(address.email())) {
            node.put("email", address.email());
        }
        node.put("address_line1", address.line1());
        if (StringUtils.isNotBlank(address.line2())) {
            node.put("address_line2", address.line2());
        }
        node.put("city", address.city());
        node.put("state", address.state());
        node.put("pincode", address.pincode());
    }

    private static void validateForwardShipmentRequest(ForwardShipmentRequest request) throws XpressBeesApiException {
        if (request == null || StringUtils.isBlank(request.caseReference())) {
            throw new XpressBeesApiException("Shipment case reference is required");
        }
        if (request.shipFrom() == null || request.deliverTo() == null || request.lines() == null || request.lines().isEmpty()) {
            throw new XpressBeesApiException("Warehouse origin, customer delivery address and shipment lines are required");
        }
        ReversePickupAddress delivery = request.deliverTo();
        if (
            StringUtils.isAnyBlank(delivery.name(), delivery.phone(), delivery.line1(), delivery.city(), delivery.state(), delivery.pincode()) ||
            !delivery.pincode().matches("^[1-9]\\d{5}$")
        ) {
            throw new XpressBeesApiException("Valid customer delivery address is required");
        }
    }

    private static void validateReversePickupRequest(ReversePickupRequest request) throws XpressBeesApiException {
        if (request == null || StringUtils.isBlank(request.caseReference())) {
            throw new XpressBeesApiException("Return case reference is required");
        }
        if (request.pickupFrom() == null || request.deliverTo() == null || request.lines() == null || request.lines().isEmpty()) {
            throw new XpressBeesApiException("Pickup address and return lines are required");
        }
        ReversePickupAddress pickup = request.pickupFrom();
        if (
            StringUtils.isAnyBlank(pickup.name(), pickup.phone(), pickup.line1(), pickup.city(), pickup.state(), pickup.pincode()) ||
            !pickup.pincode().matches("^[1-9]\\d{5}$")
        ) {
            throw new XpressBeesApiException("Valid customer pickup address is required");
        }
    }

    private String resolvePaymentType(ForwardShipmentRequest request) {
        return StringUtils.defaultIfBlank(request.paymentType(), StringUtils.defaultIfBlank(config.getPaymentType(), "prepaid"));
    }

    private int totalWeightGrams(List<ReversePickupLine> lines) {
        int total = 0;
        for (ReversePickupLine line : lines) {
            total += Math.max(1, line.weightGrams()) * Math.max(1, line.quantity());
        }
        return Math.max(total, Math.max(1, config.getDefaultWeightGrams()));
    }

    private String resolveForwardManifestUrl() {
        String path = StringUtils.trimToEmpty(config.getForwardManifestPath());
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return config.getBaseUrl().replaceAll("/$", "") + path;
    }

    private String resolveReverseManifestUrl() {
        String path = StringUtils.trimToEmpty(config.getReverseManifestPath());
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return config.getBaseUrl().replaceAll("/$", "") + path;
    }

    private String resolveConfiguredUrl(String configuredPath) {
        String path = StringUtils.trimToEmpty(configuredPath);
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return config.getBaseUrl().replaceAll("/$", "") + path;
    }

    private HttpResponse<String> postJson(String url, String body, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest
            .newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (StringUtils.isNotBlank(bearerToken)) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String accessToken() throws Exception {
        Instant now = Instant.now();
        if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(300))) {
            return cachedToken;
        }
        synchronized (this) {
            now = Instant.now();
            if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(300))) {
                return cachedToken;
            }
            String loginBody = objectMapper.writeValueAsString(
                Map.of("email", config.getEmail(), "password", config.getPassword())
            );
            String loginUrl = config.getBaseUrl().replaceAll("/$", "") + "/api/users/login";
            HttpResponse<String> response = postJson(loginUrl, loginBody, null);
            if (response.statusCode() != 200) {
                LOG.warn("XpressBees login failed with HTTP {}", response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("status").asBoolean(false)) {
                LOG.warn("XpressBees login rejected");
                return null;
            }
            String token = root.path("data").asText("");
            if (token.isBlank()) {
                return null;
            }
            cachedToken = token;
            tokenExpiresAt = jwtExpiry(token).orElse(now.plus(Duration.ofHours(2)));
            return cachedToken;
        }
    }

    private Optional<Instant> jwtExpiry(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(payloadJson);
            long exp = payload.path("exp").asLong(0L);
            if (exp <= 0L) {
                return Optional.empty();
            }
            return Optional.of(Instant.ofEpochSecond(exp));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String joinAddress(String line1, String line2) {
        if (StringUtils.isBlank(line2)) {
            return StringUtils.defaultString(line1).trim();
        }
        return (StringUtils.defaultString(line1) + " " + line2).trim();
    }

    private static String abbreviate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }

    public record ServiceabilityResult(String city, String state, String eta, int eddDays, String pincode) {}

    public record ReversePickupAddress(
        String name,
        String phone,
        String email,
        String line1,
        String line2,
        String city,
        String state,
        String pincode
    ) {}

    public record ReversePickupLine(String productName, String sku, int quantity, BigDecimal price, int weightGrams) {}

    public record ReversePickupRequest(
        String caseReference,
        String orderNumber,
        ReversePickupAddress pickupFrom,
        ReversePickupAddress deliverTo,
        List<ReversePickupLine> lines,
        String preAssignedAwb
    ) {
        public ReversePickupRequest {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public record ForwardShipmentRequest(
        String caseReference,
        String orderNumber,
        ReversePickupAddress shipFrom,
        ReversePickupAddress deliverTo,
        List<ReversePickupLine> lines,
        String preAssignedAwb,
        String paymentType
    ) {
        public ForwardShipmentRequest {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        public ForwardShipmentRequest(
            String caseReference,
            String orderNumber,
            ReversePickupAddress shipFrom,
            ReversePickupAddress deliverTo,
            List<ReversePickupLine> lines,
            String preAssignedAwb
        ) {
            this(caseReference, orderNumber, shipFrom, deliverTo, lines, preAssignedAwb, null);
        }
    }

    public record ForwardShipmentResult(
        String awb,
        String trackingUrl,
        String labelUrl,
        String providerRequestId,
        String message
    ) {
        public ForwardShipmentResult {
            awb = awb == null ? null : awb.trim();
        }
    }

    public record ReversePickupResult(String awb, String trackingUrl, String pickupWindow, String providerRequestId, String message) {
        public ReversePickupResult {
            awb = awb == null ? null : awb.trim();
        }
    }

    public record CancelShipmentResult(String providerRequestId, String message) {}

    public static class XpressBeesApiException extends Exception {

        public XpressBeesApiException(String message) {
            super(message);
        }

        public XpressBeesApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
