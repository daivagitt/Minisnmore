package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.service.dto.PincodeLookupDTO;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PincodeLookupServiceTest {

    private PincodeLookupService pincodeLookupService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        XpressBeesClient xpressBeesClient = Mockito.mock(XpressBeesClient.class);
        Mockito.when(xpressBeesClient.isConfigured()).thenReturn(false);
        pincodeLookupService = new PincodeLookupService(objectMapper, xpressBeesClient);
    }

    @Test
    void lookup_invalidPincode_returnsEmpty() {
        assertThat(pincodeLookupService.lookup(null)).isEmpty();
        assertThat(pincodeLookupService.lookup("")).isEmpty();
        assertThat(pincodeLookupService.lookup("012345")).isEmpty();
        assertThat(pincodeLookupService.lookup("abc123")).isEmpty();
        assertThat(pincodeLookupService.lookup("12345")).isEmpty();
    }

    @Test
    void parseZippopotamResponse_withSampleJson() {
        String body =
            """
            {
              "post code": "560001",
              "country": "India",
              "places": [
                {
                  "place name": "Bangalore",
                  "state": "Karnataka"
                }
              ]
            }
            """;

        Optional<PincodeLookupDTO> result = pincodeLookupService.parseZippopotamResponse(body, "560001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().city()).isEqualTo("Bangalore");
        assertThat(result.orElseThrow().state()).isEqualTo("Karnataka");
        assertThat(result.orElseThrow().eta()).isEqualTo("2–3 days");
    }

    @Test
    void parseIndiaPostResponse_withSampleJson() {
        String body =
            """
            [
              {
                "Status": "Success",
                "PostOffice": [
                  {
                    "District": "Jaipur",
                    "State": "Rajasthan"
                  }
                ]
              }
            ]
            """;

        Optional<PincodeLookupDTO> result = pincodeLookupService.parseIndiaPostResponse(body, "302001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().city()).isEqualTo("Jaipur");
        assertThat(result.orElseThrow().state()).isEqualTo("Rajasthan");
        assertThat(result.orElseThrow().eta()).isEqualTo("4–6 days");
    }

    @Test
    void deliveryEta_metroCity_returnsShortWindow() {
        assertThat(PincodeLookupService.deliveryEta("302001", "Maharashtra", "Mumbai")).isEqualTo("2–3 days");
        assertThat(PincodeLookupService.deliveryEta("110001", "Delhi", "New Delhi")).isEqualTo("2–3 days");
    }

    @Test
    void deliveryEta_nonMetro_returnsLongerWindow() {
        assertThat(PincodeLookupService.deliveryEta("302001", "Rajasthan", "Jaipur")).isEqualTo("4–6 days");
        assertThat(PincodeLookupService.deliveryEta("506001", "Telangana", "Warangal")).isEqualTo("4–6 days");
    }

    @Test
    void deliveryEta_metroPinPrefix_withoutMetroName_returnsShortWindow() {
        assertThat(PincodeLookupService.deliveryEta("560099", "Karnataka", "Hebbal")).isEqualTo("2–3 days");
    }

    @Test
    void parseZippopotamResponse_emptyPlaces_returnsEmpty() {
        assertThat(pincodeLookupService.parseZippopotamResponse("{\"places\":[]}", "560001")).isEmpty();
    }

    @Test
    void parseIndiaPostResponse_failureStatus_returnsEmpty() {
        assertThat(pincodeLookupService.parseIndiaPostResponse("[{\"Status\":\"Failure\"}]", "560001")).isEmpty();
    }

    @Test
    void parseIndiaPostResponse_emptyPostOffice_returnsEmpty() {
        assertThat(
            pincodeLookupService.parseIndiaPostResponse("[{\"Status\":\"Success\",\"PostOffice\":[]}]", "560001")
        ).isEmpty();
    }

    @Test
    void parseZippopotamResponse_blankCity_returnsEmpty() {
        assertThat(
            pincodeLookupService.parseZippopotamResponse(
                "{\"places\":[{\"place name\":\"\",\"state\":\"Karnataka\"}]}",
                "560001"
            )
        ).isEmpty();
    }

    @Test
    void parseIndiaPostResponse_invalidJson_returnsEmpty() {
        assertThat(pincodeLookupService.parseIndiaPostResponse("not-json", "560001")).isEmpty();
    }
}
