package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.service.dto.OrderItemDTO;
import com.miniandmore.app.service.mapper.OrderItemMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderItemServiceTest {

    private static final Long ID = 1L;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderItemMapper orderItemMapper;

    @InjectMocks
    private OrderItemService orderItemService;

    @Test
    void save() {
        OrderItemDTO input = new OrderItemDTO();
        OrderItem entity = new OrderItem().id(ID);
        OrderItem saved = new OrderItem().id(ID);
        OrderItemDTO expected = new OrderItemDTO();
        expected.setId(ID);

        when(orderItemMapper.toEntity(input)).thenReturn(entity);
        when(orderItemRepository.save(entity)).thenReturn(saved);
        when(orderItemMapper.toDto(saved)).thenReturn(expected);

        OrderItemDTO result = orderItemService.save(input);

        assertThat(result).isSameAs(expected);
        verify(orderItemMapper).toEntity(input);
        verify(orderItemRepository).save(entity);
        verify(orderItemMapper).toDto(saved);
    }

    @Test
    void update() {
        OrderItemDTO input = new OrderItemDTO();
        input.setId(ID);
        OrderItem entity = new OrderItem().id(ID);
        OrderItem saved = new OrderItem().id(ID);
        OrderItemDTO expected = new OrderItemDTO();
        expected.setId(ID);

        when(orderItemMapper.toEntity(input)).thenReturn(entity);
        when(orderItemRepository.save(entity)).thenReturn(saved);
        when(orderItemMapper.toDto(saved)).thenReturn(expected);

        OrderItemDTO result = orderItemService.update(input);

        assertThat(result).isSameAs(expected);
        verify(orderItemMapper).toEntity(input);
        verify(orderItemRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        OrderItemDTO input = new OrderItemDTO();
        input.setId(ID);
        OrderItem existing = new OrderItem().id(ID);
        OrderItemDTO expected = new OrderItemDTO();
        expected.setId(ID);

        when(orderItemRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(orderItemRepository.save(existing)).thenReturn(existing);
        when(orderItemMapper.toDto(existing)).thenReturn(expected);

        Optional<OrderItemDTO> result = orderItemService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(orderItemMapper).partialUpdate(existing, input);
        verify(orderItemRepository).save(existing);
    }

    @Test
    void partialUpdate_notFound() {
        OrderItemDTO input = new OrderItemDTO();
        input.setId(ID);
        when(orderItemRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<OrderItemDTO> result = orderItemService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        OrderItem entity = new OrderItem().id(ID);
        OrderItemDTO dto = new OrderItemDTO();
        dto.setId(ID);
        when(orderItemRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(orderItemMapper.toDto(entity)).thenReturn(dto);

        Optional<OrderItemDTO> result = orderItemService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(orderItemRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<OrderItemDTO> result = orderItemService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        orderItemService.delete(ID);
        verify(orderItemRepository).deleteById(ID);
    }

}
