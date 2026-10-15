package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.enumeration.CancellationRequestStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderCancellationRequestRepository;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceShipmentVoidFlowTest {

    @Mock
    private OrderCancellationRequestRepository cancellationRequestRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private AccountOrderDataAccess dataAccess;

    @Mock
    private RazorpayRefundService razorpayRefundService;

    @Mock
    private ZohoSyncService zohoSyncService;

    @Mock
    private OrderEventService orderEventService;

    @Mock
    private ProductVariantSupport variantSupport;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    @Mock
    private OrderShipmentService orderShipmentService;

    @Mock
    private OrderMailService orderMailService;

    private OrderCancellationExecutor cancellationExecutor;
    private OrderCancellationService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        cancellationExecutor = new OrderCancellationExecutor(
            dataAccess,
            org.mockito.Mockito.mock(com.miniandmore.app.repository.OrderReturnRepository.class),
            razorpayRefundService,
            zohoSyncService,
            orderEventService,
            variantSupport,
            transactionManager
        );
        service = new OrderCancellationService(
            cancellationRequestRepository,
            customerOrderRepository,
            cancellationExecutor,
            orderMailService,
            null,
            orderShipmentService
        );
        when(dataAccess.customerOrderRepository()).thenReturn(customerOrderRepository);
    }

    @Test
    void approve_withAwaitingPickupAwb_voidsBeforeCancellingWithRealExecutor() {
        CustomerOrder booked = order("ORD-001");
        booked.setStatus(OrderStatus.HAND_WRAPPED);
        booked.setTrackingNumber("XB-1");
        booked.setShipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
        booked.setShipmentCarrierStatus("booked");
        CustomerOrder afterVoid = order("ORD-001");
        afterVoid.setStatus(OrderStatus.HAND_WRAPPED);

        OrderCancellationRequest request = new OrderCancellationRequest();
        request.setRequestNumber("CAN-ORD001-111111");
        request.setStatus(CancellationRequestStatus.REQUESTED);
        request.setReason("Changed my mind");
        request.setOrder(booked);

        when(cancellationRequestRepository.findOneByRequestNumber("CAN-ORD001-111111")).thenReturn(Optional.of(request));
        when(customerOrderRepository.findOneByOrderNumberForUpdate("ORD-001")).thenReturn(Optional.of(afterVoid));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderCancellationRequest approved = service.approve("CAN-ORD001-111111", "Approved");

        assertThat(approved.getStatus()).isEqualTo(CancellationRequestStatus.APPROVED);
        InOrder inOrder = inOrder(orderShipmentService, customerOrderRepository);
        inOrder.verify(orderShipmentService).cancelShipment(
            eq("ORD-001"),
            eq(ReturnLogisticsMode.XPRESSBEES),
            eq("Changed my mind"),
            eq("cancellation-approval")
        );
        inOrder.verify(customerOrderRepository).save(any(CustomerOrder.class));
    }

    private static CustomerOrder order(String orderNumber) {
        CustomerOrder order = new CustomerOrder();
        order.setId(101L);
        order.setOrderNumber(orderNumber);
        order.setUserLogin("testuser");
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod("online");
        order.setTotal(new BigDecimal("1500.00"));
        return order;
    }
}
