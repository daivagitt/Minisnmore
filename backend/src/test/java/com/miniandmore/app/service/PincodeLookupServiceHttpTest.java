package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @BeforeEach
    void setUp() {
        service = new PincodeLookupService(new ObjectMapper());
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
}
