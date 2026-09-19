package com.miniandmore.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.service.OrderPackageService;
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
            String body = config.usesLegacyAuth()
                ? buildLegacyForwardManifestBody(request)
                : buildTokenForwardManifestBody(
                    request,
                    resolveForwardCourierId(
                        request.deliverTo().pincode(),
                        courierSelectionWeightGrams(request),
                        request.courierId()
                    )
                );
            String token = config.usesLegacyAuth() ? null : accessToken();
            if (!config.usesLegacyAuth() && token == null) {
                throw new XpressBeesApiException("ExpressBees login failed");
            }
            HttpResponse<String> response = postJson(resolveForwardManifestUrl(), body, token);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new XpressBeesApiException(providerFailureMessage("forward shipment", response));
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
                throw new XpressBeesApiException(providerFailureMessage("reverse pickup", response));
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
                throw new XpressBeesApiException(providerFailureMessage("shipment cancellation", response));
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
                    firstText(data, "label_url", "labelUrl", "LabelUrl", "shipping_label", "label"),
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

    public Optional<ForwardTrackingResult> fetchForwardTracking(String awb) {
        if (!isConfigured() || StringUtils.isBlank(awb)) {
            return Optional.empty();
        }
        try {
            String token = accessToken();
            if (token == null) {
                return Optional.empty();
            }
            String url = config.getBaseUrl().replaceAll("/$", "") + "/api/shipments2/track/" + awb.trim();
            HttpResponse<String> response = getJson(url, token);
            if (response.statusCode() != 200) {
                LOG.debug("XpressBees tracking HTTP {} for AWB {}", response.statusCode(), awb);
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("status").asBoolean(false)) {
                return Optional.empty();
            }
            JsonNode data = root.path("data");
            String status = firstText(data, "status", "shipment_status", "current_status");
            if (StringUtils.isBlank(status)) {
                return Optional.empty();
            }
            return Optional.of(
                new ForwardTrackingResult(
                    StringUtils.defaultIfBlank(firstText(data, "awb_number", "awb"), awb.trim()),
                    status,
                    firstText(data, "shipment_info", "message", "remark")
                )
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            LOG.debug("XpressBees tracking failed for AWB {}: {}", awb, ex.getMessage());
            return Optional.empty();
        }
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

    String buildTokenForwardManifestBody(ForwardShipmentRequest request, String courierId) throws Exception {
        String paymentType = resolvePaymentType(request).toLowerCase(Locale.ROOT);
        boolean cod = "cod".equals(paymentType);
        BigDecimal orderAmount = firstAmount(
            firstPositiveAmount(request.orderAmount(), sumLineValue(request.lines())),
            BigDecimal.ZERO
        );
        BigDecimal collectable = firstAmount(
            request.collectableAmount(),
            cod ? orderAmount : BigDecimal.ZERO
        );
        int weightGrams = packageWeightGrams(request);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("order_id", request.caseReference());
        root.put("order_number", request.caseReference());
        root.put("payment_type", paymentType);
        root.put("order_amount", orderAmount);
        root.put("collectable_amount", collectable);
        root.put("cod_charges", 0);
        root.put("shipping_charges", 0);
        root.put("discount", 0);
        root.put("package_weight", weightGrams);
        root.put("package_length", firstPositive(request.packageLengthCm(), 10));
        root.put("package_breadth", firstPositive(request.packageBreadthCm(), 10));
        root.put("package_height", firstPositive(request.packageHeightCm(), 10));
        root.put("request_auto_pickup", "yes");
        root.put("courier_id", StringUtils.defaultIfBlank(courierId, "1"));
        if (StringUtils.isNotBlank(request.preAssignedAwb())) {
            root.put("awb", request.preAssignedAwb());
        }

        ReversePickupAddress warehouse = request.shipFrom();
        ObjectNode pickup = root.putObject("pickup");
        pickup.put("warehouse_name", StringUtils.defaultIfBlank(config.getWarehouseName(), warehouse.name()));
        pickup.put("name", warehouse.name());
        pickup.put("address", joinAddress(warehouse.line1(), warehouse.line2()));
        pickup.put("address_2", StringUtils.defaultString(warehouse.line2()));
        pickup.put("city", warehouse.city());
        pickup.put("state", warehouse.state());
        pickup.put("pincode", warehouse.pincode());
        pickup.put("phone", indianMobile10(warehouse.phone()));

        ReversePickupAddress customer = request.deliverTo();
        ObjectNode consignee = root.putObject("consignee");
        consignee.put("name", customer.name());
        consignee.put("address", joinAddress(customer.line1(), customer.line2()));
        consignee.put("address_2", StringUtils.defaultString(customer.line2()));
        consignee.put("city", customer.city());
        consignee.put("state", customer.state());
        consignee.put("pincode", customer.pincode());
        consignee.put("phone", indianMobile10(customer.phone()));
        if (StringUtils.isNotBlank(customer.email())) {
            consignee.put("email", customer.email());
        }

        ArrayNode items = root.putArray("order_items");
        for (ReversePickupLine line : request.lines()) {
            ObjectNode item = items.addObject();
            item.put("name", line.productName());
            item.put("sku", line.sku());
            item.put("qty", String.valueOf(Math.max(1, line.quantity())));
            if (line.price() != null) {
                item.put("price", line.price().toPlainString());
            }
        }
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
        if ("cod".equalsIgnoreCase(resolvePaymentType(request))) {
            BigDecimal collectable = firstAmount(request.collectableAmount(), firstPositiveAmount(request.orderAmount(), sumLineValue(request.lines())));
            if (collectable != null) {
                body.put("CollectableAmount", collectable.toPlainString());
            }
        }
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
        body.put("CustomerMobile", indianMobile10(customer.phone()));
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
            body.put("PickupMobile", indianMobile10(warehouse.phone()));
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
        body.put("CustomerMobile", indianMobile10(pickup.phone()));
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
            body.put("RTOMobile", indianMobile10(warehouse.phone()));
        }
        return objectMapper.writeValueAsString(body);
    }

    private static void writeAddress(ObjectNode node, ReversePickupAddress address) {
        node.put("name", address.name());
        node.put("phone", indianMobile10(address.phone()));
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
            StringUtils.isAnyBlank(delivery.name(), delivery.line1(), delivery.city(), delivery.state(), delivery.pincode()) ||
            !delivery.pincode().matches("^[1-9]\\d{5}$")
        ) {
            throw new XpressBeesApiException("Valid customer delivery address is required");
        }
        if (indianMobile10(delivery.phone()) == null) {
            throw new XpressBeesApiException("Customer phone must be a 10-digit Indian mobile number");
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
            StringUtils.isAnyBlank(pickup.name(), pickup.line1(), pickup.city(), pickup.state(), pickup.pincode()) ||
            !pickup.pincode().matches("^[1-9]\\d{5}$")
        ) {
            throw new XpressBeesApiException("Valid customer pickup address is required");
        }
        if (indianMobile10(pickup.phone()) == null) {
            throw new XpressBeesApiException("Customer phone must be a 10-digit Indian mobile number");
        }
    }

    private String resolvePaymentType(ForwardShipmentRequest request) {
        return StringUtils.defaultIfBlank(request.paymentType(), StringUtils.defaultIfBlank(config.getPaymentType(), "prepaid"));
    }

    private static BigDecimal sumLineValue(List<ReversePickupLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReversePickupLine line : lines) {
            if (line.price() == null) {
                continue;
            }
            total = total.add(line.price().multiply(BigDecimal.valueOf(Math.max(1, line.quantity()))));
        }
        return total;
    }

    private static BigDecimal firstPositiveAmount(BigDecimal preferred, BigDecimal fallback) {
        if (preferred != null && preferred.compareTo(BigDecimal.ZERO) > 0) {
            return preferred;
        }
        if (fallback != null && fallback.compareTo(BigDecimal.ZERO) > 0) {
            return fallback;
        }
        return preferred != null ? preferred : fallback;
    }

    private static BigDecimal firstAmount(BigDecimal preferred, BigDecimal fallback) {
        return preferred != null ? preferred : (fallback != null ? fallback : BigDecimal.ZERO);
    }

    public List<CourierOption> listForwardCourierOptions(String destinationPincode, int weightGrams) {
        if (!isConfigured() || destinationPincode == null || !destinationPincode.matches("^[1-9]\\d{5}$")) {
            return List.of();
        }
        try {
            String token = accessToken();
            if (token == null) {
                return List.of();
            }
            String url =
                config.getBaseUrl().replaceAll("/$", "") +
                "/api/courier/serviceability/pincode/" +
                destinationPincode;
            HttpResponse<String> response = getJson(url, token);
            if (response.statusCode() != 200) {
                LOG.debug("XpressBees courier options HTTP {} for pincode {}", response.statusCode(), destinationPincode);
                return List.of();
            }
            return parseForwardCourierOptions(response.body(), weightGrams);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception ex) {
            LOG.debug("XpressBees courier options failed for pincode {}: {}", destinationPincode, ex.getMessage());
            return List.of();
        }
    }

    List<CourierOption> parseForwardCourierOptions(String body, int weightGrams) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return List.of();
            }
            String preferred = preferredForwardCourierName(weightGrams);
            List<CourierOption> options = new ArrayList<>();
            for (JsonNode option : data) {
                String id = text(option, "id");
                String name = firstText(option, "name", "courier_name", "service_name");
                if (StringUtils.isBlank(id) || StringUtils.isBlank(name) || name.toLowerCase(Locale.ROOT).contains("reverse")) {
                    continue;
                }
                int eddDays = option.path("eddDays").asInt(option.path("edd_days").asInt(-1));
                options.add(
                    new CourierOption(
                        id,
                        name,
                        eddDays,
                        eddDays < 0 ? "" : formatEta(eddDays),
                        firstDecimal(option, "rate", "freight", "charge", "price", "shipping_charge"),
                        name.equalsIgnoreCase(preferred)
                    )
                );
            }
            return options;
        } catch (Exception ex) {
            LOG.debug("Could not parse XpressBees courier options: {}", ex.getMessage());
            return List.of();
        }
    }

    private String resolveForwardCourierId(String destinationPincode, int weightGrams, String preferredCourierId)
        throws XpressBeesApiException {
        if (StringUtils.isNotBlank(preferredCourierId)) {
            return preferredCourierId.trim();
        }
        if (destinationPincode == null || !destinationPincode.matches("^[1-9]\\d{5}$")) {
            throw courierResolutionFailure(destinationPincode, "invalid pincode");
        }
        try {
            String token = accessToken();
            if (token == null) {
                throw courierResolutionFailure(destinationPincode, "login failed");
            }
            String url =
                config.getBaseUrl().replaceAll("/$", "") +
                "/api/courier/serviceability/pincode/" +
                destinationPincode;
            HttpResponse<String> response = getJson(url, token);
            if (response.statusCode() != 200) {
                throw courierResolutionFailure(destinationPincode, "serviceability HTTP " + response.statusCode());
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw courierResolutionFailure(destinationPincode, "no serviceable couriers");
            }
            String preferred = preferredForwardCourierName(weightGrams);
            String firstForward = null;
            for (JsonNode option : data) {
                String id = text(option, "id");
                String name = text(option, "name");
                if (id.isBlank() || name.toLowerCase(Locale.ROOT).contains("reverse")) {
                    continue;
                }
                if (firstForward == null) {
                    firstForward = id;
                }
                if (name.equalsIgnoreCase(preferred)) {
                    return id;
                }
            }
            if (firstForward != null) {
                return firstForward;
            }
            throw courierResolutionFailure(destinationPincode, "no forward courier match");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw courierResolutionFailure(destinationPincode, "interrupted");
        } catch (XpressBeesApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw courierResolutionFailure(destinationPincode, ex.getMessage());
        }
    }

    private static XpressBeesApiException courierResolutionFailure(String destinationPincode, String reason) {
        return new XpressBeesApiException(
            "ExpressBees courier serviceability failed for pincode " + destinationPincode + ": " + reason
        );
    }

    public static String preferredForwardCourierName(int weightGrams) {
        if (weightGrams <= 500) {
            return "Surface Xpressbees 0.5 K.G";
        }
        if (weightGrams <= 1000) {
            return "Xpressbees 1 K.G";
        }
        if (weightGrams <= 2000) {
            return "Xpressbees 2 K.G";
        }
        if (weightGrams <= 5000) {
            return "Xpressbees 5 K.G";
        }
        return "Xpressbees 10 K.G";
    }

    private int totalWeightGrams(List<ReversePickupLine> lines) {
        int total = 0;
        for (ReversePickupLine line : lines) {
            total += Math.max(1, line.weightGrams()) * Math.max(1, line.quantity());
        }
        return Math.max(total, Math.max(1, config.getDefaultWeightGrams()));
    }

    private int packageWeightGrams(ForwardShipmentRequest request) {
        if (request.packageWeightGrams() != null && request.packageWeightGrams() > 0) {
            return request.packageWeightGrams();
        }
        return totalWeightGrams(request.lines());
    }

    private int courierSelectionWeightGrams(ForwardShipmentRequest request) {
        int actual = packageWeightGrams(request);
        Integer length = request.packageLengthCm();
        Integer breadth = request.packageBreadthCm();
        Integer height = request.packageHeightCm();
        if (length != null && breadth != null && height != null && length > 0 && breadth > 0 && height > 0) {
            int volumetric = OrderPackageService.volumetricWeightGrams(
                length,
                breadth,
                height,
                Math.max(1, config.getVolumetricDivisor())
            );
            return OrderPackageService.chargeableWeightGrams(actual, volumetric);
        }
        return actual;
    }

    private static int firstPositive(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private String resolveForwardManifestUrl() {
        String path = StringUtils.trimToEmpty(config.resolvedForwardManifestPath());
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

    private HttpResponse<String> getJson(String url, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest
            .newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .GET();
        if (StringUtils.isNotBlank(bearerToken)) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String providerFailureMessage(String operation, HttpResponse<String> response) {
        String body = response.body();
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = firstText(root, "message", "error", "ReturnMessage");
            if (StringUtils.isNotBlank(message)) {
                return "ExpressBees " + operation + " rejected: " + message.replace('\n', ' ').trim();
            }
        } catch (Exception ignored) {
            // Fall through to the raw HTTP snippet.
        }
        return "ExpressBees " + operation + " HTTP " + response.statusCode() + ": " + abbreviate(body, 400);
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

    private static BigDecimal firstDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isMissingNode() || value.isNull() || value.isObject() || value.isArray()) {
                continue;
            }
            if (value.isNumber()) {
                return value.decimalValue();
            }
            String text = value.asText("").trim().replaceAll("[^0-9.\\-]", "");
            if (StringUtils.isNotBlank(text)) {
                try {
                    return new BigDecimal(text);
                } catch (NumberFormatException ignored) {
                    // try the next field
                }
            }
        }
        return null;
    }

    static String indianMobile10(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("91") && digits.length() > 10) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0") && digits.length() == 11) {
            digits = digits.substring(1);
        }
        if (digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits.matches("^[6-9]\\d{9}$") ? digits : null;
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

    public record CourierOption(String id, String name, int eddDays, String eta, BigDecimal rate, boolean preferred) {}

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
        String paymentType,
        BigDecimal orderAmount,
        BigDecimal collectableAmount,
        Integer packageLengthCm,
        Integer packageBreadthCm,
        Integer packageHeightCm,
        Integer packageWeightGrams,
        String courierId
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
            String preAssignedAwb,
            String paymentType,
            BigDecimal orderAmount,
            BigDecimal collectableAmount
        ) {
            this(
                caseReference,
                orderNumber,
                shipFrom,
                deliverTo,
                lines,
                preAssignedAwb,
                paymentType,
                orderAmount,
                collectableAmount,
                null,
                null,
                null,
                null,
                null
            );
        }

        public ForwardShipmentRequest(
            String caseReference,
            String orderNumber,
            ReversePickupAddress shipFrom,
            ReversePickupAddress deliverTo,
            List<ReversePickupLine> lines,
            String preAssignedAwb
        ) {
            this(caseReference, orderNumber, shipFrom, deliverTo, lines, preAssignedAwb, null, null, null);
        }

        public ForwardShipmentRequest(
            String caseReference,
            String orderNumber,
            ReversePickupAddress shipFrom,
            ReversePickupAddress deliverTo,
            List<ReversePickupLine> lines,
            String preAssignedAwb,
            String paymentType
        ) {
            this(caseReference, orderNumber, shipFrom, deliverTo, lines, preAssignedAwb, paymentType, null, null);
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

    public record ForwardTrackingResult(String awb, String status, String message) {}

    public static class XpressBeesApiException extends Exception {

        public XpressBeesApiException(String message) {
            super(message);
        }

        public XpressBeesApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
