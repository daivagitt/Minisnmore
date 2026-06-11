package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.service.dto.CustomerOrderDTO;
import com.miniandmore.app.service.mapper.CustomerOrderMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceTest {

    private static final Long ID = 1L;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private CustomerOrderMapper customerOrderMapper;

    @InjectMocks
    private CustomerOrderService customerOrderService;

    @Test
    void save() {
        CustomerOrderDTO input = new CustomerOrderDTO();
        CustomerOrder entity = new CustomerOrder().id(ID);
        CustomerOrder saved = new CustomerOrder().id(ID);
        CustomerOrderDTO expected = new CustomerOrderDTO();
        expected.setId(ID);

        when(customerOrderMapper.toEntity(input)).thenReturn(entity);
        when(customerOrderRepository.save(entity)).thenReturn(saved);
        when(customerOrderMapper.toDto(saved)).thenReturn(expected);

        CustomerOrderDTO result = customerOrderService.save(input);

        assertThat(result).isSameAs(expected);
        verify(customerOrderMapper).toEntity(input);
        verify(customerOrderRepository).save(entity);
        verify(customerOrderMapper).toDto(saved);
    }

    @Test
    void update() {
        CustomerOrderDTO input = new CustomerOrderDTO();
        input.setId(ID);
        CustomerOrder entity = new CustomerOrder().id(ID);
        CustomerOrder saved = new CustomerOrder().id(ID);
        CustomerOrderDTO expected = new CustomerOrderDTO();
        expected.setId(ID);

        when(customerOrderMapper.toEntity(input)).thenReturn(entity);
        when(customerOrderRepository.save(entity)).thenReturn(saved);
        when(customerOrderMapper.toDto(saved)).thenReturn(expected);

        CustomerOrderDTO result = customerOrderService.update(input);

        assertThat(result).isSameAs(expected);
        verify(customerOrderMapper).toEntity(input);
        verify(customerOrderRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        CustomerOrderDTO input = new CustomerOrderDTO();
        input.setId(ID);
        CustomerOrder existing = new CustomerOrder().id(ID);
        CustomerOrderDTO expected = new CustomerOrderDTO();
        expected.setId(ID);

        when(customerOrderRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(customerOrderRepository.save(existing)).thenReturn(existing);
        when(customerOrderMapper.toDto(existing)).thenReturn(expected);

        Optional<CustomerOrderDTO> result = customerOrderService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(customerOrderMapper).partialUpdate(existing, input);
        verify(customerOrderRepository).save(existing);
    }

    @Test
    void partialUpdate_notFound() {
        CustomerOrderDTO input = new CustomerOrderDTO();
        input.setId(ID);
        when(customerOrderRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<CustomerOrderDTO> result = customerOrderService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        CustomerOrder entity = new CustomerOrder().id(ID);
        CustomerOrderDTO dto = new CustomerOrderDTO();
        dto.setId(ID);
        when(customerOrderRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(customerOrderMapper.toDto(entity)).thenReturn(dto);

        Optional<CustomerOrderDTO> result = customerOrderService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(customerOrderRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<CustomerOrderDTO> result = customerOrderService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        customerOrderService.delete(ID);
        verify(customerOrderRepository).deleteById(ID);
    }

}
