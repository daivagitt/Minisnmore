package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.service.dto.CustomerProfileDTO;
import com.miniandmore.app.service.mapper.CustomerProfileMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceTest {

    private static final Long ID = 1L;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private CustomerProfileMapper customerProfileMapper;

    @InjectMocks
    private CustomerProfileService customerProfileService;

    @Test
    void save() {
        CustomerProfileDTO input = new CustomerProfileDTO();
        CustomerProfile entity = new CustomerProfile().id(ID);
        CustomerProfile saved = new CustomerProfile().id(ID);
        CustomerProfileDTO expected = new CustomerProfileDTO();
        expected.setId(ID);

        when(customerProfileMapper.toEntity(input)).thenReturn(entity);
        when(customerProfileRepository.save(entity)).thenReturn(saved);
        when(customerProfileMapper.toDto(saved)).thenReturn(expected);

        CustomerProfileDTO result = customerProfileService.save(input);

        assertThat(result).isSameAs(expected);
        verify(customerProfileMapper).toEntity(input);
        verify(customerProfileRepository).save(entity);
        verify(customerProfileMapper).toDto(saved);
    }

    @Test
    void update() {
        CustomerProfileDTO input = new CustomerProfileDTO();
        input.setId(ID);
        CustomerProfile entity = new CustomerProfile().id(ID);
        CustomerProfile saved = new CustomerProfile().id(ID);
        CustomerProfileDTO expected = new CustomerProfileDTO();
        expected.setId(ID);

        when(customerProfileMapper.toEntity(input)).thenReturn(entity);
        when(customerProfileRepository.save(entity)).thenReturn(saved);
        when(customerProfileMapper.toDto(saved)).thenReturn(expected);

        CustomerProfileDTO result = customerProfileService.update(input);

        assertThat(result).isSameAs(expected);
        verify(customerProfileMapper).toEntity(input);
        verify(customerProfileRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        CustomerProfileDTO input = new CustomerProfileDTO();
        input.setId(ID);
        CustomerProfile existing = new CustomerProfile().id(ID);
        CustomerProfileDTO expected = new CustomerProfileDTO();
        expected.setId(ID);

        when(customerProfileRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(customerProfileRepository.save(existing)).thenReturn(existing);
        when(customerProfileMapper.toDto(existing)).thenReturn(expected);

        Optional<CustomerProfileDTO> result = customerProfileService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(customerProfileMapper).partialUpdate(existing, input);
        verify(customerProfileRepository).save(existing);
    }

    @Test
    void partialUpdate_notFound() {
        CustomerProfileDTO input = new CustomerProfileDTO();
        input.setId(ID);
        when(customerProfileRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<CustomerProfileDTO> result = customerProfileService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        CustomerProfile entity = new CustomerProfile().id(ID);
        CustomerProfileDTO dto = new CustomerProfileDTO();
        dto.setId(ID);
        when(customerProfileRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(customerProfileMapper.toDto(entity)).thenReturn(dto);

        Optional<CustomerProfileDTO> result = customerProfileService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(customerProfileRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<CustomerProfileDTO> result = customerProfileService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        customerProfileService.delete(ID);
        verify(customerProfileRepository).deleteById(ID);
    }

}
