package com.miniandmore.app.service.mapper;

import static com.miniandmore.app.domain.PolicyAsserts.*;
import static com.miniandmore.app.domain.PolicyTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyMapperTest {

    private PolicyMapper policyMapper;

    @BeforeEach
    void setUp() {
        policyMapper = new PolicyMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getPolicySample1();
        var actual = policyMapper.toEntity(policyMapper.toDto(expected));
        assertPolicyAllPropertiesEquals(expected, actual);
    }
}
