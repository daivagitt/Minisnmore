package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.repository.SiteSettingRepository;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PincodeLookupServiceHttpTest {

    private PincodeLookupService service;
    private HttpClient httpClient;
    private XpressBeesClient xpressBeesClient;

    @BeforeEach
    void setUp() {
        xpressBeesClient = mock(XpressBeesClient.class);
        when(xpressBeesClient.isConfigured()).thenReturn(false);
        SiteSettingRepository siteSettingRepository = mock(SiteSettingRepository.class);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        service = new PincodeLookupService(new ObjectMapper(), xpressBeesClient, siteSettingRepository);
        httpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
    }

    @Test
    void lookup_zippopotamSuccess_returnsResult() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
            """
            {"places":[{"place name":"Bangalore","state":"Karnataka"}]}
            """
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Optional<?> result = service.lookup("560001");

        assertThat(result).isPresent();
    }

    @Test
    void lookup_indiaPostSuccess_returnsResult() throws Exception {
        HttpResponse<String> hit = mock(HttpResponse.class);
        when(hit.statusCode()).thenReturn(200);
        when(hit.body()).thenReturn(
            """
            [{"Status":"Success","PostOffice":[{"District":"Guntur","State":"Andhra Pradesh"}]}]
            """
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(hit);

        Optional<?> result = service.lookup("522235");

        assertThat(result).isPresent();
    }

    @Test
    void lookup_indiaPostFails_fallsBackToZippopotam() throws Exception {
        HttpResponse<String> miss = mock(HttpResponse.class);
        when(miss.statusCode()).thenReturn(404);
        HttpResponse<String> hit = mock(HttpResponse.class);
        when(hit.statusCode()).thenReturn(200);
        when(hit.body()).thenReturn(
            """
            {"places":[{"place name":"Bangalore","state":"Karnataka"}]}
            """
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(miss, hit);

        Optional<?> result = service.lookup("560001");

        assertThat(result).isPresent();
    }

    @Test
    void lookup_httpException_returnsEmpty() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new RuntimeException("network"));

        assertThat(service.lookup("560001")).isEmpty();
    }

    @Test
    void lookup_non200FromBothProviders_returnsEmpty() throws Exception {
        HttpResponse<String> miss = mock(HttpResponse.class);
        when(miss.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(miss, miss);

        assertThat(service.lookup("560001")).isEmpty();
    }

    @Test
    void lookup_xpressBeesConfigured_usesServiceability() {
        when(xpressBeesClient.isConfigured()).thenReturn(true);
        when(xpressBeesClient.checkServiceability("560001")).thenReturn(
            Optional.of(new XpressBeesClient.ServiceabilityResult("Bangalore", "Karnataka", "2 days", 2, "560001"))
        );

        Optional<com.miniandmore.app.service.dto.PincodeLookupDTO> result = service.lookup("560001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().eta()).isEqualTo("5 days");
    }

    @Test
    void lookup_xpressBeesConfigured_notServiceable_returnsEmpty() {
        when(xpressBeesClient.isConfigured()).thenReturn(true);
        when(xpressBeesClient.checkServiceability("522235")).thenReturn(Optional.empty());

        assertThat(service.lookup("522235")).isEmpty();
    }
}
