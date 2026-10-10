package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ZohoShipmentPickBestPackageMatrixTest {

    @Test
    void pickBestZohoPackage_coversEmptyNonMapRankAndDelivery() {
        assertThat(invoke(null)).isNull();
        assertThat(invoke("not-a-list")).isNull();
        assertThat(invoke(List.of())).isNull();
        assertThat(invoke(List.of("skip", 12))).isNull();

        Map<String, Object> unranked = Map.of("package_id", "p0");
        Map<String, Object> shipped = Map.of("shipment_id", "shp-1");
        Map<String, Object> nested = Map.of("shipment_order", Map.of("shipment_id", "nested"));
        Map<String, Object> delivered = Map.of("status", "delivered");

        assertThat(invoke(List.of(unranked))).isEqualTo(unranked);
        assertThat(invoke(List.of(unranked, shipped))).isEqualTo(shipped);
        assertThat(invoke(List.of(unranked, nested))).isEqualTo(nested);
        assertThat(invoke(List.of(shipped, delivered))).isEqualTo(delivered);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invoke(Object raw) {
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(
            ZohoShipmentWebhookHandlerService.class,
            "pickBestZohoPackage",
            raw
        );
    }
}
