package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.*;
import com.miniandmore.app.repository.*;
import com.miniandmore.app.service.criteria.*;
import com.miniandmore.app.service.dto.*;
import com.miniandmore.app.service.mapper.*;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import tech.jhipster.service.filter.BooleanFilter;
import tech.jhipster.service.filter.LongFilter;
import tech.jhipster.service.filter.StringFilter;

@ExtendWith(MockitoExtension.class)
class QueryServiceUnitTest {

    @Test
    void queryServiceSuiteLoads() {
        assertThat(true).isTrue();
    }

    @Nested
    class BannerQueryServiceTests {

        @Mock
        private BannerRepository bannerRepository;

        @Mock
        private BannerMapper bannerMapper;

        @InjectMocks
        private BannerQueryService bannerQueryService;

        @Test
        void countByCriteria_delegatesToRepository() {
            when(bannerRepository.count(any(Specification.class))).thenReturn(4L);
            assertThat(bannerQueryService.countByCriteria(null)).isEqualTo(4L);
        }

        @Test
        void findByCriteria_mapsEntitiesToDtos() {
            Banner entity = new Banner().id(1L);
            BannerDTO dto = new BannerDTO();
            dto.setId(1L);
            when(bannerRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(bannerMapper.toDto(entity)).thenReturn(dto);

            Page<BannerDTO> page = bannerQueryService.findByCriteria(new BannerCriteria(), PageRequest.of(0, 10));

            assertThat(page.getContent()).containsExactly(dto);
        }

        @Test
        void countByCriteria_withDistinctAndFilters() {
            BannerCriteria criteria = new BannerCriteria();
            criteria.setDistinct(true);
            BooleanFilter active = new BooleanFilter();
            active.setEquals(true);
            criteria.setActive(active);
            StringFilter title = new StringFilter();
            title.setContains("sale");
            criteria.setTitle(title);
            when(bannerRepository.count(any(Specification.class))).thenReturn(2L);

            assertThat(bannerQueryService.countByCriteria(criteria)).isEqualTo(2L);
            verify(bannerRepository).count(any(Specification.class));
        }
    }

    @Nested
    class BlogPostQueryServiceTests {

        @Mock
        private BlogPostRepository blogPostRepository;

        @Mock
        private BlogPostMapper blogPostMapper;

        @InjectMocks
        private BlogPostQueryService blogPostQueryService;

        @Test
        void countAndFindByCriteria() {
            BlogPost entity = new BlogPost().id(1L);
            BlogPostDTO dto = new BlogPostDTO();
            when(blogPostRepository.count(any(Specification.class))).thenReturn(1L);
            when(blogPostRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(blogPostMapper.toDto(entity)).thenReturn(dto);

            BlogPostCriteria criteria = new BlogPostCriteria();
            criteria.setDistinct(true);
            StringFilter slug = new StringFilter();
            slug.setEquals("post");
            criteria.setSlug(slug);

            assertThat(blogPostQueryService.countByCriteria(criteria)).isEqualTo(1L);
            assertThat(blogPostQueryService.findByCriteria(criteria, PageRequest.of(0, 5)).getContent()).containsExactly(dto);
        }
    }

    @Nested
    class CategoryQueryServiceTests {

        @Mock
        private CategoryRepository categoryRepository;

        @Mock
        private CategoryMapper categoryMapper;

        @InjectMocks
        private CategoryQueryService categoryQueryService;

        @Test
        void countAndFindByCriteria() {
            Category entity = new Category().id(1L);
            CategoryDTO dto = new CategoryDTO();
            when(categoryRepository.count(any(Specification.class))).thenReturn(3L);
            when(categoryRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(categoryMapper.toDto(entity)).thenReturn(dto);

            CategoryCriteria criteria = new CategoryCriteria();
            LongFilter id = new LongFilter();
            id.setGreaterThan(0L);
            criteria.setId(id);

            assertThat(categoryQueryService.countByCriteria(criteria)).isEqualTo(3L);
            assertThat(categoryQueryService.findByCriteria(criteria, PageRequest.of(0, 10)).getContent()).containsExactly(dto);
        }
    }

    @Nested
    class CouponQueryServiceTests {

        @Mock
        private CouponRepository couponRepository;

        @Mock
        private CouponMapper couponMapper;

        @InjectMocks
        private CouponQueryService couponQueryService;

