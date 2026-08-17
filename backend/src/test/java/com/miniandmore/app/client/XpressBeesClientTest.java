package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XpressBeesClientTest {

    private XpressBeesClient client;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.XpressBees config = properties.getXpressbees();
        config.setEmail("info@example.com");
        config.setPassword("secret");
        config.setOriginPincode("560074");
        client = new XpressBeesClient(properties, new ObjectMapper());
    }

    @Test
    void isConfigured_requiresEmailPasswordAndOrigin() {
        assertThat(client.isConfigured()).isTrue();

        ApplicationProperties empty = new ApplicationProperties();
        XpressBeesClient disabled = new XpressBeesClient(empty, new ObjectMapper());
        assertThat(disabled.isConfigured()).isFalse();
    }

    @Test
    void parseServiceabilityResponse_success_returnsBestOption() {
        String body =
            """
            {
              "status": true,
              "data": [
                {
                  "id": "4",
                  "name": "Xpressbees 10 K.G",
                  "eddDays": 7,
                  "destination_city": "Mumbai",
                  "destination_state": "Maharashtra",
                  "destination_pincode": "400067"
                },
                {
                  "id": "1",
                  "name": "Surface Xpressbees 0.5 K.G",
                  "eddDays": 2,
                  "destination_city": "Mumbai",
                  "destination_state": "Maharashtra",
                  "destination_pincode": "400067"
                }
              ]
            }
            """;

        Optional<XpressBeesClient.ServiceabilityResult> result = client.parseServiceabilityResponse(body, "400067");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().city()).isEqualTo("Mumbai");
        assertThat(result.orElseThrow().state()).isEqualTo("Maharashtra");
        assertThat(result.orElseThrow().eddDays()).isEqualTo(2);
        assertThat(result.orElseThrow().eta()).isEqualTo("2 days");
    }

    @Test
    void parseServiceabilityResponse_notServiceable_returnsEmpty() {
        String body =
            """
            {"status": false, "data": "Destination Pincode Not Servicable."}
            """;

        assertThat(client.parseServiceabilityResponse(body, "522235")).isEmpty();
    }

    @Test
    void formatEta_handlesSameDayAndPlural() {
        assertThat(XpressBeesClient.formatEta(0)).isEqualTo("Same day");
        assertThat(XpressBeesClient.formatEta(1)).isEqualTo("1 day");
        assertThat(XpressBeesClient.formatEta(4)).isEqualTo("4 days");
    }
}
