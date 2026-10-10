package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Line coverage for ZohoSyncService private catalog/order merge helpers. */
@ExtendWith(MockitoExtension.class)
class ZohoSyncServicePrivateHelpersCoverageTest {

    @Mock
    private ZohoBooksClient booksClient;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private ZohoItemImageSync itemImageSync;

    @Mock
    private ZohoProductFieldMapper productFieldMapper;

    @Mock
    private ProductDescriptionCodec descriptionCodec;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ZohoSettingRepository zohoSettingRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private OrderEventService orderEventService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProductVariantSupport variantSupport;
    private ZohoSyncService service;

    @BeforeEach
    void setUp() {
        variantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        ZohoBooksAccountResolver accountResolver = new ZohoBooksAccountResolver(booksClient, applicationProperties);
        ZohoCatalogStockAdjuster stockAdjuster = new ZohoCatalogStockAdjuster(booksClient, accountResolver);
        ZohoStockReconcileService stockReconcileService =
            new ZohoStockReconcileService(
                productRepository,
                orderItemRepository,
                variantSupport,
                accountResolver,
                stockAdjuster
            );
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                variantSupport,
                productRepository,
                zohoSettingRepository,
                customerOrderRepository,
                customerProfileRepository,
                orderItemRepository,
                s3StorageService,
                orderEventService,
                accountResolver,
                stockAdjuster,
                stockReconcileService
            );
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        lenient().when(applicationProperties.getZoho()).thenReturn(zoho);
        lenient().when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());
    }

    @Test
    void hasExtraVariantFields_detectsAttributeName2AndOptionFields() {
        Map<String, Object> byName2 = Map.of("attribute_name2", "Color");
        Map<String, Object> byOption = Map.of("attribute_option_name2", "Red");
        Boolean hasName2 = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "hasExtraVariantFields", byName2);
        Boolean hasOption2 = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "hasExtraVariantFields", byOption);
        assertThat(hasName2).isTrue();
        assertThat(hasOption2).isTrue();
    }

    @Test
    void mergeJsonUrlArrays_mergesDedupesAndHandlesEmpty() {
        String merged = ReflectionTestUtils.invokeMethod(
            service,
            "mergeJsonUrlArrays",
            "[\"https://cdn.example.com/a.jpg\"]",
            "[\"https://cdn.example.com/a.jpg\",\"https://cdn.example.com/b.jpg\"]"
        );
        assertThat(merged).contains("a.jpg").contains("b.jpg");
        String emptyBoth = ReflectionTestUtils.invokeMethod(service, "mergeJsonUrlArrays", (Object) null, (Object) null);
        assertThat(emptyBoth).isNull();
    }

    @Test
    void parseJsonStringArray_skipsInvalidAndNonTextualNodes() {
        @SuppressWarnings("unchecked")
        List<String> parsed = ReflectionTestUtils.invokeMethod(
            service,
            "parseJsonStringArray",
            "[123,\" https://cdn.example.com/x.jpg \",\"\"]"
        );
        assertThat(parsed).containsExactly("https://cdn.example.com/x.jpg");
        @SuppressWarnings("unchecked")
        List<String> notArray = ReflectionTestUtils.invokeMethod(service, "parseJsonStringArray", "{\"url\":\"x\"}");
        assertThat(notArray).isEmpty();
    }

    @Test
    void mergeColorImageMaps_mergesUrlsForSameColorKey() {
        String first = "{\"Navy\":[\"https://cdn.example.com/n1.jpg\"]}";
        String second = "{\"Navy\":[\"https://cdn.example.com/n2.jpg\"],\"Red\":[\"https://cdn.example.com/r.jpg\"]}";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorImageMaps", first, second);
        assertThat(merged).contains("n1.jpg").contains("n2.jpg").contains("r.jpg");
    }

    @Test
    void parseJsonStringListMap_skipsBlankKeysAndNonArrays() {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> parsed = ReflectionTestUtils.invokeMethod(
            service,
            "parseJsonStringListMap",
            "{\"  \":[\"skip\"],\"Blue\":[\"https://cdn.example.com/b.jpg\"],\"Bad\":\"text\"}"
        );
        assertThat(parsed).containsOnlyKeys("Blue");
        assertThat(parsed.get("Blue")).containsExactly("https://cdn.example.com/b.jpg");
    }

    @Test
    void mergeColorsJsonPreferringRich_prefersRichObjectNodes() {
        String parent = "[\"Print 1\",\"Print 2\"]";
        String duplicate =
            "[{\"name\":\"Print 1\",\"imageUrls\":[\"https://cdn.example.com/p1.jpg\"]},{\"name\":\"Print 2\",\"swatchUrl\":\"https://cdn.example.com/s2.jpg\"}]";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorsJsonPreferringRich", parent, duplicate);
        assertThat(merged).contains("p1.jpg").contains("s2.jpg");
    }

    @Test
    void applyPulledDescription_plainText_keepsDescriptionOnProduct() {
        Product product = new Product().sku("PLAIN-DESC");
        when(descriptionCodec.decode("Plain story"))
            .thenReturn(new ProductDescriptionCodec.Decoded(null, null, List.of(), List.of(), null, null, null, null, null, null));
        ReflectionTestUtils.invokeMethod(service, "applyPulledDescription", product, "Plain story");
        assertThat(product.getDescription()).isEqualTo("Plain story");
    }

    @Test
    void applyPulledDescription_metadataOnly_clearsDescriptionField() {
        Product product = new Product().sku("META-DESC");
        ProductDescriptionCodec.Decoded decoded = new ProductDescriptionCodec.Decoded(
            "Category",
            "Cotton",
            List.of("S"),
            List.of("Blue"),
            null,
            null,
            "Story",
            null,
            null,
            null
        );
        when(descriptionCodec.decode("Fabric: Cotton")).thenReturn(decoded);
        ReflectionTestUtils.invokeMethod(service, "applyPulledDescription", product, "Fabric: Cotton");
        assertThat(product.getDescription()).isNull();
    }

    @Test
    void applyPulledDescription_bodyDescription_winsOverMetadata() {
        Product product = new Product().sku("BODY-DESC");
        ProductDescriptionCodec.Decoded decoded = new ProductDescriptionCodec.Decoded(
            null,
            null,
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "Long body copy"
        );
        when(descriptionCodec.decode("raw")).thenReturn(decoded);
        ReflectionTestUtils.invokeMethod(service, "applyPulledDescription", product, "raw");
        assertThat(product.getDescription()).isEqualTo("Long body copy");
    }

    @Test
    void productUnit_andApplyPulledUnit_useDefaultsAndZohoFields() {
        String defaultUnit = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "productUnit", (Object) null);
        assertThat(defaultUnit).isEqualTo("pcs");
        Product withUnit = new Product().units("pair");
        String pairUnit = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "productUnit", withUnit);
        assertThat(pairUnit).isEqualTo("pair");

        Product product = new Product();
        ReflectionTestUtils.invokeMethod(service, "applyPulledUnit", product, Map.of("units", "box"));
        assertThat(product.getUnits()).isEqualTo("box");
    }

    @Test
    void fetchValidatedZohoItemDetail_skuMismatchThrows() {
        when(booksClient.getItem("zoho-1"))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-1", "sku", "OTHER-SKU")));
        Map<String, Object> listRow = Map.of("item_id", "zoho-1", "sku", "LIST-SKU");
        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(service, "fetchValidatedZohoItemDetail", listRow, "zoho-1", "LIST-SKU")
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SKU mismatch");
    }

    @Test
    void createVariantParentFromZohoGroup_reusesExistingParentAndSetsGroupId() {
        Product existing = new Product()
            .id(77L)
            .sku("HORIZON-SET")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[]");
        when(productRepository.findOneByZohoItemId("grp-horizon")).thenReturn(Optional.of(existing));
        List<Product> parents = new ArrayList<>();
        Map<String, Object> zohoItem = Map.of("group_id", "grp-horizon", "group_name", "Horizon Set");
        Product parent = ReflectionTestUtils.invokeMethod(
            service,
            "createVariantParentFromZohoGroup",
            zohoItem,
            "HORIZON-S-NAVY",
            parents
        );
        assertThat(parent.getId()).isEqualTo(77L);
        assertThat(parent.getZohoItemId()).isEqualTo("grp-horizon");
        assertThat(parents).contains(existing);
    }

    @Test
    void attachVariantParent_promotesSimpleProductToVariantMatrix() {
        Product simple = new Product().id(5L).sku("SIMPLE").itemStructure("SIMPLE");
        List<Product> parents = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(service, "attachVariantParent", simple, parents);
        assertThat(simple.getItemStructure()).isEqualTo(ProductVariantSupport.STRUCTURE_VARIANTS);
        assertThat(simple.getVariantsJson()).isEqualTo("[]");
        assertThat(parents).contains(simple);
    }

    @Test
    void sameProduct_comparesByIdOnlyWhenBothPresent() {
        Product a = new Product().id(1L);
        Product b = new Product().id(1L);
        Product c = new Product().id(2L);
        Boolean same = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "sameProduct", a, b);
        Boolean different = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "sameProduct", a, c);
        Boolean nullRight = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "sameProduct", a, null);
        assertThat(same).isTrue();
        assertThat(different).isFalse();
        assertThat(nullRight).isFalse();
    }

    @Test
    void uniqueGroupSku_addsSuffixWhenSkuTaken() {
        when(productRepository.findOneBySku("HORIZON-SET")).thenReturn(Optional.of(new Product().id(99L)));
        when(productRepository.findOneBySku("HORIZON-SET-1")).thenReturn(Optional.empty());
        String sku = ReflectionTestUtils.invokeMethod(service, "uniqueGroupSku", "Horizon Set", "grp-1", "FALLBACK-SKU");
        assertThat(sku).isEqualTo("HORIZON-SET-1");
    }

    @Test
    void deactivateSimpleDuplicate_inactiveDuplicateReturnsWithoutDeactivatingAgain() {
        Product parent = new Product().id(1L).sku("PARENT");
        Product duplicate = new Product().id(2L).sku("CHILD").active(false);
        when(productRepository.findOneBySku("CHILD")).thenReturn(Optional.of(duplicate));
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        Product result = ReflectionTestUtils.invokeMethod(
            service,
            "deactivateSimpleDuplicateForVariantSku",
            parent,
            "CHILD",
            builder
        );
        assertThat(result).isSameAs(duplicate);
        assertThat(duplicate.getActive()).isFalse();
    }

    @Test
    void fetchZohoItemDetailBySkuForSinglePull_missingListedSkuThrows() {
        when(booksClient.findItemBySku("SKU-NO-LIST")).thenReturn(Map.of("item_id", "zoho-x"));
        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(service, "fetchZohoItemDetailBySkuForSinglePull", "SKU-NO-LIST")
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no SKU");
    }

    @Test
    void mergeJsonUrlArrays_whenSerializerFails_returnsFirstArgument() throws Exception {
        ObjectMapper broken = org.mockito.Mockito.spy(objectMapper);
        org.mockito.Mockito.doThrow(new JsonProcessingException("fail") {}).when(broken).writeValueAsString(any());
        ReflectionTestUtils.setField(service, "objectMapper", broken);
        String first = "[\"https://cdn.example.com/a.jpg\"]";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeJsonUrlArrays", first, "[\"https://cdn.example.com/b.jpg\"]");
        assertThat(merged).isEqualTo(first);
    }

    @Test
    void linkInvoiceToOrder_blankInvoiceIdIsNoOp() {
        var order = new com.miniandmore.app.domain.CustomerOrder().id(1L);
        ReflectionTestUtils.invokeMethod(service, "linkInvoiceToOrder", order, "  ");
        assertThat(order.getZohoInvoiceId()).isNull();
    }

    @Test
    void createVariantParentFromZohoGroup_blankDisplayNameUsesFallbackSku() {
        when(productRepository.findOneByZohoItemId(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySku(any())).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(3);
        when(productRepository.save(any())).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            saved.setId(501L);
            return saved;
        });
        lenient()
            .when(descriptionCodec.decode(any()))
            .thenReturn(new ProductDescriptionCodec.Decoded(null, null, List.of(), List.of(), null, null, null, null, null, null));
        Map<String, Object> zohoItem = Map.of("group_id", "grp-fallback");
        List<Product> parents = new ArrayList<>();
        Product created = ReflectionTestUtils.invokeMethod(
            service,
            "createVariantParentFromZohoGroup",
            zohoItem,
            "FALLBACK-VAR-SKU",
            parents
        );
        assertThat(created.getSku()).isEqualTo("FALLBACK-VAR-SKU");
        assertThat(created.getZohoItemId()).isEqualTo("grp-fallback");
        assertThat(created.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fetchZohoItemDetailBySkuForSinglePull_blankSkuReturnsEmpty() {
        Optional<?> empty = ReflectionTestUtils.invokeMethod(service, "fetchZohoItemDetailBySkuForSinglePull", "  ");
        assertThat(empty).isEmpty();
    }

    @Test
    void deactivateSimpleDuplicate_skipsVariantDuplicateAndSameParent() {
        Product parent = new Product().id(1L).sku("PARENT");
        Product variantDuplicate = new Product().id(2L).sku("CHILD").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        when(productRepository.findOneBySku("CHILD")).thenReturn(Optional.of(variantDuplicate));
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        Product skippedVariant = ReflectionTestUtils.invokeMethod(
            service,
            "deactivateSimpleDuplicateForVariantSku",
            parent,
            "CHILD",
            builder
        );
        assertThat(skippedVariant).isNull();

        Product sameParent = new Product().id(1L).sku("CHILD");
        when(productRepository.findOneBySku("CHILD")).thenReturn(Optional.of(sameParent));
        Product skippedSame = ReflectionTestUtils.invokeMethod(
            service,
            "deactivateSimpleDuplicateForVariantSku",
            parent,
            "CHILD",
            builder
        );
        assertThat(skippedSame).isNull();
    }

    @Test
    void uniqueGroupSku_usesPrefixedGroupIdAfterManyCollisions() {
        when(productRepository.findOneBySku(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.of(new Product().id(1L)));
        String sku = ReflectionTestUtils.invokeMethod(service, "uniqueGroupSku", "Busy Name", "grp-99", "FALLBACK");
        assertThat(sku).isEqualTo("G-grp-99");
    }

    @Test
    void mergeColorsJsonPreferringRich_invalidDuplicateJsonReturnsParent() {
        String parent = "[{\"name\":\"Print\",\"imageUrls\":[\"https://cdn.example.com/p.jpg\"]}]";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorsJsonPreferringRich", parent, "{not-array");
        assertThat(merged).contains("p.jpg");
    }

    @Test
    void mergeColorImageMaps_whenSerializerFails_returnsFirst() throws Exception {
        ObjectMapper broken = org.mockito.Mockito.spy(objectMapper);
        org.mockito.Mockito.doThrow(new JsonProcessingException("fail") {}).when(broken).writeValueAsString(any());
        ReflectionTestUtils.setField(service, "objectMapper", broken);
        String first = "{\"Blue\":[\"https://cdn.example.com/b.jpg\"]}";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorImageMaps", first, "{\"Red\":[\"https://cdn.example.com/r.jpg\"]}");
        assertThat(merged).isEqualTo(first);
    }

    @Test
    void parseJsonStringArray_invalidJsonReturnsEmptyList() {
        @SuppressWarnings("unchecked")
        List<String> parsed = ReflectionTestUtils.invokeMethod(service, "parseJsonStringArray", "{bad");
        assertThat(parsed).isEmpty();
    }

    @Test
    void applyPulledUnit_nullProductOrItemIsNoOp() {
        Product product = new Product();
        ReflectionTestUtils.invokeMethod(service, "applyPulledUnit", null, Map.of("unit", "box"));
        ReflectionTestUtils.invokeMethod(service, "applyPulledUnit", product, (Object) null);
        assertThat(product.getUnits()).isNull();
    }

    @Test
    void applySalesOrderAddresses_blankSalesOrderIdIsNoOp() {
        ReflectionTestUtils.invokeMethod(
            service,
            "applySalesOrderAddresses",
            "  ",
            new com.miniandmore.app.domain.CustomerOrder().id(1L)
        );
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.never()).updateSalesOrderBillingAddress(any(), any());
    }

    @Test
    void buildZohoAddress_returnsNullForMissingOrBlankFields() {
        Map<String, Object> empty = ReflectionTestUtils.invokeMethod(
            service,
            "buildZohoAddress",
            objectMapper.createObjectNode()
        );
        assertThat(empty).isNull();
        com.fasterxml.jackson.databind.JsonNode partial = objectMapper.createObjectNode().put("line1", "Line 1");
        Map<String, Object> withLine = ReflectionTestUtils.invokeMethod(service, "buildZohoAddress", partial);
        assertThat(withLine).containsEntry("address", "Line 1");
    }

    @Test
    void findContactByNameIfAllowed_rejectsEmailLikeNames() {
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "findContactByNameIfAllowed", "buyer@example.com");
        assertThat(result).isNull();
    }

    @Test
    void findContactByNameIfAllowed_invalidLookupReturnsNull() {
        when(booksClient.findContactByName("Bad Lookup"))
            .thenThrow(
                new org.springframework.web.client.RestClientResponseException(
                    "bad",
                    400,
                    "Bad Request",
                    null,
                    "{\"message\":\"Invalid value passed for contact_name_contains\"}".getBytes(),
                    null
                )
            );
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "findContactByNameIfAllowed", "Bad Lookup");
        assertThat(result).isNull();
    }

    @Test
    void parseShippingAddress_invalidJsonReturnsEmptyNode() {
        com.fasterxml.jackson.databind.JsonNode node = ReflectionTestUtils.invokeMethod(
            service,
            "parseShippingAddress",
            "{not-json"
        );
        assertThat(node.isObject()).isTrue();
        assertThat(node.size()).isZero();
    }

    @Test
    void resolveZohoContactName_prefersShippingNameOverOrderNumber() {
        com.fasterxml.jackson.databind.JsonNode address = objectMapper
            .createObjectNode()
            .put("full_name", "Jane Customer");
        String name = ReflectionTestUtils.invokeMethod(
            service,
            "resolveZohoContactName",
            address,
            null,
            "buyer@example.com",
            "login",
            "MNM-100"
        );
        assertThat(name).isEqualTo("Jane Customer");
    }

    @Test
    void resolveOrCreateContact_duplicateCreateUsesExistingByName() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder()
            .id(55L)
            .orderNumber("MNM-DUP-CREATE")
            .userLogin("buyer@example.com")
            .shippingAddressJson("{\"full_name\":\"Jane Customer\",\"email\":\"buyer@example.com\",\"phone\":\"9876543210\"}");
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(null);
        when(booksClient.findContactByName("Jane Customer")).thenReturn(null).thenReturn(Map.of("contact_id", "cust-dup-2"));
        when(booksClient.createContact(any()))
            .thenThrow(
                new org.springframework.web.client.RestClientResponseException(
                    "duplicate",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":3062,\"message\":\"already exists\"}".getBytes(),
                    null
                )
            );
        String contactId = ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order);
        assertThat(contactId).isEqualTo("cust-dup-2");
    }

    @Test
    void resolveOrCreateContact_invalidEmailLookupStillCreatesContact() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder()
            .id(56L)
            .orderNumber("MNM-BAD-EMAIL-2")
            .userLogin("not-an-email")
            .shippingAddressJson("{\"full_name\":\"Jane\",\"email\":\"not-an-email\",\"phone\":\"9876543210\"}");
        when(booksClient.findContactByName("Jane")).thenReturn(Map.of("contact_id", "cust-by-name"));
        String contactId = ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order);
        assertThat(contactId).isEqualTo("cust-by-name");
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.never()).findContactByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void uniqueSlug_addsSuffixWhenSlugTaken() {
        when(productRepository.findOneBySlug("horizon-set")).thenReturn(Optional.of(new Product().id(1L)));
        when(productRepository.findOneBySlug("horizon-set-1")).thenReturn(Optional.empty());
        String slug = ReflectionTestUtils.invokeMethod(service, "uniqueSlug", "HORIZON-SET", "Horizon Set");
        assertThat(slug).isEqualTo("horizon-set-1");
    }

    @Test
    void slugify_blankValueUsesItemFallback() {
        String slug = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "slugify", "   ");
        assertThat(slug).isEqualTo("item");
    }

    @Test
    void numericCoercionHelpers_coverNullBlankAndNumberTypes() {
        String asString = ReflectionTestUtils.invokeMethod(service, "stringValue", 42);
        assertThat(asString).isEqualTo("42");
        Boolean blankNumber = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isBlankNumber", "  ");
        assertThat(blankNumber.booleanValue()).isTrue();
        java.math.BigDecimal fromBlank = ReflectionTestUtils.invokeMethod(service, "blankToBigDecimal", "  ");
        assertThat(fromBlank).isNull();
        java.math.BigDecimal fromNumber = ReflectionTestUtils.invokeMethod(service, "toBigDecimal", 12.5d);
        assertThat(fromNumber).isEqualByComparingTo("12.5");
        Integer fromText = ReflectionTestUtils.invokeMethod(service, "toInteger", "7");
        assertThat(fromText).isEqualTo(7);
        Integer negative = ReflectionTestUtils.invokeMethod(service, "toInteger", -3);
        assertThat(negative).isEqualTo(-3);
    }

    @Test
    void isZohoAuthFailure_detects401And403() {
        org.springframework.web.client.RestClientResponseException unauthorized = new org.springframework.web.client.RestClientResponseException(
            "auth",
            401,
            "Unauthorized",
            null,
            "{}".getBytes(),
            null
        );
        Boolean authFailure = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isZohoAuthFailure", unauthorized);
        Boolean benign = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isZohoAuthFailure", new RuntimeException("other"));
        assertThat(authFailure.booleanValue()).isTrue();
        assertThat(benign.booleanValue()).isFalse();
    }

    @Test
    void pullOrderTracking_withLocalForwardShipment_skipsZohoFetch() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder()
            .id(880L)
            .zohoSalesOrderId("so-local")
            .trackingNumber("LOCAL-AWB")
            .shipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        when(customerOrderRepository.findById(880L)).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pullOrderTracking(880L);

        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.never()).getSalesOrder(org.mockito.ArgumentMatchers.anyString());
        assertThat(order.getZohoTrackingLastPulledAt()).isNotNull();
    }

    @Test
    void pullInvoiceStatusOnly_updatesCancelledOrder() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder()
            .id(881L)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .zohoInvoiceId("inv-void");
        when(booksClient.getInvoice("inv-void")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-void", "status", "void")));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(service, "pullInvoiceStatusOnly", order);

        assertThat(order.getStatus()).isEqualTo(com.miniandmore.app.domain.enumeration.OrderStatus.CANCELLED);
        org.mockito.Mockito.verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void deactivateExistingForInactiveZohoItem_deactivatesSimpleProduct() {
        Product simple = new Product().id(30L).sku("SIMPLE-SKU").active(true).itemStructure("SIMPLE");
        when(productRepository.findOneByZohoItemId("zoho-simple")).thenReturn(Optional.of(simple));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> zohoItem = Map.of("item_id", "zoho-simple", "sku", "SIMPLE-SKU", "status", "inactive");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateExistingForInactiveZohoItem",
            zohoItem,
            "SIMPLE-SKU",
            new ArrayList<Product>(),
            builder,
            Optional.empty()
        );
        assertThat(simple.getActive()).isFalse();
        assertThat(simple.getZohoSyncError()).contains("inactive");
    }

    @Test
    void recordCollectedPaymentIfNeeded_recordsCashWhenBalanceDue() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder()
            .id(990L)
            .orderNumber("MNM-COD-BAL")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .zohoInvoiceId("inv-balance");
        when(booksClient.getInvoice("inv-balance"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-balance", "status", "sent", "balance", 250)));
        when(booksClient.createCustomerPayment(any())).thenReturn(Map.of("payment", Map.of("payment_id", "pay-1")));

        ReflectionTestUtils.invokeMethod(service, "recordCollectedPaymentIfNeeded", order, "cust-1");

        org.mockito.Mockito.verify(booksClient).createCustomerPayment(org.mockito.ArgumentMatchers.argThat(p -> "cash".equals(p.get("payment_mode"))));
    }

    @Test
    void resolveLineSku_usesVariantMatrixWhenVariantSkuMissing() {
        Product product = new Product()
            .sku("MATRIX")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"MATRIX-S-RED","size":"S","color":"Red","rate":10,"stock":1,"active":true}]
                """
            );
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem().itemSize("S").color("Red");
        String sku = ReflectionTestUtils.invokeMethod(service, "resolveLineSku", item, product);
        assertThat(sku).isEqualTo("MATRIX-S-RED");
    }

    @Test
    void buildZohoAddress_nullNodeReturnsNull() {
        Map<String, Object> address = ReflectionTestUtils.invokeMethod(service, "buildZohoAddress", (Object) null);
        assertThat(address).isNull();
    }

    @Test
    void resolveExistingVariantParentByCatalogKeys_findsByGroupId() {
        Product parent = new Product().id(44L).sku("GRP-PARENT").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        when(productRepository.findOneByZohoItemId("grp-44")).thenReturn(Optional.of(parent));
        Optional<Product> found = ReflectionTestUtils.invokeMethod(
            service,
            "resolveExistingVariantParentByCatalogKeys",
            "grp-44",
            "Display",
            "FALLBACK"
        );
        assertThat(found).contains(parent);
    }

    @Test
    void finalizeInvoiceForInventory_marksSentAndToleratesFailure() {
        com.miniandmore.app.domain.CustomerOrder prepaid = new com.miniandmore.app.domain.CustomerOrder()
            .paymentMethod("razorpay")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID);
        org.mockito.Mockito.doThrow(new RuntimeException("zoho down")).when(booksClient).markInvoiceAsSent("inv-fail");
        ReflectionTestUtils.invokeMethod(service, "finalizeInvoiceForInventory", prepaid, "inv-fail");
        org.mockito.Mockito.verify(booksClient).markInvoiceAsSent("inv-fail");
    }

    @Test
    void cacheInvoicePdf_uploadsPdfWhenDownloadSucceeds() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder().id(1L);
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        storage.getS3().setMaxUploadBytes(5_000_000L);
        when(applicationProperties.getStorage()).thenReturn(storage);
        when(booksClient.downloadInvoicePdf(eq("inv-pdf"), org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(Optional.of(new com.miniandmore.app.client.ZohoBooksClient.BinaryDownload(new byte[] { 1, 2 }, "application/pdf")));
        when(s3StorageService.uploadInvoicePdf(any(), any())).thenReturn("https://cdn.example.com/invoice.pdf");

        ReflectionTestUtils.invokeMethod(service, "cacheInvoicePdf", order, "inv-pdf");

        assertThat(order.getZohoInvoicePdfUrl()).isEqualTo("https://cdn.example.com/invoice.pdf");
    }

    @Test
    void ensureInvoicePdfCached_returnsExistingUrlWithoutDownload() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder()
            .id(12L)
            .zohoInvoicePdfUrl("https://cdn.example.com/existing.pdf");
        when(customerOrderRepository.findById(12L)).thenReturn(Optional.of(order));
        assertThat(service.ensureInvoicePdfCached(12L)).isEqualTo("https://cdn.example.com/existing.pdf");
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.never()).downloadInvoicePdf(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void resolveZohoItemIdForOrderLine_usesProductZohoItemId() {
        Product product = new Product().id(8L).sku("SKU-8").zohoItemId("zoho-8");
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem().productId("8");
        when(productRepository.findById(8L)).thenReturn(Optional.of(product));
        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item);
        assertThat(itemId).contains("zoho-8");
    }

    @Test
    void resolveOrderProduct_invalidProductIdReturnsEmpty() {
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem().productId("not-a-number");
        Optional<Product> product = ReflectionTestUtils.invokeMethod(service, "resolveOrderProduct", item);
        assertThat(product).isEmpty();
    }

    @Test
    void resolveLineSku_throwsWhenVariantLineCannotBeResolved() {
        Product product = new Product()
            .sku("MATRIX")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[]");
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem().itemSize("S").color("Red");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveLineSku", item, product))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing variantSku");
    }

    @Test
    void resolveOrCreateContact_rethrowsUnexpectedEmailLookupErrors() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder()
            .userLogin("buyer@example.com")
            .shippingAddressJson("{\"full_name\":\"Jane\",\"email\":\"buyer@example.com\"}");
        when(booksClient.findContactByEmail("buyer@example.com")).thenThrow(new RuntimeException("Zoho unavailable"));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Zoho unavailable");
    }

    @Test
    void mergeColorsJsonPreferringRich_blankDuplicateReturnsParent() {
        String parent = "[{\"name\":\"Print\",\"imageUrls\":[\"https://cdn.example.com/p.jpg\"]}]";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorsJsonPreferringRich", parent, "  ");
        assertThat(merged).contains("p.jpg");
    }

    @Test
    void uniqueGroupSku_blankDisplayUsesGroupFallbackAfterCollisions() {
        when(productRepository.findOneBySku(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.of(new Product().id(1L)));
        String sku = ReflectionTestUtils.invokeMethod(service, "uniqueGroupSku", "   ", "grp-long", "FALLBACK");
        assertThat(sku).isEqualTo("G-grp-long");
    }

    @Test
    void sameProduct_bothIdsNullReturnsFalse() {
        Boolean same = ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "sameProduct",
            new Product(),
            new Product()
        );
        assertThat(same.booleanValue()).isFalse();
    }

    @Test
    void retryFailedOrders_recordsFailureWhenSyncThrows() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder()
            .id(700L)
            .orderNumber("MNM-FAIL")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID);
        when(
            customerOrderRepository.findPaidOrdersForZohoRetry(
                eq(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID),
                any(),
                eq(org.springframework.data.domain.PageRequest.of(0, 200))
            )
        ).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order)));
        when(
            customerOrderRepository.findConfirmedCodOrdersForZohoRetry(
                any(),
                eq(org.springframework.data.domain.PageRequest.of(0, 200))
            )
        ).thenReturn(org.springframework.data.domain.Page.empty());
        when(customerOrderRepository.findById(700L)).thenThrow(new RuntimeException("boom"));

        Map<String, Object> result = service.retryFailedOrders();

        assertThat(result.get("failed")).isEqualTo(1);
    }

    @Test
    void resolveInvoiceBalanceDue_paidInvoiceReturnsZero() {
        when(booksClient.getInvoice("inv-paid")).thenReturn(Map.of("invoice", Map.of("status", "paid", "balance", 100)));
        java.math.BigDecimal balance = ReflectionTestUtils.invokeMethod(service, "resolveInvoiceBalanceDue", "inv-paid");
        assertThat(balance).isEqualByComparingTo("0");
    }

    @Test
    void recordCollectedPaymentIfNeeded_skipsWhenInvoiceMissing() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder()
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID);
        ReflectionTestUtils.invokeMethod(service, "recordCollectedPaymentIfNeeded", order, "cust-1");
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.never()).createCustomerPayment(any());
    }

    @Test
    void mergeColorImageMaps_emptyMapsReturnFirstArgument() {
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorImageMaps", (Object) null, "  ");
        assertThat(merged).isEqualTo("  ");
    }

    @Test
    void parseJsonStringListMap_invalidJsonReturnsEmptyMap() {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> parsed = ReflectionTestUtils.invokeMethod(service, "parseJsonStringListMap", "{bad-json");
        assertThat(parsed).isEmpty();
    }

    @Test
    void mergeColorsJsonPreferringRich_indexSkipsNonObjectNodes() {
        String parent = "[1,\"Plain\",{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/r.jpg\"]}]";
        String duplicate = "[{\"name\":\"Plain\",\"imageUrls\":[\"https://cdn.example.com/p.jpg\"]}]";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorsJsonPreferringRich", parent, duplicate);
        assertThat(merged).contains("r.jpg").contains("p.jpg");
    }

    @Test
    void resolveZohoItemIdForOrderLine_throwsWhenZohoLookupReturnsNoItemId() {
        Product product = new Product().id(9L).sku("NEW-SKU");
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem().productId("9");
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("NEW-SKU")).thenReturn(Map.of("sku", "NEW-SKU"));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no item_id");
    }

    @Test
    void cacheInvoicePdf_toleratesDownloadFailures() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder();
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        when(applicationProperties.getStorage()).thenReturn(storage);
        when(booksClient.downloadInvoicePdf(eq("inv-err"), org.mockito.ArgumentMatchers.anyLong())).thenThrow(new RuntimeException("download failed"));
        ReflectionTestUtils.invokeMethod(service, "cacheInvoicePdf", order, "inv-err");
        assertThat(order.getZohoInvoicePdfUrl()).isNull();
    }

    @Test
    void resolveExistingVariantParentByCatalogKeys_findsByDisplayNameSlug() {
        Product parent = new Product().id(55L).sku("HORIZON-SET").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        when(productRepository.findOneByZohoItemId("grp-55")).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug("horizon-set")).thenReturn(Optional.of(parent));
        Optional<Product> found = ReflectionTestUtils.invokeMethod(
            service,
            "resolveExistingVariantParentByCatalogKeys",
            "grp-55",
            "Horizon Set",
            "FALLBACK-SKU"
        );
        assertThat(found).contains(parent);
    }

    @Test
    void findVariantParentForZohoItem_matchesParentByGroupIdOnProduct() {
        Product parent = new Product().id(66L).sku("PARENT").zohoItemId("grp-66").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        List<Product> parents = new ArrayList<>(List.of(parent));
        Map<String, Object> zohoItem = Map.of("item_id", "zoho-var", "sku", "VAR-SKU", "group_id", "grp-66");
        Optional<Product> found = ReflectionTestUtils.invokeMethod(
            service,
            "findVariantParentForZohoItem",
            zohoItem,
            "VAR-SKU",
            parents
        );
        assertThat(found).contains(parent);
    }

    @Test
    void deactivateExistingForInactiveZohoItem_skipsAlreadyInactiveVariantNotice() {
        Product parent = new Product()
            .id(70L)
            .sku("PARENT")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"VAR-1","size":"S","color":"Red","rate":1,"stock":1,"active":false,"zohoItemId":"zoho-var-1"}]
                """
            );
        Map<String, Object> zohoItem = Map.of("item_id", "zoho-var-1", "sku", "VAR-1", "status", "inactive");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateExistingForInactiveZohoItem",
            zohoItem,
            "VAR-1",
            List.of(parent),
            builder,
            Optional.of(parent)
        );
        assertThat(builder.build().toMap().get("notices")).isNotNull();
    }

    @Test
    void ensureInvoicePdfCached_downloadsPdfAndPersistsUrl() {
        com.miniandmore.app.domain.CustomerOrder order = new com.miniandmore.app.domain.CustomerOrder()
            .id(13L)
            .zohoInvoiceId("inv-cache");
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        when(applicationProperties.getStorage()).thenReturn(storage);
        when(customerOrderRepository.findById(13L)).thenReturn(Optional.of(order));
        when(booksClient.downloadInvoicePdf(eq("inv-cache"), org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(Optional.of(new com.miniandmore.app.client.ZohoBooksClient.BinaryDownload(new byte[] { 9 }, "application/pdf")));
        when(s3StorageService.uploadInvoicePdf(any(), any())).thenReturn("https://cdn.example.com/inv-cache.pdf");
        when(customerOrderRepository.save(order)).thenReturn(order);

        assertThat(service.ensureInvoicePdfCached(13L)).isEqualTo("https://cdn.example.com/inv-cache.pdf");
        org.mockito.Mockito.verify(customerOrderRepository).save(order);
    }

    @Test
    void resolveExistingVariantParentByCatalogKeys_findsByDisplayNameDerivedSku() {
        Product parent = new Product().id(56L).sku("HORIZON-SET").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        when(productRepository.findOneByZohoItemId("grp-x")).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug("horizon-set")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("HORIZON-SET")).thenReturn(Optional.of(parent));
        Optional<Product> found = ReflectionTestUtils.invokeMethod(
            service,
            "resolveExistingVariantParentByCatalogKeys",
            "grp-x",
            "Horizon Set",
            "FALLBACK-SKU"
        );
        assertThat(found).contains(parent);
    }

    @Test
    void sameProduct_sameInstance_returnsTrue() {
        Product product = new Product().id(1L);
        Boolean same = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "sameProduct", product, product);
        assertThat(same).isTrue();
    }

    @Test
    void mergeColorsJsonPreferringRich_overwritesExistingRichObjectForSameName() {
        String parent = "[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/old.jpg\"]}]";
        String duplicate = "[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/new.jpg\"],\"swatchUrl\":\"https://cdn.example.com/s.jpg\"}]";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorsJsonPreferringRich", parent, duplicate);
        assertThat(merged).contains("new.jpg").contains("s.jpg").doesNotContain("old.jpg");
    }

    @Test
    void deactivateSimpleDuplicateForVariantSku_deactivatesActiveSimpleDuplicate() {
        Product parent = new Product().id(1L).sku("PARENT");
        Product duplicate = new Product().id(2L).sku("CHILD").active(true).itemStructure("SIMPLE");
        when(productRepository.findOneBySku("CHILD")).thenReturn(Optional.of(duplicate));
        when(productRepository.save(duplicate)).thenAnswer(inv -> inv.getArgument(0));
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        Product result = ReflectionTestUtils.invokeMethod(
            service,
            "deactivateSimpleDuplicateForVariantSku",
            parent,
            "CHILD",
            builder
        );
        assertThat(result).isSameAs(duplicate);
        assertThat(duplicate.getActive()).isFalse();
        assertThat(builder.build().toMap().get("notices")).isNotNull();
    }

    @Test
    void resolveZohoItemIdForOrderLine_variantWithCachedZohoId_skipsBooksLookup() {
        String variants =
            "[{\"sku\":\"VAR-CACHED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true,\"zohoItemId\":\"zoho-cached\"}]";
        Product product = new Product().id(15L).sku("PARENT").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS).variantsJson(variants);
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem().productId("15").variantSku("VAR-CACHED");
        when(productRepository.findById(15L)).thenReturn(Optional.of(product));

        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item);

        assertThat(itemId).contains("zoho-cached");
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.never()).findItemBySku(any());
    }

    @Test
    void resolveZohoItemIdForOrderLine_variantResolvesSkuFromSizeColor() {
        String variants =
            "[{\"sku\":\"VAR-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true,\"zohoItemId\":\"zoho-from-matrix\"}]";
        Product product = new Product().id(16L).sku("PARENT").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS).variantsJson(variants);
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem().productId("16").itemSize("S").color("Red");
        when(productRepository.findById(16L)).thenReturn(Optional.of(product));

        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item);

        assertThat(itemId).contains("zoho-from-matrix");
    }

    @Test
    void resolveZohoItemIdForOrderLine_booksLookupCachesVariantZohoId() {
        String variants = "[{\"sku\":\"VAR-LOOKUP\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":1,\"stock\":1,\"active\":true}]";
        Product product = new Product().id(17L).sku("PARENT").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS).variantsJson(variants);
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem().productId("17").variantSku("VAR-LOOKUP");
        when(productRepository.findById(17L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("VAR-LOOKUP")).thenReturn(Map.of("item_id", "zoho-lookup"));
        when(productRepository.save(product)).thenReturn(product);

        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item);

        assertThat(itemId).contains("zoho-lookup");
        assertThat(product.getVariantsJson()).contains("zoho-lookup");
        org.mockito.Mockito.verify(productRepository).save(product);
    }

    @Test
    void resolveZohoItemIdForOrderLine_missingProductId_returnsEmpty() {
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem();
        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item);
        assertThat(itemId).isEmpty();
    }

    @Test
    void resolveZohoItemIdForOrderLine_lookupFailureWrapsRuntimeException() {
        Product product = new Product().id(18L).sku("FAIL-SKU");
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem().productId("18");
        when(productRepository.findById(18L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("FAIL-SKU")).thenThrow(new RuntimeException("Zoho down"));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Zoho item lookup failed");
    }

    @Test
    void resolveZohoItemIdForOrderLine_variantMatrixWithoutResolvableSku_returnsEmpty() {
        Product product = new Product().id(19L).sku("PARENT").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS).variantsJson("[]");
        com.miniandmore.app.domain.OrderItem item = new com.miniandmore.app.domain.OrderItem().productId("19");
        when(productRepository.findById(19L)).thenReturn(Optional.of(product));

        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item);

        assertThat(itemId).isEmpty();
    }

    @Test
    void indexColorNodes_prefersRichObjectOverPlainTextForSameKey() throws com.fasterxml.jackson.core.JsonProcessingException {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(
            "[\"red\",{\"name\":\"Red\",\"imageUrl\":\"https://cdn.example.com/r.jpg\"}]"
        );
        java.util.LinkedHashMap<String, com.fasterxml.jackson.databind.JsonNode> byName = new java.util.LinkedHashMap<>();
        ReflectionTestUtils.invokeMethod(service, "indexColorNodes", root, byName, true);
        assertThat(byName).hasSize(1);
        assertThat(byName.get("red").isObject()).isTrue();
        assertThat(byName.get("red").path("imageUrl").asText()).contains("r.jpg");
    }

    @Test
    void indexColorNodes_skipsNonTextualNonObjectNodes() throws com.fasterxml.jackson.core.JsonProcessingException {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree("[42,null,{\"name\":\"Blue\"}]");
        java.util.LinkedHashMap<String, com.fasterxml.jackson.databind.JsonNode> byName = new java.util.LinkedHashMap<>();
        ReflectionTestUtils.invokeMethod(service, "indexColorNodes", root, byName, false);
        assertThat(byName).containsOnlyKeys("blue");
    }

    @Test
    void indexColorNodes_nullOrNonArrayRoot_isNoOp() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.util.LinkedHashMap<String, com.fasterxml.jackson.databind.JsonNode> byName = new java.util.LinkedHashMap<>();
        ReflectionTestUtils.invokeMethod(service, "indexColorNodes", null, byName, false);
        ReflectionTestUtils.invokeMethod(service, "indexColorNodes", objectMapper.readTree("{\"x\":1}"), byName, false);
        assertThat(byName).isEmpty();
    }

    @Test
    void indexColorNodes_skipsBlankObjectNamesAndNullElements() throws com.fasterxml.jackson.core.JsonProcessingException {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree("[null,{\"name\":\"  \"},\"Teal\"]");
        java.util.LinkedHashMap<String, com.fasterxml.jackson.databind.JsonNode> byName = new java.util.LinkedHashMap<>();
        ReflectionTestUtils.invokeMethod(service, "indexColorNodes", root, byName, false);
        assertThat(byName).containsOnlyKeys("teal");
        assertThat(byName.get("teal").asText()).isEqualTo("Teal");
    }

    @Test
    void indexColorNodes_preferRichFalse_keepsFirstEntryForDuplicateKey() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.util.LinkedHashMap<String, com.fasterxml.jackson.databind.JsonNode> byName = new java.util.LinkedHashMap<>();
        ReflectionTestUtils.invokeMethod(service, "indexColorNodes", objectMapper.readTree("[\"Red\"]"), byName, false);
        ReflectionTestUtils.invokeMethod(service, "indexColorNodes", objectMapper.readTree("[\"RED\"]"), byName, false);
        assertThat(byName.get("red").asText()).isEqualTo("Red");
    }

    @Test
    void indexColorNodes_preferRichTrue_replacesRichObjectWithNewerRich() throws com.fasterxml.jackson.core.JsonProcessingException {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(
            "[{\"name\":\"Red\",\"imageUrl\":\"https://cdn.example.com/a.jpg\"},{\"name\":\"Red\",\"imageUrl\":\"https://cdn.example.com/b.jpg\"}]"
        );
        java.util.LinkedHashMap<String, com.fasterxml.jackson.databind.JsonNode> byName = new java.util.LinkedHashMap<>();
        ReflectionTestUtils.invokeMethod(service, "indexColorNodes", root, byName, true);
        assertThat(byName.get("red").path("imageUrl").asText()).endsWith("b.jpg");
    }

    @Test
    void mergeColorsJsonPreferringRich_nonArrayDuplicateReturnsParent() {
        String parent = "[\"Coral\"]";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorsJsonPreferringRich", parent, "{\"not\":\"array\"}");
        assertThat(merged).isEqualTo(parent);
    }

    @Test
    void applyPulledDescription_blankRaw_isNoOp() {
        Product product = new Product().sku("BLANK");
        product.setDescription("keep");
        ReflectionTestUtils.invokeMethod(service, "applyPulledDescription", product, "   ");
        assertThat(product.getDescription()).isEqualTo("keep");
    }

    @Test
    void applyPulledDescription_whitespaceBodyFallsThroughToMetadataClear() {
        Product product = new Product().sku("WS-BODY");
        ProductDescriptionCodec.Decoded decoded = new ProductDescriptionCodec.Decoded(
            "Cat",
            null,
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "   "
        );
        when(descriptionCodec.decode("x")).thenReturn(decoded);
        ReflectionTestUtils.invokeMethod(service, "applyPulledDescription", product, "x");
        assertThat(product.getDescription()).isNull();
    }

    @Test
    void parseJsonStringListMap_coversSkipAndMalformedPaths() {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> emptyNull = ReflectionTestUtils.invokeMethod(service, "parseJsonStringListMap", (Object) null);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> emptyArray = ReflectionTestUtils.invokeMethod(service, "parseJsonStringListMap", "[]");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> malformed = ReflectionTestUtils.invokeMethod(service, "parseJsonStringListMap", "{bad");
        assertThat(emptyNull).isEmpty();
        assertThat(emptyArray).isEmpty();
        assertThat(malformed).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, List<String>> parsed = ReflectionTestUtils.invokeMethod(
            service,
            "parseJsonStringListMap",
            "{\"Ok\":[\"https://cdn.example.com/a.jpg\",\"  \"],\"Empty\":[],\"Bad\":\"x\",\"\":[\"z\"]}"
        );
        assertThat(parsed).containsOnlyKeys("Ok");
        assertThat(parsed.get("Ok")).containsExactly("https://cdn.example.com/a.jpg");
    }

    @Test
    void buildItemPayload_salesTypeWithoutInventoryAccount_andOpeningStockLocations() {
        Product product = new Product().id(7L).name("Widget").sku("W-7").price(BigDecimal.TEN).stock(5).description("  nice  ");
        @SuppressWarnings("unchecked")
        Map<String, Object> salesPayload = ReflectionTestUtils.invokeMethod(
            service,
            "buildItemPayload",
            product,
            true,
            Optional.empty(),
            "loc-1"
        );
        assertThat(salesPayload.get("item_type")).isEqualTo("sales");
        assertThat(salesPayload).doesNotContainKey("locations");
        assertThat(salesPayload.get("description")).isEqualTo("nice");

        @SuppressWarnings("unchecked")
        Map<String, Object> inventoryPayload = ReflectionTestUtils.invokeMethod(
            service,
            "buildItemPayload",
            product,
            true,
            Optional.of("inv-acct"),
            "loc-9"
        );
        assertThat(inventoryPayload.get("item_type")).isEqualTo("inventory");
        assertThat(inventoryPayload.get("locations")).isNotNull();
    }

    @Test
    void ensureLabelRateAtLeastRate_bumpsLabelWhenBelowRate() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rate", BigDecimal.valueOf(100));
        payload.put("label_rate", BigDecimal.valueOf(50));
        ReflectionTestUtils.invokeMethod(service, "ensureLabelRateAtLeastRate", payload);
        assertThat(payload.get("label_rate")).isEqualTo("100");
    }
}
