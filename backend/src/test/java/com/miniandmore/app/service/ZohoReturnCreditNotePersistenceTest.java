package com.miniandmore.app.service;

import static org.mockito.Mockito.verify;

import com.miniandmore.app.repository.OrderReturnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoReturnCreditNotePersistenceTest {

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @InjectMocks
    private ZohoReturnCreditNotePersistence persistence;

    @Test
    void persistIfAbsent_updatesOnlyMissingCreditNoteId() {
        persistence.persistIfAbsent("RET-1", "cn-1");

        verify(orderReturnRepository).persistZohoCreditNoteIdIfAbsent("RET-1", "cn-1");
    }
}
