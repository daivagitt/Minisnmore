package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.repository.*;
import com.miniandmore.app.service.PincodeLookupService;
import com.miniandmore.app.service.ProductVariantSupport;
import com.miniandmore.app.service.dto.*;
import com.miniandmore.app.service.mapper.*;
import com.miniandmore.app.web.rest.v1.AboutPageDTO;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import com.miniandmore.app.web.rest.v1.SubscribePageDTO;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1")
public class InternalStorefrontResource {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final BlogPostRepository blogPostRepository;
    private final BannerRepository bannerRepository;
    private final HomeSectionRepository homeSectionRepository;
    private final PageVisibilityRepository pageVisibilityRepository;
    private final SiteSettingRepository siteSettingRepository;
    private final PolicyRepository policyRepository;
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final BlogPostMapper blogPostMapper;
    private final BannerMapper bannerMapper;
    private final HomeSectionMapper homeSectionMapper;
    private final PageVisibilityMapper pageVisibilityMapper;
    private final SiteSettingMapper siteSettingMapper;
    private final PolicyMapper policyMapper;
    private final PincodeLookupService pincodeLookupService;
    private final ProductVariantSupport productVariantSupport;

    public InternalStorefrontResource(
        CategoryRepository categoryRepository,
        ProductRepository productRepository,
        BlogPostRepository blogPostRepository,
        BannerRepository bannerRepository,
        HomeSectionRepository homeSectionRepository,
        PageVisibilityRepository pageVisibilityRepository,
        SiteSettingRepository siteSettingRepository,
        PolicyRepository policyRepository,
        CategoryMapper categoryMapper,
        ProductMapper productMapper,
        BlogPostMapper blogPostMapper,
        BannerMapper bannerMapper,
        HomeSectionMapper homeSectionMapper,
        PageVisibilityMapper pageVisibilityMapper,
        SiteSettingMapper siteSettingMapper,
        PolicyMapper policyMapper,
        PincodeLookupService pincodeLookupService,
        ProductVariantSupport productVariantSupport
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.blogPostRepository = blogPostRepository;
        this.bannerRepository = bannerRepository;
        this.homeSectionRepository = homeSectionRepository;
        this.pageVisibilityRepository = pageVisibilityRepository;
        this.siteSettingRepository = siteSettingRepository;
        this.policyRepository = policyRepository;
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.blogPostMapper = blogPostMapper;
        this.bannerMapper = bannerMapper;
        this.homeSectionMapper = homeSectionMapper;
        this.pageVisibilityMapper = pageVisibilityMapper;
        this.siteSettingMapper = siteSettingMapper;
        this.policyMapper = policyMapper;
        this.pincodeLookupService = pincodeLookupService;
        this.productVariantSupport = productVariantSupport;
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryDTO>> categories() {
        return ApiResponse.ok(categoryRepository.findAllByActiveTrueOrderBySortOrderAsc().stream().map(categoryMapper::toDto).toList());
    }

    @GetMapping("/products")
    public ApiResponse<List<ProductDTO>> products() {
        return ApiResponse.ok(
            productRepository
                .findAllActiveWithCategoryOrderBySortOrder()
                .stream()
                .map(productMapper::toDto)
                .peek(productVariantSupport::applyRuntimePricing)
                .toList()
        );
    }

    @GetMapping("/products/{slug}")
    public ApiResponse<ProductDTO> product(@PathVariable String slug) {
        return ApiResponse.ok(
            productRepository
                .findOneActiveBySlugWithCategory(slug)
                .map(productMapper::toDto)
                .map(dto -> {
                    productVariantSupport.applyRuntimePricing(dto);
                    return dto;
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"))
        );
    }

    @GetMapping("/blog-posts")
    public ApiResponse<List<BlogPostDTO>> blogPosts() {
        return ApiResponse.ok(blogPostRepository.findAllByPublishedTrueOrderByPublishedAtDesc().stream().map(blogPostMapper::toDto).toList());
    }

    @GetMapping("/blog-posts/{slug}")
    public ApiResponse<BlogPostDTO> blogPost(@PathVariable String slug) {
        return ApiResponse.ok(
            blogPostRepository
                .findOneBySlugAndPublishedTrue(slug)
                .map(blogPostMapper::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog post not found"))
        );
    }

    @GetMapping("/banners")
    public ApiResponse<List<BannerDTO>> banners() {
        return ApiResponse.ok(bannerRepository.findAllByActiveTrueOrderBySortOrderAsc().stream().map(bannerMapper::toDto).toList());
    }

    @GetMapping("/home-sections")
    public ApiResponse<List<HomeSectionDTO>> homeSections() {
        return ApiResponse.ok(homeSectionRepository.findAllByEnabledTrueOrderBySortOrderAsc().stream().map(homeSectionMapper::toDto).toList());
    }

    @GetMapping("/page-visibility")
    public ApiResponse<List<PageVisibilityDTO>> pageVisibility() {
        return ApiResponse.ok(pageVisibilityRepository.findAllByOrderByPageKeyAsc().stream().map(pageVisibilityMapper::toDto).toList());
    }

    @GetMapping("/site-settings")
    public ApiResponse<SiteSettingDTO> siteSettings() {
        return ApiResponse.ok(siteSettingRepository.findFirstByOrderByIdAsc().map(siteSettingMapper::toDto).orElse(null));
    }

    @GetMapping("/subscribe-page")
    public ApiResponse<SubscribePageDTO> subscribePage() {
        return ApiResponse.ok(
            siteSettingRepository
                .findFirstByOrderByIdAsc()
                .map(siteSettingMapper::toDto)
                .map(SubscribePageDTO::from)
                .orElseGet(SubscribePageDTO::new)
        );
    }

    @GetMapping("/about-page")
    public ApiResponse<AboutPageDTO> aboutPage() {
        return ApiResponse.ok(
            siteSettingRepository
                .findFirstByOrderByIdAsc()
                .map(siteSettingMapper::toDto)
                .map(AboutPageDTO::from)
                .orElseGet(AboutPageDTO::new)
        );
    }

    @GetMapping("/pincode/{pincode}")
    public ApiResponse<PincodeLookupDTO> pincode(@PathVariable String pincode) {
        if (pincode == null || !pincode.matches("^[1-9]\\d{5}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pincode format");
        }
        return ApiResponse.ok(pincodeLookupService.lookup(pincode).orElse(null));
    }

    @GetMapping("/policies/{slug}")
    public ApiResponse<PolicyDTO> policy(@PathVariable String slug) {
        return ApiResponse.ok(
            policyRepository
                .findOneBySlug(slug)
                .map(policyMapper::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"))
        );
    }
}
