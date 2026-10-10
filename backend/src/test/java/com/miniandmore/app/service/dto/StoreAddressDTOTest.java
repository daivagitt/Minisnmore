package com.miniandmore.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.enumeration.StoreAddressUsage;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class StoreAddressDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyUsageJson_survivesEmptyUsagesArray() throws Exception {
        StoreAddressDTO dto = objectMapper.readValue(
            "{\"fullName\":\"HQ\",\"usages\":[],\"usage\":\"SHIPMENT\"}",
            StoreAddressDTO.class
        );

        assertThat(dto.getUsages()).containsExactly(StoreAddressUsage.SHIPMENT);
    }

    @Test
    void serializesLegacyUsageField() throws Exception {
        StoreAddressDTO dto = new StoreAddressDTO();
        dto.setUsages(EnumSet.of(StoreAddressUsage.SHIPMENT, StoreAddressUsage.INVOICE));

        var tree = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertThat(tree.get("usage").asText()).isEqualTo("SHIPMENT");
        assertThat(tree.get("usages")).isNotNull();
    }
}
