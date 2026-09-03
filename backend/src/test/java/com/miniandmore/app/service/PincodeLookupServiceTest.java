package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.service.dto.PincodeLookupDTO;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PincodeLookupServiceTest {

    private static final int DEFAULT_BUFFER = SiteSetting.DEFAULT_DELIVERY_ETA_BUFFER_DAYS;

    private PincodeLookupService pincodeLookupService;
    private XpressBeesClient xpressBeesClient;
    private SiteSettingRepository siteSettingRepository;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        xpressBeesClient = Mockito.mock(XpressBeesClient.class);
        siteSettingRepository = Mockito.mock(SiteSettingRepository.class);
        Mockito.when(xpressBeesClient.isConfigured()).thenReturn(false);
        Mockito.when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        pincodeLookupService = new PincodeLookupService(objectMapper, xpressBeesClient, siteSettingRepository);
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
    void adjustEtaWithBuffer_addsDefaultBufferDays() {
        assertThat(PincodeLookupService.adjustEtaWithBuffer("2–3 days", DEFAULT_BUFFER)).isEqualTo("5–6 days");
        assertThat(PincodeLookupService.adjustEtaWithBuffer("4–6 days", DEFAULT_BUFFER)).isEqualTo("7–9 days");
        assertThat(PincodeLookupService.adjustEtaWithBuffer("2 days", DEFAULT_BUFFER)).isEqualTo("5 days");
        assertThat(PincodeLookupService.adjustEtaWithBuffer("1 day", DEFAULT_BUFFER)).isEqualTo("4 days");
        assertThat(PincodeLookupService.adjustEtaWithBuffer("Same day", DEFAULT_BUFFER)).isEqualTo("3 days");
    }

    @Test
    void adjustEtaWithBuffer_customBufferDays() {
        assertThat(PincodeLookupService.adjustEtaWithBuffer("2–3 days", 2)).isEqualTo("4–5 days");
        assertThat(PincodeLookupService.adjustEtaWithBuffer("4–6 days", 0)).isEqualTo("4–6 days");
        assertThat(PincodeLookupService.adjustEtaWithBuffer("Same day", 0)).isEqualTo("Same day");
        assertThat(PincodeLookupService.adjustEtaWithBuffer("1 day", 1)).isEqualTo("2 days");
    }

    @Test
    void lookup_appliesConfiguredDeliveryEtaBufferDays() {
        Mockito.when(xpressBeesClient.isConfigured()).thenReturn(true);
        Mockito
            .when(xpressBeesClient.checkServiceability("560001"))
            .thenReturn(
                Optional.of(new XpressBeesClient.ServiceabilityResult("Bangalore", "Karnataka", "2–3 days", 3, "560001"))
            );
        Mockito
            .when(siteSettingRepository.findFirstByOrderByIdAsc())
            .thenReturn(Optional.of(new SiteSetting().deliveryEtaBufferDays(2)));

        Optional<PincodeLookupDTO> result = pincodeLookupService.lookup("560001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().city()).isEqualTo("Bangalore");
        assertThat(result.orElseThrow().eta()).isEqualTo("4–5 days");
    }

    @Test
    void lookup_fallsBackToDefaultBufferWhenSettingMissing() {
        Mockito.when(xpressBeesClient.isConfigured()).thenReturn(true);
        Mockito
            .when(xpressBeesClient.checkServiceability("560001"))
            .thenReturn(
                Optional.of(new XpressBeesClient.ServiceabilityResult("Bangalore", "Karnataka", "2–3 days", 3, "560001"))
            );
        Mockito.when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        Optional<PincodeLookupDTO> result = pincodeLookupService.lookup("560001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().eta()).isEqualTo("5–6 days");
    }

    @Test
    void lookup_ignoresOutOfRangeBufferAndUsesDefault() {
        Mockito.when(xpressBeesClient.isConfigured()).thenReturn(true);
        Mockito
            .when(xpressBeesClient.checkServiceability("560001"))
            .thenReturn(
                Optional.of(new XpressBeesClient.ServiceabilityResult("Bangalore", "Karnataka", "2–3 days", 3, "560001"))
            );
        Mockito
            .when(siteSettingRepository.findFirstByOrderByIdAsc())
            .thenReturn(Optional.of(new SiteSetting().deliveryEtaBufferDays(999)));

        Optional<PincodeLookupDTO> result = pincodeLookupService.lookup("560001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().eta()).isEqualTo("5–6 days");
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

    @Test
    void adjustEtaWithBuffer_blankOrUnparseable_returnsUnchanged() {
        assertThat(PincodeLookupService.adjustEtaWithBuffer(null, DEFAULT_BUFFER)).isNull();
        assertThat(PincodeLookupService.adjustEtaWithBuffer("  ", DEFAULT_BUFFER)).isEqualTo("  ");
        assertThat(PincodeLookupService.adjustEtaWithBuffer("TBD", DEFAULT_BUFFER)).isEqualTo("TBD");
    }

    @Test
    void adjustEtaWithBuffer_sameDay_addsBufferDays() {
        assertThat(PincodeLookupService.adjustEtaWithBuffer("Same day", DEFAULT_BUFFER)).isEqualTo("3 days");
    }

    @Test
    void parseZippopotamResponse_missingPlaces_returnsEmpty() {
        assertThat(pincodeLookupService.parseZippopotamResponse("{}", "560001")).isEmpty();
    }
}
