package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.net.http.HttpClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ImagePreviewServiceTest {

    @Mock
    private S3StorageService storage;

    @Mock
    private HttpClient httpClient;

    private ImagePreviewService service;

    @BeforeEach
    void setUp() {
        service = new ImagePreviewService(storage, httpClient);
        org.mockito.Mockito.lenient().when(storage.publicBaseUrl()).thenReturn("https://cdn.example.com/my-bucket");
    }

    @Test
    void preview_rejectsForeignHost() {
        assertThatThrownBy(() -> service.preview("https://evil.example/photo.jpg", 240))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void preview_returnsStoredThumbWithoutReprocessing() throws Exception {
        byte[] thumb = ImagePreviewSupportTest.jpeg(80, 80, Color.GREEN);
        when(storage.getObjectBytes("products/photo.jpg.thumb.jpg")).thenReturn(Optional.of(thumb));

        byte[] result = service.preview("https://cdn.example.com/my-bucket/products/photo.jpg", 240);

        assertThat(result).isEqualTo(thumb);
        verify(storage, never()).getObjectBytes("products/photo.jpg");
        verify(storage, never()).ensureImageDerivatives(any(), any());
        verify(storage, never()).putPublicImage(any(), any());
    }

    @Test
    void preview_resizesOriginalAndWritesThumb() throws Exception {
        byte[] original = ImagePreviewSupportTest.jpeg(400, 200, Color.RED);
        when(storage.getObjectBytes("products/photo.jpg.thumb.jpg")).thenReturn(Optional.empty());
        when(storage.getObjectBytes("products/photo.jpg")).thenReturn(Optional.of(original));

        byte[] result = service.preview("https://cdn.example.com/my-bucket/products/photo.jpg", 240);

        assertThat(result.length).isGreaterThan(0);
        verify(storage).ensureImageDerivatives(eq("products/photo.jpg"), any(byte[].class));
        assertThat(service.preview("https://cdn.example.com/my-bucket/products/photo.jpg", 240)).isSameAs(result);
        verify(storage).getObjectBytes("products/photo.jpg");
    }

    @Test
    void preview_unreadableOriginal_returnsNotFound() {
        when(storage.getObjectBytes("products/photo.jpg.thumb.jpg")).thenReturn(Optional.empty());
        when(storage.getObjectBytes("products/photo.jpg")).thenReturn(Optional.of("not-an-image".getBytes()));

        assertThatThrownBy(() -> service.preview("https://cdn.example.com/my-bucket/products/photo.jpg", null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        verify(storage, never()).putPublicImage(any(), any());
        verify(storage, never()).ensureImageDerivatives(any(), any());
    }

    @Test
    void defaultConstructor_instantiates() {
        ImagePreviewService defaultService = new ImagePreviewService(storage);
        assertThat(defaultService).isNotNull();
    }

    @Test
    void preview_downloadsPublic_whenNotInStorage_success() throws Exception {
        byte[] original = ImagePreviewSupportTest.jpeg(300, 300, Color.BLUE);
        when(storage.getObjectBytes("products/photo.jpg.thumb.jpg")).thenReturn(Optional.empty());
        when(storage.getObjectBytes("products/photo.jpg")).thenReturn(Optional.empty());

        java.net.http.HttpResponse<java.io.InputStream> httpResp = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        when(httpResp.statusCode()).thenReturn(200);
        when(httpResp.body()).thenReturn(new java.io.ByteArrayInputStream(original));
        when(httpResp.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of(), (k, v) -> true));
        org.mockito.Mockito.doReturn(httpResp).when(httpClient).send(any(java.net.http.HttpRequest.class), any());

        byte[] result = service.preview("https://cdn.example.com/my-bucket/products/photo.jpg", 240);
        assertThat(result).isNotEmpty();
    }

    @Test
    void preview_downloadsPublic_non200_throwsNotFound() throws Exception {
        when(storage.getObjectBytes("products/photo.jpg.thumb.jpg")).thenReturn(Optional.empty());
        when(storage.getObjectBytes("products/photo.jpg")).thenReturn(Optional.empty());

        java.net.http.HttpResponse<java.io.InputStream> httpResp = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        when(httpResp.statusCode()).thenReturn(404);
        org.mockito.Mockito.doReturn(httpResp).when(httpClient).send(any(java.net.http.HttpRequest.class), any());

        assertThatThrownBy(() -> service.preview("https://cdn.example.com/my-bucket/products/photo.jpg", 240))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void preview_downloadsPublic_tooLargeHeader_throwsBadRequest() throws Exception {
        when(storage.getObjectBytes("products/photo.jpg.thumb.jpg")).thenReturn(Optional.empty());
        when(storage.getObjectBytes("products/photo.jpg")).thenReturn(Optional.empty());

        java.net.http.HttpResponse<java.io.InputStream> httpResp = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        when(httpResp.statusCode()).thenReturn(200);
        when(httpResp.headers()).thenReturn(
            java.net.http.HttpHeaders.of(java.util.Map.of("Content-Length", java.util.List.of("20000000")), (k, v) -> true)
        );
        org.mockito.Mockito.doReturn(httpResp).when(httpClient).send(any(java.net.http.HttpRequest.class), any());

        assertThatThrownBy(() -> service.preview("https://cdn.example.com/my-bucket/products/photo.jpg", 240))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void preview_downloadsPublic_emptyBody_throwsNotFound() throws Exception {
        when(storage.getObjectBytes("products/photo.jpg.thumb.jpg")).thenReturn(Optional.empty());
        when(storage.getObjectBytes("products/photo.jpg")).thenReturn(Optional.empty());

        java.net.http.HttpResponse<java.io.InputStream> httpResp = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        when(httpResp.statusCode()).thenReturn(200);
        when(httpResp.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of(), (k, v) -> true));
        when(httpResp.body()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));
        org.mockito.Mockito.doReturn(httpResp).when(httpClient).send(any(java.net.http.HttpRequest.class), any());

        assertThatThrownBy(() -> service.preview("https://cdn.example.com/my-bucket/products/photo.jpg", 240))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void preview_downloadsPublic_networkError_throwsNotFound() throws Exception {
        when(storage.getObjectBytes("products/photo.jpg.thumb.jpg")).thenReturn(Optional.empty());
        when(storage.getObjectBytes("products/photo.jpg")).thenReturn(Optional.empty());
        when(httpClient.send(any(java.net.http.HttpRequest.class), any())).thenThrow(new java.io.IOException("network timeout"));

        assertThatThrownBy(() -> service.preview("https://cdn.example.com/my-bucket/products/photo.jpg", 240))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
