package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.OrderReturnItem;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.repository.OrderReturnItemRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds ExpressBees reverse-pickup and replacement-shipment payloads from a return case.
 * Kept out of {@link OrderReturnService} so booking/workflow stays separate from carrier mapping.
 */
final class ReturnXpressBeesShipmentFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ReturnXpressBeesShipmentFactory.class);

    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper;
    private final OrderReturnItemRepository orderReturnItemRepository;

    ReturnXpressBeesShipmentFactory(
        ApplicationProperties applicationProperties,
        ObjectMapper objectMapper,
        OrderReturnItemRepository orderReturnItemRepository
    ) {
        this.applicationProperties = applicationProperties;
        this.objectMapper = objectMapper;
        this.orderReturnItemRepository = orderReturnItemRepository;
    }

    XpressBeesClient.ReversePickupRequest buildReversePickupRequest(OrderReturn orderReturn, String preAssignedAwb) {
        CustomerOrder order = orderReturn.getOrder();
        JsonNode shipping = parseShippingAddress(order.getShippingAddressJson());
        ApplicationProperties.XpressBees xpressBees = applicationProperties.getXpressbees();
        XpressBeesClient.ReversePickupAddress pickupFrom = customerAddress(shipping);
        XpressBeesClient.ReversePickupAddress deliverTo = new XpressBeesClient.ReversePickupAddress(
            StringUtils.defaultIfBlank(xpressBees.getWarehouseName(), "Mini's n More"),
            StringUtils.defaultIfBlank(xpressBees.getWarehousePhone(), pickupFrom.phone()),
            null,
            xpressBees.getWarehouseLine1(),
            xpressBees.getWarehouseLine2(),
            xpressBees.getWarehouseCity(),
            xpressBees.getWarehouseState(),
            xpressBees.getOriginPincode()
        );
        return new XpressBeesClient.ReversePickupRequest(
            orderReturn.getReturnNumber(),
            order.getOrderNumber(),
            pickupFrom,
            deliverTo,
            buildReversePickupLines(orderReturn, order),
            StringUtils.trimToNull(preAssignedAwb)
        );
    }

    XpressBeesClient.ForwardShipmentRequest buildForwardShipmentRequest(
        OrderReturn orderReturn,
        String preAssignedAwb,
        BigDecimal orderAmount
    ) {
        CustomerOrder order = orderReturn.getOrder();
        JsonNode shipping = parseShippingAddress(order.getShippingAddressJson());
        ApplicationProperties.XpressBees xpressBees = applicationProperties.getXpressbees();
        XpressBeesClient.ReversePickupAddress shipFrom = new XpressBeesClient.ReversePickupAddress(
            StringUtils.defaultIfBlank(xpressBees.getWarehouseName(), "Mini's n More"),
            StringUtils.defaultIfBlank(xpressBees.getWarehousePhone(), firstAddressField(shipping, "phone", "mobile")),
            null,
            xpressBees.getWarehouseLine1(),
            xpressBees.getWarehouseLine2(),
            xpressBees.getWarehouseCity(),
            xpressBees.getWarehouseState(),
            xpressBees.getOriginPincode()
        );
        Integer packageWeight = orderReturn.getPackageWeightGrams() != null && orderReturn.getPackageWeightGrams() > 0
            ? orderReturn.getPackageWeightGrams()
            : orderReturn.getPackageChargeableWeightGrams();
        return new XpressBeesClient.ForwardShipmentRequest(
            orderReturn.getReturnNumber() + "-OUT",
            order.getOrderNumber(),
            shipFrom,
            customerAddress(shipping),
            buildReplacementShipmentLines(orderReturn),
            StringUtils.trimToNull(preAssignedAwb),
            "prepaid",
            orderAmount,
            BigDecimal.ZERO,
            orderReturn.getPackageLengthCm(),
            orderReturn.getPackageBreadthCm(),
            orderReturn.getPackageHeightCm(),
            packageWeight,
            StringUtils.trimToNull(orderReturn.getPackageCourierId())
        );
    }

    Map<String, Object> shippingAddressPayload(OrderReturn orderReturn) {
        JsonNode address = parseShippingAddress(
            orderReturn == null || orderReturn.getOrder() == null ? null : orderReturn.getOrder().getShippingAddressJson()
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", firstAddressField(address, "full_name", "name"));
        data.put("full_name", firstAddressField(address, "full_name", "name"));
        data.put("line1", firstAddressField(address, "line1", "address_line1", "address"));
        data.put("line2", firstAddressField(address, "line2", "address_line2"));
        data.put("city", firstAddressField(address, "city"));
        data.put("state", firstAddressField(address, "state"));
        data.put("pincode", firstAddressField(address, "pincode", "postal_code", "zip"));
        data.put("phone", firstAddressField(address, "phone", "mobile"));
        data.put("email", firstAddressField(address, "email"));
        data.put("country", StringUtils.defaultIfBlank(firstAddressField(address, "country"), "India"));
        return data;
    }

    Map<String, Object> dispatchAddressPayload() {
        ApplicationProperties.XpressBees warehouse = applicationProperties.getXpressbees();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", StringUtils.defaultString(warehouse.getWarehouseName()));
        data.put("full_name", StringUtils.defaultString(warehouse.getWarehouseName()));
        data.put("line1", StringUtils.defaultString(warehouse.getWarehouseLine1()));
        data.put("line2", StringUtils.defaultString(warehouse.getWarehouseLine2()));
        data.put("city", StringUtils.defaultString(warehouse.getWarehouseCity()));
        data.put("state", StringUtils.defaultString(warehouse.getWarehouseState()));
        data.put("pincode", StringUtils.defaultString(warehouse.getOriginPincode()));
        data.put("phone", StringUtils.defaultString(warehouse.getWarehousePhone()));
        data.put("country", "India");
        return data;
    }

    String destinationPincode(OrderReturn orderReturn) {
        CustomerOrder order = orderReturn == null ? null : orderReturn.getOrder();
        JsonNode shipping = parseShippingAddress(order == null ? null : order.getShippingAddressJson());
        return firstAddressField(shipping, "pincode", "postal_code", "zip");
    }

    private List<XpressBeesClient.ReversePickupLine> buildReversePickupLines(OrderReturn orderReturn, CustomerOrder order) {
        List<XpressBeesClient.ReversePickupLine> lines = new ArrayList<>();
        int defaultWeight = Math.max(1, applicationProperties.getXpressbees().getDefaultWeightGrams());
        if (orderReturn.getRequestType() == OrderRequestType.RETURN && orderReturn.getId() != null) {
            for (OrderReturnItem returnItem : orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId())) {
                OrderItem selected = returnItem.getOrderItem();
                lines.add(
                    new XpressBeesClient.ReversePickupLine(
                        StringUtils.defaultIfBlank(selected.getProductName(), "Return item"),
                        StringUtils.defaultIfBlank(selected.getVariantSku(), selected.getProductSlug()),
                        returnItem.getRequestedQty(),
                        returnItem.getUnitAmount(),
                        defaultWeight
                    )
                );
            }
            if (!lines.isEmpty()) {
                return lines;
            }
        }
        OrderItem item = orderReturn.getOrderItem();
        if (item != null) {
            lines.add(
                new XpressBeesClient.ReversePickupLine(
                    StringUtils.defaultIfBlank(item.getProductName(), "Return item"),
                    StringUtils.defaultIfBlank(item.getVariantSku(), item.getProductSlug()),
                    Math.max(1, item.getQty() == null ? 1 : item.getQty()),
                    item.getPrice(),
                    defaultWeight
                )
            );
            return lines;
        }
        for (OrderItem orderItem : order.getItemses()) {
            lines.add(
                new XpressBeesClient.ReversePickupLine(
                    StringUtils.defaultIfBlank(orderItem.getProductName(), "Return item"),
                    StringUtils.defaultIfBlank(orderItem.getVariantSku(), orderItem.getProductSlug()),
                    Math.max(1, orderItem.getQty() == null ? 1 : orderItem.getQty()),
                    orderItem.getPrice(),
                    defaultWeight
                )
            );
        }
        if (lines.isEmpty()) {
            lines.add(
                new XpressBeesClient.ReversePickupLine(
                    "Return item",
                    orderReturn.getReturnNumber(),
                    1,
                    order.getTotal(),
                    defaultWeight
                )
            );
        }
        return lines;
    }

    private List<XpressBeesClient.ReversePickupLine> buildReplacementShipmentLines(OrderReturn orderReturn) {
        OrderItem item = orderReturn.getOrderItem();
        String productName = item != null ? StringUtils.defaultIfBlank(item.getProductName(), "Replacement item") : "Replacement item";
        String sku = StringUtils.defaultIfBlank(
            orderReturn.getReplacementVariantSku(),
            item != null ? StringUtils.defaultIfBlank(item.getVariantSku(), item.getProductSlug()) : orderReturn.getReturnNumber()
        );
        int qty = 1;
        if (item != null && item.getQty() != null) {
            qty = Math.max(1, item.getQty());
        }
        BigDecimal price = item != null ? item.getPrice() : orderReturn.getOrder().getTotal();
        int weight = orderReturn.getPackageWeightGrams() != null && orderReturn.getPackageWeightGrams() > 0
            ? orderReturn.getPackageWeightGrams()
            : Math.max(1, applicationProperties.getXpressbees().getDefaultWeightGrams());
        return List.of(new XpressBeesClient.ReversePickupLine(productName, sku, qty, price, weight));
    }

    private XpressBeesClient.ReversePickupAddress customerAddress(JsonNode shipping) {
        return new XpressBeesClient.ReversePickupAddress(
            firstAddressField(shipping, "full_name", "name"),
            firstAddressField(shipping, "phone", "mobile"),
            firstAddressField(shipping, "email"),
            firstAddressField(shipping, "line1", "address_line1", "address"),
            firstAddressField(shipping, "line2", "address_line2"),
            firstAddressField(shipping, "city"),
            firstAddressField(shipping, "state"),
            firstAddressField(shipping, "pincode", "postal_code", "zip")
        );
    }

    private JsonNode parseShippingAddress(String shippingAddressJson) {
        if (StringUtils.isBlank(shippingAddressJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(shippingAddressJson);
        } catch (Exception ex) {
            LOG.debug("Could not parse shipping address JSON for reverse pickup: {}", ex.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private static String firstAddressField(JsonNode address, String... fields) {
        if (address == null || address.isMissingNode()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = address.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText("").trim();
                if (StringUtils.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }
}
