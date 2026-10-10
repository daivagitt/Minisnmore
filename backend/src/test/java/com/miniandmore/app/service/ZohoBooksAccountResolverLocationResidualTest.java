package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoBooksAccountResolverLocationResidualTest {

    @Mock
    private ZohoBooksClient booksClient;

    private ApplicationProperties properties;
    private ZohoBooksAccountResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new ApplicationProperties();
        resolver = new ZohoBooksAccountResolver(booksClient, properties);
    }

    @Test
    void resolveLocationId_secondCallUsesCache() {
        when(booksClient.listLocations()).thenReturn(List.of(Map.of("location_id", "loc-cache", "is_primary", true)));

        assertThat(resolver.resolveLocationId()).contains("loc-cache");
        assertThat(resolver.resolveLocationId()).contains("loc-cache");
        verify(booksClient, times(1)).listLocations();
    }

    @Test
    void resolveLocationId_configuredPresentInListIsAccepted() {
        properties.getZoho().setDefaultLocationId("loc-keep");
        when(booksClient.listLocations())
            .thenReturn(List.of(Map.of("location_id", "loc-keep", "location_name", "Keep"), Map.of("location_id", "other")));

        assertThat(resolver.resolveLocationId()).contains("loc-keep");
    }

    @Test
    void resolveLocationId_skipsBlankIdsAndUsesFallbackWhenNoPrimary() {
        properties.getZoho().setDefaultLocationName("");
        List<Map<String, Object>> locations = new ArrayList<>();
        locations.add(new HashMap<>());
        locations.add(Map.of("location_id", "  "));
        locations.add(Map.of("location_id", "loc-fallback", "location_name", "A", "is_primary", false));
        locations.add(Map.of("location_id", "loc-2", "location_name", "B", "is_primary", "false"));
        when(booksClient.listLocations()).thenReturn(locations);

        assertThat(resolver.resolveLocationId()).contains("loc-fallback");
    }

    @Test
    void resolveLocationId_nullLocationsWithNoConfigured_returnsEmpty() {
        when(booksClient.listLocations()).thenReturn(null);
        assertThat(resolver.resolveLocationId()).isEmpty();
    }

    @Test
    void resolveLocationId_booleanPrimaryWinsWhenNameBlank() {
        properties.getZoho().setDefaultLocationName("   ");
        when(booksClient.listLocations())
            .thenReturn(
                List.of(
                    Map.of("location_id", "loc-a", "location_name", "A", "is_primary", Boolean.FALSE),
                    Map.of("location_id", "loc-b", "location_name", "B", "is_primary", Boolean.TRUE)
                )
            );

        assertThat(resolver.resolveLocationId()).contains("loc-b");
    }
}
