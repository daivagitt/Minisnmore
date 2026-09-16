package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.service.ImagePreviewService;
import com.miniandmore.app.service.ImagePreviewSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(InternalMediaPreviewResource.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class InternalMediaPreviewResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImagePreviewService imagePreviewService;

    @Test
    void preview_returnsJpegWithCacheControl() throws Exception {
        when(imagePreviewService.preview("https://cdn.example.com/photo.jpg", 240)).thenReturn(new byte[] { 1, 2, 3 });

        mockMvc
            .perform(get("/internal/v1/media/preview").param("u", "https://cdn.example.com/photo.jpg").param("w", "240"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andExpect(header().string("Cache-Control", ImagePreviewSupport.PREVIEW_CACHE_CONTROL))
            .andExpect(content().bytes(new byte[] { 1, 2, 3 }));
    }

    @Test
    void preview_rejectsForeignHost() throws Exception {
        when(imagePreviewService.preview(eq("https://evil.example/x.jpg"), isNull())).thenThrow(
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL is not a catalog storage URL")
        );

        mockMvc
            .perform(get("/internal/v1/media/preview").param("u", "https://evil.example/x.jpg"))
            .andExpect(status().isBadRequest());
    }
}
