package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.domain.Banner;
import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.Policy;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.repository.*;
import com.miniandmore.app.service.PincodeLookupService;
import com.miniandmore.app.service.dto.*;
import com.miniandmore.app.service.mapper.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalStorefrontResource.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalStorefrontResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private BlogPostRepository blogPostRepository;

    @MockitoBean
    private BannerRepository bannerRepository;

    @MockitoBean
    private HomeSectionRepository homeSectionRepository;

    @MockitoBean
    private PageVisibilityRepository pageVisibilityRepository;

    @MockitoBean
    private SiteSettingRepository siteSettingRepository;

    @MockitoBean
    private PolicyRepository policyRepository;

    @MockitoBean
    private CategoryMapper categoryMapper;

    @MockitoBean
    private ProductMapper productMapper;

    @MockitoBean
    private BlogPostMapper blogPostMapper;

    @MockitoBean
    private BannerMapper bannerMapper;

    @MockitoBean
    private HomeSectionMapper homeSectionMapper;

    @MockitoBean
    private PageVisibilityMapper pageVisibilityMapper;

    @MockitoBean
    private SiteSettingMapper siteSettingMapper;

    @MockitoBean
    private PolicyMapper policyMapper;

    @MockitoBean
    private PincodeLookupService pincodeLookupService;

    @Test
    void categories_returns200() throws Exception {
        Category category = new Category().id(1L).name("Tops");
        CategoryDTO dto = new CategoryDTO();
        dto.setId(1L);
        dto.setName("Tops");
        when(categoryRepository.findAllByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(category));
        when(categoryMapper.toDto(category)).thenReturn(dto);

        mockMvc
            .perform(get("/internal/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].name").value("Tops"));
    }

    @Test
    void products_returns200() throws Exception {
        Product product = new Product().id(1L).name("Dress");
        ProductDTO dto = new ProductDTO();
        dto.setId(1L);
        dto.setName("Dress");
        when(productRepository.findAllActiveWithCategoryOrderBySortOrder()).thenReturn(List.of(product));
        when(productMapper.toDto(product)).thenReturn(dto);

        mockMvc
            .perform(get("/internal/v1/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("Dress"));
    }

    @Test
    void productBySlug_returns200() throws Exception {
        Product product = new Product().id(1L).slug("dress");
        ProductDTO dto = new ProductDTO();
        dto.setSlug("dress");
        when(productRepository.findOneActiveBySlugWithCategory("dress")).thenReturn(Optional.of(product));
        when(productMapper.toDto(product)).thenReturn(dto);

        mockMvc.perform(get("/internal/v1/products/dress")).andExpect(status().isOk()).andExpect(jsonPath("$.data.slug").value("dress"));
    }

    @Test
    void siteSettings_returns200() throws Exception {
        SiteSetting setting = new SiteSetting().id(1L);
        SiteSettingDTO dto = new SiteSettingDTO();
        dto.setId(1L);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        when(siteSettingMapper.toDto(setting)).thenReturn(dto);

        mockMvc.perform(get("/internal/v1/site-settings")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void pincode_returns200() throws Exception {
        when(pincodeLookupService.lookup("560001")).thenReturn(Optional.of(new PincodeLookupDTO("Bangalore", "Karnataka", "2–3 days")));

        mockMvc
            .perform(get("/internal/v1/pincode/560001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.city").value("Bangalore"));
    }

    @Test
    void pincode_returns200WithNullWhenUnknown() throws Exception {
        when(pincodeLookupService.lookup("522235")).thenReturn(Optional.empty());

        mockMvc
            .perform(get("/internal/v1/pincode/522235"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void pincode_returns400ForInvalidFormat() throws Exception {
        mockMvc.perform(get("/internal/v1/pincode/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void policy_returns200() throws Exception {
        Policy policy = new Policy().id(1L).slug("privacy");
        PolicyDTO dto = new PolicyDTO();
        dto.setSlug("privacy");
        when(policyRepository.findOneBySlug("privacy")).thenReturn(Optional.of(policy));
        when(policyMapper.toDto(policy)).thenReturn(dto);

        mockMvc.perform(get("/internal/v1/policies/privacy")).andExpect(status().isOk()).andExpect(jsonPath("$.data.slug").value("privacy"));
    }

    @Test
    void banners_returns200() throws Exception {
        Banner banner = new Banner().id(1L);
        BannerDTO dto = new BannerDTO();
        dto.setId(1L);
        when(bannerRepository.findAllByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(banner));
        when(bannerMapper.toDto(any(Banner.class))).thenReturn(dto);

        mockMvc.perform(get("/internal/v1/banners")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void blogPosts_returns200() throws Exception {
        when(blogPostRepository.findAllByPublishedTrueOrderByPublishedAtDesc()).thenReturn(List.of());

        mockMvc.perform(get("/internal/v1/blog-posts")).andExpect(status().isOk());
    }

    @Test
    void subscribePage_returns200() throws Exception {
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/v1/subscribe-page")).andExpect(status().isOk());
    }

    @Test
    void pageVisibility_returns200() throws Exception {
        when(pageVisibilityRepository.findAllByOrderByPageKeyAsc()).thenReturn(List.of());

        mockMvc.perform(get("/internal/v1/page-visibility")).andExpect(status().isOk());
    }
}
