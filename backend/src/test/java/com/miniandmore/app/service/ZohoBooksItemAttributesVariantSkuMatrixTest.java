package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ZohoBooksItemAttributesVariantSkuMatrixTest {

    @Test
    void isVariantSku_nullEmptyAndGroupId() {
        assertThat(ZohoBooksItemAttributes.isVariantSku(null)).isFalse();
        assertThat(ZohoBooksItemAttributes.isVariantSku(Map.of())).isFalse();
        assertThat(ZohoBooksItemAttributes.isVariantSku(Map.of("group_id", "g-1"))).isTrue();
        assertThat(ZohoBooksItemAttributes.isVariantSku(Map.of("itemgroup_id", "g-2"))).isTrue();
    }

    @Test
    void isVariantSku_namedSizeAndColorAttributes() {
        assertThat(
            ZohoBooksItemAttributes.isVariantSku(
                Map.of(
                    "attribute_name1",
                    "Size",
                    "attribute_option_name1",
                    "S",
                    "attribute_name2",
                    "Colour",
                    "attribute_option_name2",
                    "Navy"
                )
            )
        ).isTrue();
        assertThat(
            ZohoBooksItemAttributes.isVariantSku(
                Map.of("attribute_name1", "sz", "attribute_option_name1", "M", "attribute_name2", "print", "attribute_option_name2", "Dots")
            )
        ).isTrue();
        assertThat(
            ZohoBooksItemAttributes.isVariantSku(
                Map.of("attribute_name1", "pattern", "attribute_option_name1", "Stripe", "attribute_name2", "unknown", "attribute_option_name2", "X")
            )
        ).isFalse();
    }

    @Test
    void isVariantSku_nameSuffixAndPipeDescription() {
        assertThat(ZohoBooksItemAttributes.isVariantSku(Map.of("name", "Horizon Set — S / Navy"))).isTrue();
        assertThat(ZohoBooksItemAttributes.isVariantSku(Map.of("item_name", "Jabla-0-3M-Aqua"))).isTrue();
        assertThat(ZohoBooksItemAttributes.isVariantSku(Map.of("description", "Base|age:0-3M|print:Aqua"))).isTrue();
        assertThat(ZohoBooksItemAttributes.isVariantSku(Map.of("name", "Simple item", "description", "just text"))).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  ", "Simple" })
    void hasVariantNameSuffix_falseForBlankAndPlain(String name) {
        assertThat(ZohoBooksItemAttributes.hasVariantNameSuffix(name)).isFalse();
    }

    @Test
    void classifyPathsViaStripAndGrouped() {
        assertThat(ZohoBooksItemAttributes.isGrouped(Map.of("group_name", "Parent"))).isTrue();
        assertThat(ZohoBooksItemAttributes.stripVariantSuffix("Horizon Set — S / Navy")).isEqualTo("Horizon Set");
        assertThat(ZohoBooksItemAttributes.stripVariantSuffix("Plain")).isEqualTo("Plain");
        assertThat(ZohoBooksItemAttributes.stripVariantSuffix(null)).isNull();
        assertThat(ZohoBooksItemAttributes.itemName(Map.of("item_name", "FromItemName"))).isEqualTo("FromItemName");
    }
}
