package com.miniandmore.app.domain.enumeration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PackageTypeTest {

    @Test
    void parse_acceptsZohoParcelTypesAndDefaultsBlankToCustom() {
        assertThat(PackageType.parse(null)).contains(PackageType.CUSTOM);
        assertThat(PackageType.parse("")).contains(PackageType.CUSTOM);
        assertThat(PackageType.parse("box")).contains(PackageType.BOX);
        assertThat(PackageType.parse("ENVELOPE")).contains(PackageType.ENVELOPE);
        assertThat(PackageType.parse("custom")).contains(PackageType.CUSTOM);
        assertThat(PackageType.parse("pallet")).isEmpty();
        assertThat(PackageType.BOX.toApi()).isEqualTo("box");
    }
}
