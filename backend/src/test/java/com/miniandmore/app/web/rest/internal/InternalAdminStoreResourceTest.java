package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.domain.*;
import com.miniandmore.app.repository.*;
import com.miniandmore.app.service.*;
import com.miniandmore.app.service.dto.*;
import com.miniandmore.app.service.mapper.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalAdminStoreResource.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = "ROLE_ADMIN")
class InternalAdminStoreResourceTest {

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
    private CouponRepository couponRepository;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private BlogPostService blogPostService;

    @MockitoBean
    private BannerService bannerService;

    @MockitoBean
    private HomeSectionService homeSectionService;

    @MockitoBean
    private PageVisibilityService pageVisibilityService;

    @MockitoBean
    private SiteSettingService siteSettingService;

    @MockitoBean
    private PolicyService policyService;

    @MockitoBean
    private CouponService couponService;

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
    private CouponMapper couponMapper;

    @MockitoBean
    private LedgerlySyncService ledgerlySyncService;

    @MockitoBean
    private ZohoSyncService zohoSyncService;

    @BeforeEach
    void stubLists() {
        Category category = new Category().id(1L).name("Essentials");
        when(categoryRepository.count()).thenReturn(1L);
        when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(category));
        when(categoryMapper.toDto(category)).thenReturn(categoryDto(1L, "Essentials"));

        Product product = new Product().id(2L).name("Shorts").active(true);
        when(productRepository.count()).thenReturn(2L);
        when(productRepository.countByActiveTrue()).thenReturn(1L);
        when(productRepository.countWithSku()).thenReturn(1L);
        when(productRepository.findAllWithCategoryOrderBySortOrder()).thenReturn(List.of(product));
        when(productMapper.toDto(product)).thenReturn(productDto(2L, "Shorts"));

        BlogPost post = new BlogPost().id(3L).title("Post");
        when(blogPostRepository.count()).thenReturn(1L);
        when(blogPostRepository.findAllByOrderByIdDesc()).thenReturn(List.of(post));
        when(blogPostMapper.toDto(post)).thenReturn(blogPostDto(3L, "Post"));

        Banner banner = new Banner().id(4L).title("Hero");
        when(bannerRepository.count()).thenReturn(1L);
        when(bannerRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(banner));
        when(bannerMapper.toDto(banner)).thenReturn(bannerDto(4L, "Hero"));

        HomeSection section = new HomeSection().id(5L).sectionKey("hero");
        when(homeSectionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(section));
        when(homeSectionMapper.toDto(section)).thenReturn(homeSectionDto(5L, "hero"));

        PageVisibility page = new PageVisibility().id(6L).pageKey("shop").visible(true);
        when(pageVisibilityRepository.count()).thenReturn(1L);
        when(pageVisibilityRepository.findAllByOrderByPageKeyAsc()).thenReturn(List.of(page));
        when(pageVisibilityMapper.toDto(page)).thenReturn(pageVisibilityDto(6L, "shop"));

