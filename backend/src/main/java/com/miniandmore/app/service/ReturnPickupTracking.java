package com.miniandmore.app.service;

import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

/** Customer-facing reverse-pickup tracking URL. Does not invent an ExpressBees link for other carriers. */
public final class ReturnPickupTracking {

    private ReturnPickupTracking() {}

    public static boolean isXpressBees(ReturnLogisticsMode mode, String carrierName) {
        if (mode == ReturnLogisticsMode.XPRESSBEES) {
            return true;
        }
        String carrier = StringUtils.trimToEmpty(carrierName).toLowerCase(Locale.ROOT);
        return carrier.contains("xpressbees") || carrier.contains("expressbees");
    }

    public static String url(OrderReturn orderReturn) {
        if (orderReturn == null) {
            return null;
        }
        return url(
            orderReturn.getTrackingUrl(),
            orderReturn.getReverseAwb(),
            orderReturn.getLogisticsMode(),
            orderReturn.getCarrierName()
        );
    }

    public static String url(String existingTrackingUrl, String reverseAwb, ReturnLogisticsMode mode, String carrierName) {
        String stored = StringUtils.trimToNull(existingTrackingUrl);
        if (stored != null) {
            return stored;
        }
        if (!isXpressBees(mode, carrierName)) {
            return null;
        }
        String awb = StringUtils.trimToNull(reverseAwb);
        if (awb == null) {
            return null;
        }
        return XpressBeesClient.DEFAULT_TRACKING_URL_TEMPLATE.replace("{awb}", XpressBeesClient.encodeAwb(awb));
    }
}