        @Test
        void countAndFindByCriteria() {
            Coupon entity = new Coupon().id(1L);
            CouponDTO dto = new CouponDTO();
            when(couponRepository.count(any(Specification.class))).thenReturn(2L);
            when(couponRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(couponMapper.toDto(entity)).thenReturn(dto);

            CouponCriteria criteria = new CouponCriteria();
            StringFilter code = new StringFilter();
            code.setContains("SAVE");
            criteria.setCode(code);

            assertThat(couponQueryService.countByCriteria(criteria)).isEqualTo(2L);
            assertThat(couponQueryService.findByCriteria(criteria, PageRequest.of(0, 10)).getContent()).containsExactly(dto);
        }
    }

    @Nested
    class CustomerOrderQueryServiceTests {

        @Mock
        private CustomerOrderRepository customerOrderRepository;

        @Mock
        private CustomerOrderMapper customerOrderMapper;

        @InjectMocks
        private CustomerOrderQueryService customerOrderQueryService;

        @Test
        void countAndFindByCriteria() {
            CustomerOrder entity = new CustomerOrder().id(1L);
            CustomerOrderDTO dto = new CustomerOrderDTO();
            when(customerOrderRepository.count(any(Specification.class))).thenReturn(5L);
            when(customerOrderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(customerOrderMapper.toDto(entity)).thenReturn(dto);

            CustomerOrderCriteria criteria = new CustomerOrderCriteria();
            criteria.setDistinct(true);
            StringFilter orderNumber = new StringFilter();
            orderNumber.setEquals("MNM-001");
            criteria.setOrderNumber(orderNumber);

            assertThat(customerOrderQueryService.countByCriteria(criteria)).isEqualTo(5L);
            assertThat(customerOrderQueryService.findByCriteria(criteria, PageRequest.of(0, 10)).getContent()).containsExactly(dto);
        }
    }

    @Nested
    class CustomerProfileQueryServiceTests {

        @Mock
        private CustomerProfileRepository customerProfileRepository;

        @Mock
        private CustomerProfileMapper customerProfileMapper;

        @InjectMocks
        private CustomerProfileQueryService customerProfileQueryService;

        @Test
        void countAndFindByCriteria() {
            CustomerProfile entity = new CustomerProfile().id(1L);
            CustomerProfileDTO dto = new CustomerProfileDTO();
            when(customerProfileRepository.count(any(Specification.class))).thenReturn(1L);
            when(customerProfileRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(customerProfileMapper.toDto(entity)).thenReturn(dto);

            assertThat(customerProfileQueryService.countByCriteria(new CustomerProfileCriteria())).isEqualTo(1L);
            assertThat(
                customerProfileQueryService.findByCriteria(new CustomerProfileCriteria(), PageRequest.of(0, 10)).getContent()
            ).containsExactly(dto);
        }
    }

    @Nested
    class HomeSectionQueryServiceTests {

        @Mock
        private HomeSectionRepository homeSectionRepository;

        @Mock
        private HomeSectionMapper homeSectionMapper;

        @InjectMocks
        private HomeSectionQueryService homeSectionQueryService;

        @Test
        void countAndFindByCriteria() {
            HomeSection entity = new HomeSection().id(1L);
            HomeSectionDTO dto = new HomeSectionDTO();
            when(homeSectionRepository.count(any(Specification.class))).thenReturn(2L);
            when(homeSectionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(homeSectionMapper.toDto(entity)).thenReturn(dto);

            HomeSectionCriteria criteria = new HomeSectionCriteria();
            BooleanFilter enabled = new BooleanFilter();
            enabled.setEquals(true);
            criteria.setEnabled(enabled);

            assertThat(homeSectionQueryService.countByCriteria(criteria)).isEqualTo(2L);
            assertThat(homeSectionQueryService.findByCriteria(criteria, PageRequest.of(0, 10)).getContent()).containsExactly(dto);
        }
    }

    @Nested
    class OrderItemQueryServiceTests {

        @Mock
        private OrderItemRepository orderItemRepository;

        @Mock
        private OrderItemMapper orderItemMapper;

        @InjectMocks
        private OrderItemQueryService orderItemQueryService;

