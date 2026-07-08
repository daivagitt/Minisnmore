package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.repository.*;
import com.miniandmore.app.service.*;
import com.miniandmore.app.service.dto.*;
import com.miniandmore.app.service.mapper.*;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class InternalAdminStoreResource {

    private static final Logger LOG = LoggerFactory.getLogger(InternalAdminStoreResource.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final BlogPostRepository blogPostRepository;
    private final BannerRepository bannerRepository;
    private final HomeSectionRepository homeSectionRepository;
    private final PageVisibilityRepository pageVisibilityRepository;
    private final SiteSettingRepository siteSettingRepository;
    private final PolicyRepository policyRepository;
    private final CouponRepository couponRepository;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final BlogPostService blogPostService;
    private final BannerService bannerService;
    private final HomeSectionService homeSectionService;
    private final PageVisibilityService pageVisibilityService;
    private final SiteSettingService siteSettingService;
    private final PolicyService policyService;
    private final CouponService couponService;
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final BlogPostMapper blogPostMapper;
    private final BannerMapper bannerMapper;
    private final HomeSectionMapper homeSectionMapper;
    private final PageVisibilityMapper pageVisibilityMapper;
    private final SiteSettingMapper siteSettingMapper;
    private final PolicyMapper policyMapper;
    private final CouponMapper couponMapper;
    private final LedgerlySyncService ledgerlySyncService;

    public InternalAdminStoreResource(
        CategoryRepository categoryRepository,
        ProductRepository productRepository,
        BlogPostRepository blogPostRepository,
        BannerRepository bannerRepository,
        HomeSectionRepository homeSectionRepository,
        PageVisibilityRepository pageVisibilityRepository,
        SiteSettingRepository siteSettingRepository,
        PolicyRepository policyRepository,
        CouponRepository couponRepository,
        CategoryService categoryService,
        ProductService productService,
        BlogPostService blogPostService,
        BannerService bannerService,
        HomeSectionService homeSectionService,
        PageVisibilityService pageVisibilityService,
        SiteSettingService siteSettingService,
        PolicyService policyService,
        CouponService couponService,
        CategoryMapper categoryMapper,
        ProductMapper productMapper,
        BlogPostMapper blogPostMapper,
        BannerMapper bannerMapper,
        HomeSectionMapper homeSectionMapper,
        PageVisibilityMapper pageVisibilityMapper,
        SiteSettingMapper siteSettingMapper,
        PolicyMapper policyMapper,
        CouponMapper couponMapper,
        LedgerlySyncService ledgerlySyncService
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.blogPostRepository = blogPostRepository;
        this.bannerRepository = bannerRepository;
        this.homeSectionRepository = homeSectionRepository;
        this.pageVisibilityRepository = pageVisibilityRepository;
        this.siteSettingRepository = siteSettingRepository;
        this.policyRepository = policyRepository;
        this.couponRepository = couponRepository;
        this.categoryService = categoryService;
        this.productService = productService;
        this.blogPostService = blogPostService;
        this.bannerService = bannerService;
        this.homeSectionService = homeSectionService;
        this.pageVisibilityService = pageVisibilityService;
        this.siteSettingService = siteSettingService;
        this.policyService = policyService;
        this.couponService = couponService;
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.blogPostMapper = blogPostMapper;
        this.bannerMapper = bannerMapper;
        this.homeSectionMapper = homeSectionMapper;
        this.pageVisibilityMapper = pageVisibilityMapper;
        this.siteSettingMapper = siteSettingMapper;
        this.policyMapper = policyMapper;
        this.couponMapper = couponMapper;
        this.ledgerlySyncService = ledgerlySyncService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("categories", categoryRepository.count());
        long productTotal = productRepository.count();
        long activeProducts = productRepository.countByActiveTrue();
        stats.put("products", productTotal);
        stats.put("activeProducts", activeProducts);
        stats.put("inactiveProducts", Math.max(0, productTotal - activeProducts));
        stats.put("productsWithSku", productRepository.countWithSku());
        stats.put("coupons", couponRepository.count());
        stats.put("blogPosts", blogPostRepository.count());
        long pageTotal = pageVisibilityRepository.count();
        long visiblePages = pageVisibilityRepository
            .findAllByOrderByPageKeyAsc()
            .stream()
            .filter(page -> Boolean.TRUE.equals(page.getVisible()))
            .count();
        stats.put("pageVisibility", pageTotal);
        stats.put("visiblePages", visiblePages);
        stats.put("banners", bannerRepository.count());
        stats.put("policies", policyRepository.count());
        return ApiResponse.ok(stats);
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryDTO>> categories() {
        return ApiResponse.ok(categoryRepository.findAllByOrderBySortOrderAsc().stream().map(categoryMapper::toDto).toList());
    }

    @PostMapping("/categories")
    public ApiResponse<CategoryDTO> createCategory(@Valid @RequestBody CategoryDTO dto) {
        return ApiResponse.ok(categoryService.save(dto));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<CategoryDTO> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryDTO dto) {
        requireExists(id, categoryRepository::existsById, "Category");
        dto.setId(id);
        return ApiResponse.ok(categoryService.update(dto));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Map<String, Boolean>> deleteCategory(@PathVariable Long id) {
        requireExists(id, categoryRepository::existsById, "Category");
        if (productRepository.countByCategory_Id(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category has products");
        }
        categoryService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @GetMapping("/products")
    public ApiResponse<List<ProductDTO>> products() {
        return ApiResponse.ok(productRepository.findAllWithCategoryOrderBySortOrder().stream().map(productMapper::toDto).toList());
    }

    @PostMapping("/products")
    public ApiResponse<ProductDTO> createProduct(@Valid @RequestBody ProductDTO dto) {
        ProductDTO saved = productService.save(dto);
        scheduleLedgerlyProductSync(saved.getId());
        return ApiResponse.ok(saved);
    }

    @PutMapping("/products/{id}")
    public ApiResponse<ProductDTO> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDTO dto) {
        requireExists(id, productRepository::existsById, "Product");
        dto.setId(id);
        ProductDTO saved = productService.update(dto);
        scheduleLedgerlyProductSync(saved.getId());
        return ApiResponse.ok(saved);
    }

    @DeleteMapping("/products/{id}")
    public ApiResponse<Map<String, Boolean>> deleteProduct(@PathVariable Long id) {
        requireExists(id, productRepository::existsById, "Product");
        ledgerlySyncService.notifyLedgerlyBeforeStoreDelete(id);
        productService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @GetMapping("/blog-posts")
    public ApiResponse<List<BlogPostDTO>> blogPosts() {
        return ApiResponse.ok(blogPostRepository.findAllByOrderByIdDesc().stream().map(blogPostMapper::toDto).toList());
    }

    @PostMapping("/blog-posts")
    public ApiResponse<BlogPostDTO> createBlogPost(@Valid @RequestBody BlogPostDTO dto) {
        return ApiResponse.ok(blogPostService.save(dto));
    }

    @PutMapping("/blog-posts/{id}")
    public ApiResponse<BlogPostDTO> updateBlogPost(@PathVariable Long id, @Valid @RequestBody BlogPostDTO dto) {
        requireExists(id, blogPostRepository::existsById, "Blog post");
        dto.setId(id);
        return ApiResponse.ok(blogPostService.update(dto));
    }

    @DeleteMapping("/blog-posts/{id}")
    public ApiResponse<Map<String, Boolean>> deleteBlogPost(@PathVariable Long id) {
        requireExists(id, blogPostRepository::existsById, "Blog post");
        blogPostService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @GetMapping("/banners")
    public ApiResponse<List<BannerDTO>> banners() {
        return ApiResponse.ok(bannerRepository.findAllByOrderBySortOrderAsc().stream().map(bannerMapper::toDto).toList());
    }

    @PostMapping("/banners")
    public ApiResponse<BannerDTO> createBanner(@Valid @RequestBody BannerDTO dto) {
        return ApiResponse.ok(bannerService.save(dto));
    }

    @PutMapping("/banners/{id}")
    public ApiResponse<BannerDTO> updateBanner(@PathVariable Long id, @Valid @RequestBody BannerDTO dto) {
        requireExists(id, bannerRepository::existsById, "Banner");
        dto.setId(id);
        return ApiResponse.ok(bannerService.update(dto));
    }

    @DeleteMapping("/banners/{id}")
    public ApiResponse<Map<String, Boolean>> deleteBanner(@PathVariable Long id) {
        requireExists(id, bannerRepository::existsById, "Banner");
        bannerService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @GetMapping("/home-sections")
    public ApiResponse<List<HomeSectionDTO>> homeSections() {
        return ApiResponse.ok(homeSectionRepository.findAllByOrderBySortOrderAsc().stream().map(homeSectionMapper::toDto).toList());
    }

    @PutMapping("/home-sections/{id}")
    public ApiResponse<HomeSectionDTO> updateHomeSection(@PathVariable Long id, @Valid @RequestBody HomeSectionDTO dto) {
        requireExists(id, homeSectionRepository::existsById, "Home section");
        dto.setId(id);
        return ApiResponse.ok(homeSectionService.update(dto));
    }

    @GetMapping("/page-visibility")
    public ApiResponse<List<PageVisibilityDTO>> pageVisibility() {
        return ApiResponse.ok(pageVisibilityRepository.findAllByOrderByPageKeyAsc().stream().map(pageVisibilityMapper::toDto).toList());
    }

    @PutMapping("/page-visibility/{id}")
    public ApiResponse<PageVisibilityDTO> updatePageVisibility(@PathVariable Long id, @Valid @RequestBody PageVisibilityDTO dto) {
        requireExists(id, pageVisibilityRepository::existsById, "Page visibility");
        dto.setId(id);
        return ApiResponse.ok(pageVisibilityService.update(dto));
    }

    @GetMapping("/site-settings")
    public ApiResponse<SiteSettingDTO> siteSettings() {
        return ApiResponse.ok(siteSettingRepository.findFirstByOrderByIdAsc().map(siteSettingMapper::toDto).orElse(null));
    }

    @PutMapping("/site-settings")
    public ApiResponse<SiteSettingDTO> updateSiteSettings(@Valid @RequestBody SiteSettingDTO dto) {
        if (dto.getId() == null) {
            siteSettingRepository.findFirstByOrderByIdAsc().ifPresent(existing -> dto.setId(existing.getId()));
        }
        if (dto.getId() == null) {
            return ApiResponse.ok(siteSettingService.save(dto));
        }
        return ApiResponse.ok(siteSettingService.update(dto));
    }

    @GetMapping("/policies")
    public ApiResponse<List<PolicyDTO>> policies() {
        return ApiResponse.ok(policyRepository.findAllByOrderBySlugAsc().stream().map(policyMapper::toDto).toList());
    }

    @PostMapping("/policies")
    public ApiResponse<PolicyDTO> createPolicy(@Valid @RequestBody PolicyDTO dto) {
        return ApiResponse.ok(policyService.save(dto));
    }

    @PutMapping("/policies/{id}")
    public ApiResponse<PolicyDTO> updatePolicy(@PathVariable Long id, @Valid @RequestBody PolicyDTO dto) {
        requireExists(id, policyRepository::existsById, "Policy");
        dto.setId(id);
        return ApiResponse.ok(policyService.update(dto));
    }

    @DeleteMapping("/policies/{id}")
    public ApiResponse<Map<String, Boolean>> deletePolicy(@PathVariable Long id) {
        requireExists(id, policyRepository::existsById, "Policy");
        policyService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @GetMapping("/coupons")
    public ApiResponse<List<CouponDTO>> coupons() {
        return ApiResponse.ok(couponRepository.findAllByOrderByIdDesc().stream().map(couponMapper::toDto).toList());
    }

    @PostMapping("/coupons")
    public ApiResponse<CouponDTO> createCoupon(@Valid @RequestBody CouponDTO dto) {
        return ApiResponse.ok(couponService.save(dto));
    }

    @PutMapping("/coupons/{id}")
    public ApiResponse<CouponDTO> updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponDTO dto) {
        requireExists(id, couponRepository::existsById, "Coupon");
        dto.setId(id);
        return ApiResponse.ok(couponService.update(dto));
    }

    @DeleteMapping("/coupons/{id}")
    public ApiResponse<Map<String, Boolean>> deleteCoupon(@PathVariable Long id) {
        requireExists(id, couponRepository::existsById, "Coupon");
        couponService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    private void requireExists(Long id, java.util.function.Predicate<Long> exists, String label) {
        if (id == null || !exists.test(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found");
        }
    }

    private void scheduleLedgerlyProductSync(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            ledgerlySyncService.syncProductAsync(productId);
        } catch (RuntimeException e) {
            LOG.warn("Could not schedule Ledgerly product sync for product {}", productId, e);
        }
    }
}
