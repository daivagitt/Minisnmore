package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZohoBooksItemAttributesBranchCoverageTest {

    @Test
    void isActive_parsesStringBooleanFlags() {
        assertThat(ZohoBooksItemAttributes.isActive(Map.of("is_active", "false"))).isFalse();
        assertThat(ZohoBooksItemAttributes.isActive(Map.of("is_active", "TRUE"))).isTrue();
        assertThat(ZohoBooksItemAttributes.isActive(Map.of("is_active", "maybe"))).isTrue();
    }

    @Test
    void isActive_emptyOrNullItem_defaultsToActive() {
        assertThat(ZohoBooksItemAttributes.isActive(Map.of())).isTrue();
        assertThat(ZohoBooksItemAttributes.isActive(null)).isTrue();
    }

    @Test
    void groupId_prefersGroupIdOverItemGroupId() {
        Map<String, Object> item = Map.of("group_id", "g1", "itemgroup_id", "g2");
        assertThat(ZohoBooksItemAttributes.groupId(item)).isEqualTo("g1");
        assertThat(ZohoBooksItemAttributes.groupName(Map.of("group_name", "A", "itemgroup_name", "B"))).isEqualTo("A");
    }

    @Test
    void parseZohoTimestamp_blankOrUnparseable_returnsEmpty() {
        assertThat(ZohoBooksItemAttributes.parseZohoTimestamp("  ")).isEmpty();
        assertThat(ZohoBooksItemAttributes.parseZohoTimestamp("not-a-date")).isEmpty();
    }

    @Test
    void isChangedOnOrAfter_nullCursorOrItem_treatsAsChanged() {
        Instant cursor = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(ZohoBooksItemAttributes.isChangedOnOrAfter(null, cursor)).isTrue();
        assertThat(ZohoBooksItemAttributes.isChangedOnOrAfter(Map.of(), cursor)).isTrue();
        assertThat(ZohoBooksItemAttributes.isChangedOnOrAfter(Map.of("sku", "X"), null)).isTrue();
    }

    @Test
    void isVariantSku_trueWhenPipeDescriptionHasSizeAndColor() {
        Map<String, Object> item = Map.of(
            "name",
            "Wrap",
            "description",
            "Wrap | Age: 0-3 M | Print: Aqua"
        );
        assertThat(ZohoBooksItemAttributes.isVariantSku(item)).isTrue();
        assertThat(ZohoBooksItemAttributes.size(item)).isEqualTo("0-3 M");
        assertThat(ZohoBooksItemAttributes.color(item)).isEqualTo("Aqua");
    }

    @Test
    void lastModifiedAt_emptyItem_returnsEmpty() {
        assertThat(ZohoBooksItemAttributes.lastModifiedAt(Map.of())).isEmpty();
        assertThat(ZohoBooksItemAttributes.lastModifiedAt(null)).isEmpty();
    }
}