        @Test
        void countAndFindByCriteria() {
            OrderItem entity = new OrderItem().id(1L);
            OrderItemDTO dto = new OrderItemDTO();
            when(orderItemRepository.count(any(Specification.class))).thenReturn(7L);
            when(orderItemRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(orderItemMapper.toDto(entity)).thenReturn(dto);

            OrderItemCriteria criteria = new OrderItemCriteria();
            StringFilter productName = new StringFilter();
            productName.setContains("shirt");
            criteria.setProductName(productName);

            assertThat(orderItemQueryService.countByCriteria(criteria)).isEqualTo(7L);
            assertThat(orderItemQueryService.findByCriteria(criteria, PageRequest.of(0, 10)).getContent()).containsExactly(dto);
        }
    }

    @Nested
    class PageVisibilityQueryServiceTests {

        @Mock
        private PageVisibilityRepository pageVisibilityRepository;

        @Mock
        private PageVisibilityMapper pageVisibilityMapper;

        @InjectMocks
        private PageVisibilityQueryService pageVisibilityQueryService;

        @Test
        void countAndFindByCriteria() {
            PageVisibility entity = new PageVisibility().id(1L);
            PageVisibilityDTO dto = new PageVisibilityDTO();
            when(pageVisibilityRepository.count(any(Specification.class))).thenReturn(1L);
            when(pageVisibilityRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(pageVisibilityMapper.toDto(entity)).thenReturn(dto);

            assertThat(pageVisibilityQueryService.countByCriteria(new PageVisibilityCriteria())).isEqualTo(1L);
            assertThat(
                pageVisibilityQueryService.findByCriteria(new PageVisibilityCriteria(), PageRequest.of(0, 10)).getContent()
            ).containsExactly(dto);
        }
    }

    @Nested
    class PolicyQueryServiceTests {

        @Mock
        private PolicyRepository policyRepository;

        @Mock
        private PolicyMapper policyMapper;

        @InjectMocks
        private PolicyQueryService policyQueryService;

        @Test
        void countAndFindByCriteria() {
            Policy entity = new Policy().id(1L);
            PolicyDTO dto = new PolicyDTO();
            when(policyRepository.count(any(Specification.class))).thenReturn(1L);
            when(policyRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(policyMapper.toDto(entity)).thenReturn(dto);

            PolicyCriteria criteria = new PolicyCriteria();
            StringFilter slug = new StringFilter();
            slug.setEquals("privacy");
            criteria.setSlug(slug);

            assertThat(policyQueryService.countByCriteria(criteria)).isEqualTo(1L);
            assertThat(policyQueryService.findByCriteria(criteria, PageRequest.of(0, 10)).getContent()).containsExactly(dto);
        }
    }

    @Nested
    class ProductQueryServiceTests {

        @Mock
        private ProductRepository productRepository;

        @Mock
        private ProductMapper productMapper;

        @InjectMocks
        private ProductQueryService productQueryService;

        @Test
        void countAndFindByCriteria() {
            Product entity = new Product().id(1L);
            ProductDTO dto = new ProductDTO();
            when(productRepository.count(any(Specification.class))).thenReturn(10L);
            when(productRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(productMapper.toDto(entity)).thenReturn(dto);

            ProductCriteria criteria = new ProductCriteria();
            criteria.setDistinct(true);
            BooleanFilter active = new BooleanFilter();
            active.setEquals(true);
            criteria.setActive(active);
            StringFilter name = new StringFilter();
            name.setContains("dress");
            criteria.setName(name);

            assertThat(productQueryService.countByCriteria(criteria)).isEqualTo(10L);
            assertThat(productQueryService.findByCriteria(criteria, PageRequest.of(0, 10)).getContent()).containsExactly(dto);
        }
    }

    @Nested
    class SiteSettingQueryServiceTests {

        @Mock
        private SiteSettingRepository siteSettingRepository;

        @Mock
        private SiteSettingMapper siteSettingMapper;

        @InjectMocks
        private SiteSettingQueryService siteSettingQueryService;

        @Test
        void countAndFindByCriteria() {
            SiteSetting entity = new SiteSetting().id(1L);
            SiteSettingDTO dto = new SiteSettingDTO();
            when(siteSettingRepository.count(any(Specification.class))).thenReturn(1L);
            when(siteSettingRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));
            when(siteSettingMapper.toDto(entity)).thenReturn(dto);

            assertThat(siteSettingQueryService.countByCriteria(new SiteSettingCriteria())).isEqualTo(1L);
            assertThat(
                siteSettingQueryService.findByCriteria(new SiteSettingCriteria(), PageRequest.of(0, 10)).getContent()
            ).containsExactly(dto);
        }
    }
}
