package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.Policy;
import com.miniandmore.app.repository.PolicyRepository;
import com.miniandmore.app.service.dto.PolicyDTO;
import com.miniandmore.app.service.mapper.PolicyMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    private static final Long ID = 1L;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyMapper policyMapper;

    @InjectMocks
    private PolicyService policyService;

    @Test
    void save() {
        PolicyDTO input = new PolicyDTO();
        Policy entity = new Policy().id(ID);
        Policy saved = new Policy().id(ID);
        PolicyDTO expected = new PolicyDTO();
        expected.setId(ID);

        when(policyMapper.toEntity(input)).thenReturn(entity);
        when(policyRepository.save(entity)).thenReturn(saved);
        when(policyMapper.toDto(saved)).thenReturn(expected);

        PolicyDTO result = policyService.save(input);

        assertThat(result).isSameAs(expected);
        verify(policyMapper).toEntity(input);
        verify(policyRepository).save(entity);
        verify(policyMapper).toDto(saved);
    }

    @Test
    void update() {
        PolicyDTO input = new PolicyDTO();
        input.setId(ID);
        Policy entity = new Policy().id(ID);
        Policy saved = new Policy().id(ID);
        PolicyDTO expected = new PolicyDTO();
        expected.setId(ID);

        when(policyMapper.toEntity(input)).thenReturn(entity);
        when(policyRepository.save(entity)).thenReturn(saved);
        when(policyMapper.toDto(saved)).thenReturn(expected);

        PolicyDTO result = policyService.update(input);

        assertThat(result).isSameAs(expected);
        verify(policyMapper).toEntity(input);
        verify(policyRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        PolicyDTO input = new PolicyDTO();
        input.setId(ID);
        Policy existing = new Policy().id(ID);
        PolicyDTO expected = new PolicyDTO();
        expected.setId(ID);

        when(policyRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(policyRepository.save(existing)).thenReturn(existing);
        when(policyMapper.toDto(existing)).thenReturn(expected);

        Optional<PolicyDTO> result = policyService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(policyMapper).partialUpdate(existing, input);
        verify(policyRepository).save(existing);
    }

    @Test
    void partialUpdate_notFound() {
        PolicyDTO input = new PolicyDTO();
        input.setId(ID);
        when(policyRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<PolicyDTO> result = policyService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        Policy entity = new Policy().id(ID);
        PolicyDTO dto = new PolicyDTO();
        dto.setId(ID);
        when(policyRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(policyMapper.toDto(entity)).thenReturn(dto);

        Optional<PolicyDTO> result = policyService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(policyRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<PolicyDTO> result = policyService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        policyService.delete(ID);
        verify(policyRepository).deleteById(ID);
    }

}
