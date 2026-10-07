package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Commits exchange price-difference Razorpay refunds in their own transaction so a successful
 * gateway refund is not lost when outer request handling rolls back.
 */
@Service
public class ExchangeRazorpaySettlementService {

    private final OrderReturnRepository orderReturnRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final RazorpayRefundService razorpayRefundService;
    private final OrderMailService orderMailService;

    public ExchangeRazorpaySettlementService(
        OrderReturnRepository orderReturnRepository,
        CustomerOrderRepository customerOrderRepository,
        RazorpayRefundService razorpayRefundService,
        OrderMailService orderMailService
    ) {
        this.orderReturnRepository = orderReturnRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.razorpayRefundService = razorpayRefundService;
        this.orderMailService = orderMailService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderReturn recordRazorpayRefund(
        String returnNumber,
        BigDecimal amount,
        String settledBy,
        String adminNotes
    ) {
        OrderReturn orderReturn = orderReturnRepository
            .findOneByReturnNumberForUpdate(returnNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Return not found"));
        if (orderReturn.getRequestType() != OrderRequestType.EXCHANGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price difference settlement applies only to exchanges");
        }
        if (orderReturn.getStatus() != OrderReturnStatus.APPROVED && orderReturn.getStatus() != OrderReturnStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price difference can only be settled for an approved exchange");
        }
        ExchangeSettlementStatus currentSettlement = orderReturn.getSettlementStatus();
        if (ExchangeSettlementAmounts.alreadySettled(currentSettlement)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Price difference already settled");
        }
        BigDecimal difference = ExchangeSettlementAmounts.money(orderReturn.getPriceDifference());
        if (difference.signum() >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay refund applies only when the replacement is cheaper");
        }
        BigDecimal expectedAmount = ExchangeSettlementAmounts.money(difference.abs());
        BigDecimal recordedAmount = ExchangeSettlementAmounts.money(amount);
        if (recordedAmount.signum() <= 0) {
            recordedAmount = expectedAmount;
        }
        if (recordedAmount.compareTo(expectedAmount) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement amount must equal the price difference");
        }
        if (!ExchangeSettlementAmounts.isPrepaidRazorpayEligible(orderReturn.getOrder())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not eligible for Razorpay refund");
        }
        CustomerOrder order = orderReturn.getOrder();
        String priorRefundId = StringUtils.trimToNull(orderReturn.getSettlementReference());
        String refundId;
        if (
            priorRefundId != null &&
            priorRefundId.startsWith("rfnd_") &&
            currentSettlement != ExchangeSettlementStatus.PENDING
        ) {
            refundId = priorRefundId;
        } else {
            BigDecimal razorpayAmount = recordedAmount;
            refundId = razorpayRefundService
                .findExistingRefundForOrder(order, razorpayAmount, RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT)
                .orElseGet(() ->
                    razorpayRefundService.refundPayment(order, razorpayAmount, RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT)
                );
        }
        if (
            order != null &&
            (order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.REFUND_FAILED)
        ) {
            order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
            customerOrderRepository.save(order);
        }
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(recordedAmount);
        orderReturn.setSettlementMethod("razorpay");
        orderReturn.setSettlementReference(refundId);
        orderReturn.setSettledAt(Instant.now());
        orderReturn.setSettledBy(StringUtils.left(StringUtils.defaultIfBlank(settledBy, "system"), 50));
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), adminNotes));
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        queueRefundEmail(order, recordedAmount, refundId);
        return saved;
    }

    private void queueRefundEmail(CustomerOrder order, BigDecimal amount, String refundId) {
        if (order == null) {
            return;
        }
        AfterCommit.run(() -> {
            try {
                orderMailService.sendRefundInitiated(order, amount, refundId, order.getPaymentMethod());
            } catch (RuntimeException ignored) {
                // best-effort notification
            }
        });
    }

    private static String mergeNotes(String existing, String addition) {
        String left = StringUtils.trimToEmpty(existing);
        String right = StringUtils.trimToEmpty(addition);
        if (left.isEmpty()) {
            return StringUtils.left(right, 2000);
        }
        if (right.isEmpty()) {
            return StringUtils.left(left, 2000);
        }
        return StringUtils.left(left + "\n" + right, 2000);
    }
}