        SiteSetting siteSetting = new SiteSetting().id(7L).brandName("Mini");
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(siteSetting));
        when(siteSettingMapper.toDto(siteSetting)).thenReturn(siteSettingDto(7L, "Mini"));

        Policy policy = new Policy().id(8L).slug("privacy");
        when(policyRepository.count()).thenReturn(1L);
        when(policyRepository.findAllByOrderBySlugAsc()).thenReturn(List.of(policy));
        when(policyMapper.toDto(policy)).thenReturn(policyDto(8L, "privacy"));

        Coupon coupon = new Coupon().id(9L).code("WELCOME10");
        when(couponRepository.count()).thenReturn(1L);
        when(couponRepository.findAllByOrderByIdDesc()).thenReturn(List.of(coupon));
        when(couponMapper.toDto(coupon)).thenReturn(couponDto(9L, "WELCOME10"));
    }

    @Test
    void dashboard_returnsCounts() throws Exception {
        mockMvc
            .perform(get("/internal/v1/admin/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.products").value(2))
            .andExpect(jsonPath("$.data.activeProducts").value(1))
            .andExpect(jsonPath("$.data.inactiveProducts").value(1))
            .andExpect(jsonPath("$.data.productsWithSku").value(1))
            .andExpect(jsonPath("$.data.visiblePages").value(1));
    }

    @Test
    void listEndpoints_return200() throws Exception {
        mockMvc.perform(get("/internal/v1/admin/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/admin/products")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/admin/blog-posts")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/admin/banners")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/admin/home-sections")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/admin/page-visibility")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/admin/site-settings")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/admin/policies")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/admin/coupons")).andExpect(status().isOk());
    }

    @Test
    void deleteCategory_whenHasProducts_returnsConflict() throws Exception {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(productRepository.countByCategory_Id(1L)).thenReturn(2L);

        mockMvc.perform(delete("/internal/v1/admin/categories/1")).andExpect(status().isConflict());
    }

    @Test
    void deleteCategory_whenEmpty_deletes() throws Exception {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(productRepository.countByCategory_Id(1L)).thenReturn(0L);

        mockMvc
            .perform(delete("/internal/v1/admin/categories/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void createProduct_schedulesLedgerlySync() throws Exception {
        ProductDTO dto = productDto(10L, "New");
        dto.setSku("SKU-10");
        when(productService.save(any(ProductDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                post("/internal/v1/admin/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"slug\":\"new\",\"name\":\"New\",\"sku\":\"SKU-10\",\"price\":10,\"currency\":\"INR\",\"stock\":5,\"sortOrder\":0,\"featured\":false,\"trending\":false,\"newArrival\":false,\"bestSeller\":false,\"active\":true}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    void updateSiteSettings_withoutId_creates() throws Exception {
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(siteSettingService.save(any(SiteSettingDTO.class))).thenReturn(siteSettingDto(11L, "Mini & More"));

        mockMvc
            .perform(
                put("/internal/v1/admin/site-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"brandName\":\"Mini & More\",\"storeDiscountPercent\":20}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    void updateCategory_returnsUpdatedDto() throws Exception {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        CategoryDTO dto = categoryDto(1L, "Updated");
        when(categoryService.update(any(CategoryDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                put("/internal/v1/admin/categories/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Updated\",\"slug\":\"updated\",\"sortOrder\":0,\"active\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Updated"));
    }

    @Test
    void deleteBanner_deletesWhenExists() throws Exception {
        when(bannerRepository.existsById(4L)).thenReturn(true);

        mockMvc
            .perform(delete("/internal/v1/admin/banners/4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deleted").value(true));

        verify(bannerService).delete(4L);
    }

    @Test
    void deleteCoupon_deletesWhenExists() throws Exception {
        when(couponRepository.existsById(9L)).thenReturn(true);

        mockMvc.perform(delete("/internal/v1/admin/coupons/9")).andExpect(status().isOk());

        verify(couponService).delete(9L);
    }

    @Test
    void createCategory_returnsSavedDto() throws Exception {
        CategoryDTO dto = categoryDto(12L, "New Cat");
        when(categoryService.save(any(CategoryDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                post("/internal/v1/admin/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"New Cat\",\"slug\":\"new-cat\",\"sortOrder\":0,\"active\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("New Cat"));
    }

    @Test
    void updateCategory_whenMissing_returnsNotFound() throws Exception {
        when(categoryRepository.existsById(99L)).thenReturn(false);

        mockMvc
            .perform(
                put("/internal/v1/admin/categories/99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Ghost\",\"slug\":\"ghost\",\"sortOrder\":0,\"active\":true}")
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_schedulesLedgerlySync() throws Exception {
        when(productRepository.existsById(2L)).thenReturn(true);
        ProductDTO dto = productDto(2L, "Updated");
        dto.setSku("SKU-2");
        when(productService.update(any(ProductDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                put("/internal/v1/admin/products/2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"slug\":\"shorts\",\"name\":\"Updated\",\"sku\":\"SKU-2\",\"price\":10,\"currency\":\"INR\",\"stock\":5,\"sortOrder\":0,\"featured\":false,\"trending\":false,\"newArrival\":false,\"bestSeller\":false,\"active\":true}"
                    )
            )
            .andExpect(status().isOk());

        verify(ledgerlySyncService).syncProductAsync(2L);
    }

    @Test
    void createProduct_ledgerlySyncFailureStillReturnsOk() throws Exception {
        ProductDTO dto = productDto(13L, "Resilient");
        dto.setSku("SKU-13");
        when(productService.save(any(ProductDTO.class))).thenReturn(dto);
        doThrow(new RuntimeException("sync down")).when(ledgerlySyncService).syncProductAsync(13L);

        mockMvc
            .perform(
                post("/internal/v1/admin/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"slug\":\"resilient\",\"name\":\"Resilient\",\"sku\":\"SKU-13\",\"price\":10,\"currency\":\"INR\",\"stock\":5,\"sortOrder\":0,\"featured\":false,\"trending\":false,\"newArrival\":false,\"bestSeller\":false,\"active\":true}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(13));
    }

    @Test
    void deleteProduct_notifiesLedgerlyAndDeletes() throws Exception {
        when(productRepository.existsById(2L)).thenReturn(true);

        mockMvc
            .perform(delete("/internal/v1/admin/products/2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deleted").value(true));

        verify(ledgerlySyncService).notifyLedgerlyBeforeStoreDelete(2L);
        verify(productService).delete(2L);
    }

    @Test
    void clearVariantPhotos_returnsUpdatedProduct() throws Exception {
        when(productRepository.existsById(2L)).thenReturn(true);
        ProductDTO dto = productDto(2L, "Shorts");
        dto.setVariantsJson("[]");
        when(productService.clearVariantPhotos(2L)).thenReturn(dto);

        mockMvc
            .perform(post("/internal/v1/admin/products/2/clear-variant-photos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(2));

        verify(productService).clearVariantPhotos(2L);
    }

    @Test
    void createBlogPost_returnsSavedDto() throws Exception {
        BlogPostDTO dto = blogPostDto(14L, "Hello");
        when(blogPostService.save(any(BlogPostDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                post("/internal/v1/admin/blog-posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Hello\",\"slug\":\"hello\",\"content\":\"Body\",\"published\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Hello"));
    }

    @Test
    void updateBlogPost_returnsUpdatedDto() throws Exception {
        when(blogPostRepository.existsById(3L)).thenReturn(true);
        BlogPostDTO dto = blogPostDto(3L, "Revised");
        when(blogPostService.update(any(BlogPostDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                put("/internal/v1/admin/blog-posts/3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Revised\",\"slug\":\"revised\",\"content\":\"Body\",\"published\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Revised"));
    }

    @Test
    void deleteBlogPost_deletesWhenExists() throws Exception {
        when(blogPostRepository.existsById(3L)).thenReturn(true);

        mockMvc.perform(delete("/internal/v1/admin/blog-posts/3")).andExpect(status().isOk());

        verify(blogPostService).delete(3L);
    }

    @Test
    void createBanner_returnsSavedDto() throws Exception {
        BannerDTO dto = bannerDto(15L, "Promo");
        when(bannerService.save(any(BannerDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                post("/internal/v1/admin/banners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Promo\",\"imageUrl\":\"https://cdn.example.com/b.jpg\",\"sortOrder\":0,\"active\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Promo"));
    }

    @Test
    void updateBanner_returnsUpdatedDto() throws Exception {
        when(bannerRepository.existsById(4L)).thenReturn(true);
        BannerDTO dto = bannerDto(4L, "Updated Hero");
        when(bannerService.update(any(BannerDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                put("/internal/v1/admin/banners/4")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Updated Hero\",\"imageUrl\":\"https://cdn.example.com/b.jpg\",\"sortOrder\":0,\"active\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Updated Hero"));
    }

    @Test
    void updateHomeSection_returnsUpdatedDto() throws Exception {
        when(homeSectionRepository.existsById(5L)).thenReturn(true);
        HomeSectionDTO dto = homeSectionDto(5L, "featured");
        when(homeSectionService.update(any(HomeSectionDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                put("/internal/v1/admin/home-sections/5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sectionKey\":\"featured\",\"title\":\"Featured\",\"enabled\":true,\"sortOrder\":1}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sectionKey").value("featured"));
    }

    @Test
    void updatePageVisibility_returnsUpdatedDto() throws Exception {
        when(pageVisibilityRepository.existsById(6L)).thenReturn(true);
        PageVisibilityDTO dto = pageVisibilityDto(6L, "shop");
        dto.setVisible(false);
        when(pageVisibilityService.update(any(PageVisibilityDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                put("/internal/v1/admin/page-visibility/6")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"pageKey\":\"shop\",\"visible\":false}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pageKey").value("shop"));
    }

    @Test
    void updateSiteSettings_withExistingId_updates() throws Exception {
        SiteSettingDTO dto = siteSettingDto(7L, "Mini Updated");
        when(siteSettingService.update(any(SiteSettingDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                put("/internal/v1/admin/site-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":7,\"brandName\":\"Mini Updated\",\"storeDiscountPercent\":20}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.brandName").value("Mini Updated"));

        verify(siteSettingService).update(any(SiteSettingDTO.class));
    }

    @Test
    void updateSiteSettings_withoutIdButExistingRecord_updates() throws Exception {
        SiteSettingDTO dto = siteSettingDto(7L, "Mini Patched");
        when(siteSettingService.update(any(SiteSettingDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                put("/internal/v1/admin/site-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"brandName\":\"Mini Patched\",\"storeDiscountPercent\":20}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.brandName").value("Mini Patched"));

        verify(siteSettingService).update(any(SiteSettingDTO.class));
    }

    @Test
    void createPolicy_returnsSavedDto() throws Exception {
        PolicyDTO dto = policyDto(16L, "terms");
        when(policyService.save(any(PolicyDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                post("/internal/v1/admin/policies")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"slug\":\"terms\",\"title\":\"Terms\",\"body\":\"Legal\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.slug").value("terms"));
    }

    @Test
    void updatePolicy_returnsUpdatedDto() throws Exception {
        when(policyRepository.existsById(8L)).thenReturn(true);
        PolicyDTO dto = policyDto(8L, "privacy-v2");
        when(policyService.update(any(PolicyDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                put("/internal/v1/admin/policies/8")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"slug\":\"privacy-v2\",\"title\":\"Privacy\",\"body\":\"Legal\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.slug").value("privacy-v2"));
    }

    @Test
    void deletePolicy_deletesWhenExists() throws Exception {
        when(policyRepository.existsById(8L)).thenReturn(true);

        mockMvc.perform(delete("/internal/v1/admin/policies/8")).andExpect(status().isOk());

        verify(policyService).delete(8L);
    }

    @Test
    void createCoupon_returnsSavedDto() throws Exception {
        CouponDTO dto = couponDto(17L, "SAVE20");
        when(couponService.save(any(CouponDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                post("/internal/v1/admin/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"SAVE20\",\"discountType\":\"PERCENT\",\"discountValue\":20,\"minOrder\":0,\"active\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("SAVE20"));
    }

    @Test
    void updateCoupon_returnsUpdatedDto() throws Exception {
        when(couponRepository.existsById(9L)).thenReturn(true);
        CouponDTO dto = couponDto(9L, "WELCOME15");
        when(couponService.update(any(CouponDTO.class))).thenReturn(dto);

        mockMvc
            .perform(
                put("/internal/v1/admin/coupons/9")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"WELCOME15\",\"discountType\":\"PERCENT\",\"discountValue\":15,\"minOrder\":0,\"active\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("WELCOME15"));
    }

    @Test
    void deleteProduct_whenMissing_returnsNotFound() throws Exception {
        when(productRepository.existsById(404L)).thenReturn(false);

        mockMvc.perform(delete("/internal/v1/admin/products/404")).andExpect(status().isNotFound());
    }

    private static CategoryDTO categoryDto(Long id, String name) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setSlug(name.toLowerCase());
        dto.setSortOrder(0);
        dto.setActive(true);
        return dto;
    }

    private static ProductDTO productDto(Long id, String name) {
        ProductDTO dto = new ProductDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }

    private static BlogPostDTO blogPostDto(Long id, String title) {
        BlogPostDTO dto = new BlogPostDTO();
        dto.setId(id);
        dto.setTitle(title);
        return dto;
    }

    private static BannerDTO bannerDto(Long id, String title) {
        BannerDTO dto = new BannerDTO();
        dto.setId(id);
        dto.setTitle(title);
        return dto;
    }

    private static HomeSectionDTO homeSectionDto(Long id, String sectionKey) {
        HomeSectionDTO dto = new HomeSectionDTO();
        dto.setId(id);
        dto.setSectionKey(sectionKey);
        return dto;
    }

    private static PageVisibilityDTO pageVisibilityDto(Long id, String pageKey) {
        PageVisibilityDTO dto = new PageVisibilityDTO();
        dto.setId(id);
        dto.setPageKey(pageKey);
        return dto;
    }

    private static SiteSettingDTO siteSettingDto(Long id, String brandName) {
        SiteSettingDTO dto = new SiteSettingDTO();
        dto.setId(id);
        dto.setBrandName(brandName);
        return dto;
    }

    private static PolicyDTO policyDto(Long id, String slug) {
        PolicyDTO dto = new PolicyDTO();
        dto.setId(id);
        dto.setSlug(slug);
        return dto;
    }

    private static CouponDTO couponDto(Long id, String code) {
        CouponDTO dto = new CouponDTO();
        dto.setId(id);
        dto.setCode(code);
        return dto;
    }
}
