package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ZohoProductFieldMapperStaticCoverageTest {

    @Test
    void isActive_honorsBooleanFlagAndInactiveStatus() {
        Boolean inactiveFlag = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "isActive",
            Map.of("is_active", false)
        );
        Boolean inactiveStatus = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "isActive",
            Map.of("status", "inactive")
        );
        assertThat(inactiveFlag.booleanValue()).isFalse();
        assertThat(inactiveStatus.booleanValue()).isFalse();
    }

    @Test
    void matchesLabel_andApiSuffix_handleNullAndMatches() {
        Boolean labelMatch = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "matchesLabel",
            "Fabric Type",
            List.of("fabric type")
        );
        Boolean suffixMatch = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "matchesApiSuffix",
            "cf__fabric_type",
            List.of("fabric_type")
        );
        Boolean blankLabel = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "matchesLabel",
            "  ",
            List.of("fabric type")
        );
        assertThat(labelMatch.booleanValue()).isTrue();
        assertThat(suffixMatch.booleanValue()).isTrue();
        assertThat(blankLabel.booleanValue()).isFalse();
    }

    @Test
    void normalizedLabel_prefersFormattedName() {
        String label = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "normalizedLabel",
            Map.of("field_name_formatted", "Sizes", "field_name", "sizes_raw")
        );
        assertThat(label).isEqualTo("Sizes");
    }

    @Test
    void findField_skipsInactiveDefinitions() {
        Object found = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "findField",
            List.of(Map.of("is_active", false, "label", "Fabric Type", "field_id", "f1", "is_custom_field", true)),
            List.of("fabric type"),
            List.of("fabric_type"),
            true
        );
        assertThat(found).isNull();
    }

    @Test
    void isActive_treatsInactiveStatusAsDisabled() {
        Boolean inactive = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "isActive",
            Map.of("status", "inactive")
        );
        assertThat(inactive.booleanValue()).isFalse();
    }

    @Test
    void matchesLabel_nullPreferredList_returnsFalse() {
        Boolean match = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "matchesLabel",
            "Fabric",
            null
        );
        assertThat(match.booleanValue()).isFalse();
    }

    @Test
    void matchesApiSuffix_nullSuffixList_returnsFalse() {
        Boolean match = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "matchesApiSuffix",
            "cf_fabric",
            null
        );
        assertThat(match.booleanValue()).isFalse();
    }

    @Test
    void findField_skipsNonCustomWhenCustomOnly() {
        Object found = ReflectionTestUtils.invokeMethod(
            ZohoProductFieldMapper.class,
            "findField",
            List.of(Map.of("is_active", true, "label", "Fabric Type", "field_id", "f1", "is_custom_field", false)),
            List.of("fabric type"),
            List.of("fabric_type"),
            true
        );
        assertThat(found).isNull();
    }
}
