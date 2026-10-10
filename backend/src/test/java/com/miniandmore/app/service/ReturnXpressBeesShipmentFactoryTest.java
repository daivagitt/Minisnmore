package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.OrderReturnItem;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.repository.OrderReturnItemRepository;
import com.miniandmore.app.repository.StoreAddressRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReturnXpressBeesShipmentFactoryTest {

    @Mock
    private OrderReturnItemRepository orderReturnItemRepository;

    @Mock
    private StoreAddressRepository storeAddressRepository;

    private ReturnXpressBeesShipmentFactory factory;
    private ApplicationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ApplicationProperties();
        ApplicationProperties.XpressBees xb = properties.getXpressbees();
        xb.setWarehouseName("Mini Warehouse");
        xb.setWarehouseLine1("Line 1");
        xb.setWarehouseCity("Bengaluru");
        xb.setWarehouseState("Karnataka");
        xb.setOriginPincode("560074");
        xb.setWarehousePhone("9876500000");
        xb.setDefaultWeightGrams(500);
        factory = new ReturnXpressBeesShipmentFactory(
            properties,
            new ObjectMapper(),
            orderReturnItemRepository,
            storeAddressRepository
        );
    }

    private static OrderReturn orderReturnWith(CustomerOrder order, String returnNumber) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber(returnNumber);
        orderReturn.setOrder(order);
        return orderReturn;
    }

    @Test
    void shippingAddressPayload_mapsCustomerFieldsFromOrderJson() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-1")
            .shippingAddressJson(
                """
                {"full_name":"Jane Doe","phone":"9876543210","email":"jane@example.com","line1":"12 MG Road","city":"Mumbai","state":"Maharashtra","pincode":"400001"}
                """
            );
        OrderReturn orderReturn = orderReturnWith(order, "RET-00001");

        assertThat(factory.shippingAddressPayload(orderReturn))
            .containsEntry("name", "Jane Doe")
            .containsEntry("pincode", "400001")
            .containsEntry("country", "India");
    }

    @Test
    void dispatchAddressPayload_usesWarehouseConfiguration() {
        assertThat(factory.dispatchAddressPayload())
            .containsEntry("name", "Mini Warehouse")
            .containsEntry("pincode", "560074")
            .containsEntry("country", "India");
    }

    @Test
    void destinationPincode_readsFromShippingJson() {
        CustomerOrder order = new CustomerOrder().shippingAddressJson("{\"postal_code\":\"560001\"}");
        OrderReturn orderReturn = orderReturnWith(order, "RET-00002");

        assertThat(factory.destinationPincode(orderReturn)).isEqualTo("560001");
    }

    @Test
    void buildReversePickupRequest_usesReturnLineItemsWhenPresent() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-2")
            .shippingAddressJson("{\"full_name\":\"Buyer\",\"phone\":\"9876543210\",\"line1\":\"A\",\"city\":\"City\",\"state\":\"KA\",\"pincode\":\"560001\"}");
        OrderItem orderItem = new OrderItem().productName("Dress").variantSku("SKU-D").qty(1).price(BigDecimal.valueOf(499));
        OrderReturn orderReturn = orderReturnWith(order, "RET-00010");
        orderReturn.setId(10L);
        orderReturn.setRequestType(OrderRequestType.RETURN);
        OrderReturnItem returnItem = new OrderReturnItem();
        returnItem.setOrderItem(orderItem);
        returnItem.setRequestedQty(1);
        returnItem.setUnitAmount(BigDecimal.valueOf(499));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(10L)).thenReturn(List.of(returnItem));

        XpressBeesClient.ReversePickupRequest request = factory.buildReversePickupRequest(orderReturn, "AWB-PRE");

        assertThat(request.preAssignedAwb()).isEqualTo("AWB-PRE");
        assertThat(request.lines()).hasSize(1);
        assertThat(request.lines().get(0).sku()).isEqualTo("SKU-D");
        assertThat(request.pickupFrom().pincode()).isEqualTo("560001");
        assertThat(request.deliverTo().pincode()).isEqualTo("560074");
    }

    @Test
    void buildForwardShipmentRequest_prepaidReplacementUsesPackageWeight() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-3")
            .total(BigDecimal.valueOf(999))
            .shippingAddressJson("{\"full_name\":\"Buyer\",\"phone\":\"9876543210\",\"line1\":\"A\",\"city\":\"City\",\"state\":\"KA\",\"pincode\":\"560002\"}");
        OrderItem item = new OrderItem().productName("Romper").variantSku("SKU-R").qty(1).price(BigDecimal.valueOf(999));
        OrderReturn orderReturn = orderReturnWith(order, "RET-00011");
        orderReturn.setReplacementVariantSku("SKU-R-NEW");
        orderReturn.setPackageWeightGrams(750);
        orderReturn.setPackageCourierId("courier-1");
        orderReturn.setOrderItem(item);

        XpressBeesClient.ForwardShipmentRequest request = factory.buildForwardShipmentRequest(
            orderReturn,
            null,
            BigDecimal.valueOf(999)
        );

        assertThat(request.paymentType()).isEqualTo("prepaid");
        assertThat(request.courierId()).isEqualTo("courier-1");
        assertThat(request.lines().get(0).sku()).isEqualTo("SKU-R-NEW");
        assertThat(request.packageWeightGrams()).isEqualTo(750);
    }

    @Test
    void buildForwardShipmentRequest_usesChargeableWeightWhenPackageWeightUnset() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-4")
            .total(BigDecimal.TEN)
            .shippingAddressJson("{\"full_name\":\"Buyer\",\"phone\":\"9876543210\",\"line1\":\"A\",\"city\":\"City\",\"state\":\"KA\",\"pincode\":\"560003\"}");
        OrderItem item = new OrderItem().productName("Tee").variantSku("SKU-T").qty(1).price(BigDecimal.TEN);
        OrderReturn orderReturn = orderReturnWith(order, "RET-00012");
        orderReturn.setOrderItem(item);
        orderReturn.setPackageChargeableWeightGrams(620);
        orderReturn.setPackageLengthCm(10);
        orderReturn.setPackageBreadthCm(8);
        orderReturn.setPackageHeightCm(6);

        XpressBeesClient.ForwardShipmentRequest request = factory.buildForwardShipmentRequest(
            orderReturn,
            "AWB-FWD",
            BigDecimal.TEN
        );

        assertThat(request.packageWeightGrams()).isEqualTo(620);
    }

    @Test
    void destinationPincode_nullReturn_isNull() {
        assertThat(factory.destinationPincode(null)).isNull();
    }

    @Test
    void buildReversePickupRequest_fallsBackToAllOrderLinesWhenNoReturnItems() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-5")
            .shippingAddressJson("{\"full_name\":\"Buyer\",\"phone\":\"9876543210\",\"line1\":\"A\",\"city\":\"City\",\"state\":\"KA\",\"pincode\":\"560004\"}");
        OrderItem line = new OrderItem().productName("Hat").variantSku("HAT-1").qty(2).price(BigDecimal.valueOf(50));
        order.addItems(line);
        OrderReturn orderReturn = orderReturnWith(order, "RET-00013");
        orderReturn.setId(13L);
        orderReturn.setRequestType(OrderRequestType.RETURN);
        orderReturn.setOrderItem(null);
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(13L)).thenReturn(List.of());

        XpressBeesClient.ReversePickupRequest request = factory.buildReversePickupRequest(orderReturn, null);

        assertThat(request.lines()).hasSize(1);
        assertThat(request.lines().get(0).sku()).isEqualTo("HAT-1");
        assertThat(request.lines().get(0).quantity()).isEqualTo(2);
    }
}
