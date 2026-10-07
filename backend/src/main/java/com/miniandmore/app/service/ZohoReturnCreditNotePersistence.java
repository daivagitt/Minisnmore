package com.miniandmore.app.service;

import com.miniandmore.app.repository.OrderReturnRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits a Zoho credit-note id onto the return in its own transaction so a successful Zoho
 * create is not lost if the outer allocate/refund transaction rolls back.
 */
@Service
public class ZohoReturnCreditNotePersistence {

    private final OrderReturnRepository orderReturnRepository;

    public ZohoReturnCreditNotePersistence(OrderReturnRepository orderReturnRepository) {
        this.orderReturnRepository = orderReturnRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistIfAbsent(String returnNumber, String creditNoteId) {
        String number = StringUtils.trimToNull(returnNumber);
        String id = StringUtils.trimToNull(creditNoteId);
        if (number == null || id == null) {
            return;
        }
        orderReturnRepository.persistZohoCreditNoteIdIfAbsent(number, id);
    }
}
