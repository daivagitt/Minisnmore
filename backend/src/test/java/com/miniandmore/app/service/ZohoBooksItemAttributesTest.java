package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ZohoBooksItemAttributesTest {

    @Test
    void readsGroupAndNamedSizeColorAttributes() {
        Map<String, Object> item = Map.of(
            "group_id",
            "grp-1",
            "group_name",
            "Horizon Set",
            "name",
            "Horizon Set — S / Navy",
            "attribute_name1",
            "Size",
            "attribute_option_name1",
            "S",
            "attribute_name2",
            "Colour",
            "attribute_option_name2",
            "Navy"
        );

        assertThat(ZohoBooksItemAttributes.isGrouped(item)).isTrue();
        assertThat(ZohoBooksItemAttributes.groupId(item)).isEqualTo("grp-1");
        assertThat(ZohoBooksItemAttributes.size(item)).isEqualTo("S");
        assertThat(ZohoBooksItemAttributes.color(item)).isEqualTo("Navy");
        assertThat(ZohoBooksItemAttributes.parentDisplayName(item)).isEqualTo("Horizon Set");
    }

    @Test
    void fallsBackToPushedNameSuffixWhenAttributesMissing() {
        Map<String, Object> item = Map.of("name", "Horizon Set — M / Ivory");

        assertThat(ZohoBooksItemAttributes.isGrouped(item)).isFalse();
        assertThat(ZohoBooksItemAttributes.size(item)).isEqualTo("M");
        assertThat(ZohoBooksItemAttributes.color(item)).isEqualTo("Ivory");
        assertThat(ZohoBooksItemAttributes.parentDisplayName(item)).isEqualTo("Horizon Set");
    }

    @Test
    void unlabeledAttributesAndNameSuffixWithoutSlash() {
        Map<String, Object> unlabeled = Map.of(
            "attribute_option_name1",
            "0-3 M",
            "attribute_option_name2",
            "Ivory",
            "name",
            "Wrap"
        );
        assertThat(ZohoBooksItemAttributes.size(unlabeled)).isEqualTo("0-3 M");
        assertThat(ZohoBooksItemAttributes.color(unlabeled)).isEqualTo("Ivory");

        Map<String, Object> sizeOnly = Map.of("name", "Wrap — 3-6 M");
        assertThat(ZohoBooksItemAttributes.size(sizeOnly)).isEqualTo("3-6 M");
        assertThat(ZohoBooksItemAttributes.color(sizeOnly)).isNull();
        assertThat(ZohoBooksItemAttributes.parentDisplayName(sizeOnly)).isEqualTo("Wrap");
        assertThat(ZohoBooksItemAttributes.isVariantSku(unlabeled)).isFalse();
        assertThat(ZohoBooksItemAttributes.isVariantSku(sizeOnly)).isFalse();
    }

    @Test
    void hyphenNameSuffix_isVariantSku_hyphenatedProductNameIsNot() {
        Map<String, Object> hyphen = Map.of("name", "Horizon Set-S-Navy", "sku", "HORIZON-S-NAVY");
        assertThat(ZohoBooksItemAttributes.isVariantSku(hyphen)).isTrue();
        assertThat(ZohoBooksItemAttributes.size(hyphen)).isEqualTo("S");
        assertThat(ZohoBooksItemAttributes.color(hyphen)).isEqualTo("Navy");
        assertThat(ZohoBooksItemAttributes.parentDisplayName(hyphen)).isEqualTo("Horizon Set");

        Map<String, Object> productName = Map.of("name", "Side-Button Set", "sku", "SIDE-BUTTON");
        assertThat(ZohoBooksItemAttributes.isVariantSku(productName)).isFalse();
        assertThat(ZohoBooksItemAttributes.parentDisplayName(productName)).isEqualTo("Side-Button Set");
    }

    @Test
    void simpleItemHasNoVariantOptions() {
        Map<String, Object> item = Map.of("name", "Muslin Swaddle", "sku", "SWADDLE-1");

        assertThat(ZohoBooksItemAttributes.isGrouped(item)).isFalse();
        assertThat(ZohoBooksItemAttributes.size(item)).isNull();
        assertThat(ZohoBooksItemAttributes.color(item)).isNull();
        assertThat(ZohoBooksItemAttributes.parentDisplayName(item)).isEqualTo("Muslin Swaddle");
    }

    @Test
    void stripsVariantSuffixFromCorruptGroupName() {
        Map<String, Object> item = Map.of(
            "group_id",
            "grp-singleton",
            "group_name",
            "Book Style Tying Jabla — 0-3 M / Aqua",
            "name",
            "Book Style Tying Jabla — 0-3 M / Aqua",
            "attribute_name1",
            "Size",
            "attribute_option_name1",
            "0-3 M",
            "attribute_name2",
            "color",
            "attribute_option_name2",
            "Aqua"
        );
        assertThat(ZohoBooksItemAttributes.parentDisplayName(item)).isEqualTo("Book Style Tying Jabla");
        assertThat(ZohoBooksItemAttributes.stripVariantSuffix(item.get("group_name").toString()))
            .isEqualTo("Book Style Tying Jabla");
    }

    @Test
    void parsesMnSpacedColorThenSizeAndPipeDescription() {
        Map<String, Object> named = Map.of(
            "group_id",
            "grp-swaddle",
            "group_name",
            "Baby Swaddle",
            "name",
            "Baby Swaddle - Pink Buds - COMMON SIZE",
            "sku",
            "MN-NEW-141-CS-P02"
        );
        assertThat(ZohoBooksItemAttributes.isVariantSku(named)).isTrue();
        assertThat(ZohoBooksItemAttributes.parentDisplayName(named)).isEqualTo("Baby Swaddle");
        assertThat(ZohoBooksItemAttributes.size(named)).isEqualTo("COMMON SIZE");
        assertThat(ZohoBooksItemAttributes.color(named)).isEqualTo("Pink Buds");

        Map<String, Object> fromDesc = new java.util.LinkedHashMap<>();
        fromDesc.put("group_id", "grp-swaddle");
        fromDesc.put("group_name", "Baby Swaddle");
        fromDesc.put("name", "Baby Swaddle - Aqua - COMMON SIZE");
        fromDesc.put("description", "Baby Swaddle | Age: COMMON SIZE | Print: Aqua");
        fromDesc.put("attribute_name1", "");
        fromDesc.put("attribute_option_name1", "");
        assertThat(ZohoBooksItemAttributes.size(fromDesc)).isEqualTo("COMMON SIZE");
        assertThat(ZohoBooksItemAttributes.color(fromDesc)).isEqualTo("Aqua");
    }

    @Test
    void treatsPushedNameSuffixAsVariantSkuWithoutGroupId() {
        Map<String, Object> item = Map.of("name", "Horizon Set — S / Navy", "sku", "HORIZON-S-NAVY");
        assertThat(ZohoBooksItemAttributes.isGrouped(item)).isFalse();
        assertThat(ZohoBooksItemAttributes.isVariantSku(item)).isTrue();
        assertThat(ZohoBooksItemAttributes.hasVariantNameSuffix("Horizon Set — S / Navy")).isTrue();
        assertThat(ZohoBooksItemAttributes.hasVariantNameSuffix("Muslin Swaddle")).isFalse();
        assertThat(ZohoBooksItemAttributes.parentDisplayName(item)).isEqualTo("Horizon Set");
    }

    @Test
    void isActive_readsStatusAndIsActiveFlag() {
        assertThat(ZohoBooksItemAttributes.isActive(Map.of("status", "active"))).isTrue();
        assertThat(ZohoBooksItemAttributes.isActive(Map.of("status", "inactive"))).isFalse();
        assertThat(ZohoBooksItemAttributes.isActive(Map.of("is_active", false))).isFalse();
        assertThat(ZohoBooksItemAttributes.isActive(Map.of("is_active", true, "status", "inactive"))).isTrue();
        assertThat(ZohoBooksItemAttributes.isActive(Map.of("name", "No status"))).isTrue();
    }
}
