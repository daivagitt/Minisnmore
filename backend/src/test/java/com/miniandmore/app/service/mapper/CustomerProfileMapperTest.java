package com.miniandmore.app.service.mapper;

import static com.miniandmore.app.domain.CustomerProfileAsserts.*;
import static com.miniandmore.app.domain.CustomerProfileTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerProfileMapperTest {

    private CustomerProfileMapper customerProfileMapper;

    @BeforeEach
    void setUp() {
        customerProfileMapper = new CustomerProfileMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getCustomerProfileSample1();
        var actual = customerProfileMapper.toEntity(customerProfileMapper.toDto(expected));
        assertCustomerProfileAllPropertiesEquals(expected, actual);
    }
}
