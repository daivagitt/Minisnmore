package com.miniandmore.app.domain.enumeration;

import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Zoho Inventory parcel types used when creating a package.
 * Matches the New Shipment / package "Parcel Type" options: custom dimensions, box, or envelope.
 */
public enum PackageType {
    CUSTOM,
    BOX,
    ENVELOPE;

    public String toApi() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<PackageType> parse(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Optional.of(CUSTOM);
        }
        try {
            return Optional.of(PackageType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
