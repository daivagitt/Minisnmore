package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class ProductColorImagesSupportBranchCoverageTest {

    private ProductColorImagesSupport support;

    @BeforeEach
    void setUp() {
        support = new ProductColorImagesSupport(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void seedGalleryUrlIfEmpty_blankColorOrUrl_isNoOp() {
        Product product = new Product().colorsJson("[{\"name\":\"Navy\",\"imageUrls\":[]}]");
        support.seedGalleryUrlIfEmpty(product, null, "https://cdn.example.com/n.jpg");
        support.seedGalleryUrlIfEmpty(product, "Navy", null);
        support.seedGalleryUrlIfEmpty(product, "  ", "https://cdn.example.com/n.jpg");
        assertThat(product.getColorsJson()).doesNotContain("cdn.example.com");
    }

    @Test
    void replaceColorGallery_nullProductOrBlankColor_isNoOp() {
        support.replaceColorGallery(null, "Red", List.of("https://cdn.example.com/r.jpg"));
        Product product = new Product().colorsJson("[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/old.jpg\"]}]");
        support.replaceColorGallery(product, null, List.of("https://cdn.example.com/new.jpg"));
        assertThat(product.getColorsJson()).contains("old.jpg").doesNotContain("new.jpg");
    }

    @Test
    void appendGalleryUrl_nullProduct_isNoOp() {
        support.appendGalleryUrl(null, "Blue", "https://cdn.example.com/b.jpg");
    }

    @Test
    void syncProductColorImages_nullProduct_isNoOp() {
        support.syncProductColorImages(null);
    }

    @Test
    void isImagesJsonEmpty_whitespaceTextNodesTreatedAsEmpty() {
        assertThat(support.isImagesJsonEmpty("[\"  \",\"\"]")).isTrue();
        assertThat(support.isImagesJsonEmpty("[\"https://cdn.example.com/x.jpg\"]")).isFalse();
    }

    @Test
    void allSimpleProductGallerySourceUrls_mergesImagesAndColorGalleries() {
        Product product = new Product()
            .imagesJson("[\"https://cdn.example.com/hero.jpg\"]")
            .colorsJson("[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/navy.jpg\"]}]");
        assertThat(support.allSimpleProductGallerySourceUrls(product))
            .containsExactly("https://cdn.example.com/hero.jpg", "https://cdn.example.com/navy.jpg");
    }

    @Test
    void replaceColorGallery_addsNewColorWhenNotFound() {
        Product product = new Product().colorsJson("[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/r.jpg\"]}]");
        support.replaceColorGallery(product, "Blue", List.of("https://cdn.example.com/b.jpg"));
        assertThat(support.galleryUrlsForColor(product, "Blue")).containsExactly("https://cdn.example.com/b.jpg");
    }

    @Test
    void mergeFrozenColorMedia_emptyAdminOrMatrix_returnsEarly() {
        String matrix = "[{\"name\":\"A\",\"imageUrls\":[\"https://cdn.example.com/a.jpg\"]}]";
        assertThat(support.mergeFrozenColorMedia(matrix, null)).contains("a.jpg");
        assertThat(support.mergeFrozenColorMedia("[]", matrix)).contains("A");
    }

    @Test
    void freezeSwatchesFromStored_emptyFrozen_returnsNormalizedCurrent() {
        String current = "[{\"name\":\"Print\",\"imageUrls\":[]}]";
        assertThat(support.freezeSwatchesFromStored(current, null)).contains("Print");
    }

    @Test
    void serializeStringList_failureSurfacesAsIllegalArgumentException() throws Exception {
        ObjectMapper mapper = spy(new ObjectMapper());
        ProductColorImagesSupport broken = new ProductColorImagesSupport(mapper);
        doThrow(new JsonProcessingException("fail") {}).when(mapper).writeValueAsString(any());
        assertThatThrownBy(() ->
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(broken, "serializeStringList", List.of("https://cdn.example.com/x.jpg"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failed to serialize images JSON");
    }

    @Test
    void galleryMapFromColorsJson_blankNameEntriesSkipped() {
        Map<String, List<String>> map = support.galleryMapFromColorsJson("[{\"name\":\"  \",\"imageUrls\":[\"https://cdn.example.com/x.jpg\"]}]");
        assertThat(map).isEmpty();
    }

    @Test
    void buildPositionalColorRename_recordsRenamedSlots() {
        String before = "[{\"name\":\"Old\",\"imageUrls\":[]},{\"name\":\"Two\",\"imageUrls\":[]}]";
        String after = "[{\"name\":\"New\",\"imageUrls\":[]},{\"name\":\"Two\",\"imageUrls\":[]}]";
        Map<String, String> rename = support.buildPositionalColorRename(before, after);
        assertThat(rename).containsEntry("Old", "New");
    }

    @Test
    void serializeMap_failureSurfacesAsIllegalArgumentException() throws Exception {
        ObjectMapper mapper = spy(new ObjectMapper());
        ProductColorImagesSupport broken = new ProductColorImagesSupport(mapper);
        doThrow(new JsonProcessingException("fail") {}).when(mapper).writeValueAsString(any());
        assertThatThrownBy(() -> broken.serializeMap(Map.of("Red", List.of("https://cdn.example.com/r.jpg"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failed to serialize colour images map");
    }

    @Test
    void coerceMap_fromRawMap_skipsBlankKeysAndEmptyUrlLists() {
        Map<String, List<String>> map = support.coerceMap(Map.of("  ", List.of("https://cdn.example.com/x.jpg"), "Blue", List.of()));
        assertThat(map).isEmpty();
        Map<String, List<String>> parsed = support.coerceMap(Map.of("Navy", List.of("https://cdn.example.com/n.jpg")));
        assertThat(parsed).containsKey("Navy");
    }

    @Test
    void galleryUrlsForColor_truncatesBeyondMaxPerColor() throws Exception {
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add("https://cdn.example.com/c" + i + ".jpg");
        }
        Product product = new Product().colorsJson("[{\"name\":\"Red\",\"imageUrls\":" + new ObjectMapper().writeValueAsString(many) + "}]");
        assertThat(support.galleryUrlsForColor(product, "Red")).hasSize(ProductColorImagesSupport.MAX_IMAGES_PER_COLOR);
    }

    @Test
    void replaceColorGallery_singleImageUsesImageUrlField() {
        Product product = new Product().colorsJson("[]");
        support.replaceColorGallery(product, "Mint", List.of("https://cdn.example.com/m.jpg"));
        assertThat(product.getColorsJson()).contains("\"imageUrl\":\"https://cdn.example.com/m.jpg\"");
    }

    @Test
    void parseMap_invalidJsonReturnsEmptyMap() {
        assertThat(support.parseMap("{bad")).isEmpty();
    }

    @Test
    void stripUrlsFromImagesJson_removesMatchingUrlsAndNullsWhenEmpty() {
        Product product = new Product()
            .imagesJson("[\"https://cdn.example.com/keep.jpg\",\"https://cdn.example.com/drop.jpg\",\"  \"]");
        support.stripUrlsFromImagesJson(product, List.of("https://cdn.example.com/drop.jpg", " "));
        assertThat(product.getImagesJson()).contains("keep.jpg").doesNotContain("drop.jpg");

        Product onlyRemoved = new Product().imagesJson("[\"https://cdn.example.com/drop.jpg\"]");
        support.stripUrlsFromImagesJson(onlyRemoved, List.of("https://cdn.example.com/drop.jpg"));
        assertThat(onlyRemoved.getImagesJson()).isNull();
    }

    @Test
    void ensureProductImagesFromColorGalleries_usesSwatchWhenNoGalleryUrls() {
        Product product = new Product()
            .imagesJson("[]")
            .colorsJson("[{\"name\":\"Navy\",\"swatchUrl\":\"https://cdn.example.com/swatch.jpg\",\"imageUrls\":[]}]");
        support.ensureProductImagesFromColorGalleries(product);
        assertThat(product.getImagesJson()).contains("swatch.jpg");
    }

    @Test
    void galleryUrlsForColor_mergesColorsJsonAndColorImagesMap() {
        Product product = new Product()
            .colorsJson("[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/r1.jpg\"]}]")
            .colorImagesJson("{\"Red\":[\"https://cdn.example.com/r2.jpg\"]}");
        assertThat(support.galleryUrlsForColor(product, "red"))
            .containsExactly("https://cdn.example.com/r1.jpg", "https://cdn.example.com/r2.jpg");
    }

    @Test
    void stripUrlsFromImagesJson_skipsNonTextualNodes() {
        Product product = new Product().imagesJson("[{\"url\":\"x\"},\"https://cdn.example.com/ok.jpg\"]");
        support.stripUrlsFromImagesJson(product, List.of("https://cdn.example.com/missing.jpg"));
        assertThat(product.getImagesJson()).contains("ok.jpg");
    }

    @Test
    void freezeSwatchesFromStored_usesPositionWhenNameKeyMissing() {
        String frozen =
            "[{\"name\":\"A\",\"swatchUrl\":\"https://cdn.example.com/s1.jpg\"},{\"name\":\"B\",\"swatchUrl\":\"https://cdn.example.com/s2.jpg\"}]";
        String current = "[{\"name\":\"C\",\"imageUrls\":[]},{\"name\":\"D\",\"imageUrls\":[]}]";
        String merged = support.freezeSwatchesFromStored(current, frozen);
        assertThat(merged).contains("s1.jpg").contains("s2.jpg").contains("C").contains("D");
    }

    @Test
    void mergeRichColorMetadata_restoresGalleriesForMatrixNames() {
        String matrix = "[\"Navy\",\"Coral\"]";
        String rich =
            "[{\"name\":\"Navy\",\"swatchUrl\":\"https://cdn.example.com/n.jpg\",\"imageUrls\":[\"https://cdn.example.com/n-rear.jpg\"]}]";
        String merged = support.mergeRichColorMetadata(matrix, rich);
        assertThat(merged).contains("Navy").contains("n-rear.jpg").contains("Coral");
    }

    @Test
    void galleryMapLookup_matchesIgnoreCase() {
        Map<String, List<String>> map = Map.of("Navy", List.of("https://cdn.example.com/n.jpg"));
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(support, "galleryMapLookup", map, "navy");
        assertThat(urls).containsExactly("https://cdn.example.com/n.jpg");
    }

    @Test
    void appendGalleryUrl_skipsDuplicateUrlAndTruncatesToMax() throws Exception {
        Product product = new Product()
            .colorsJson("[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/r0.jpg\"]}]");
        support.appendGalleryUrl(product, "Red", "https://cdn.example.com/r0.jpg");
        assertThat(support.galleryUrlsForColor(product, "Red")).containsExactly("https://cdn.example.com/r0.jpg");

        List<String> many = new ArrayList<>();
        for (int i = 0; i < ProductColorImagesSupport.MAX_IMAGES_PER_COLOR; i++) {
            many.add("https://cdn.example.com/fill" + i + ".jpg");
        }
        Product crowded = new Product()
            .colorsJson("[{\"name\":\"Blue\",\"imageUrls\":" + new ObjectMapper().writeValueAsString(many) + "}]");
        support.appendGalleryUrl(crowded, "Blue", "https://cdn.example.com/extra.jpg");
        assertThat(support.galleryUrlsForColor(crowded, "Blue")).hasSize(ProductColorImagesSupport.MAX_IMAGES_PER_COLOR);
    }

    @Test
    void seedGalleryUrlIfEmpty_appendsWhenColorMissingFromJson() {
        Product product = new Product().colorsJson("[{\"name\":\"Other\",\"imageUrls\":[]}]");
        support.seedGalleryUrlIfEmpty(product, "NewColor", "https://cdn.example.com/new.jpg");
        assertThat(support.galleryUrlsForColor(product, "NewColor")).containsExactly("https://cdn.example.com/new.jpg");
    }

    @Test
    void replaceColorGallery_updatesExistingAndClearsStaleColorImagesKey() {
        Product product = new Product()
            .colorsJson("[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/old.jpg\"]}]")
            .colorImagesJson("{\"NAVY\":[\"https://cdn.example.com/stale.jpg\"]}");
        support.replaceColorGallery(product, "navy", List.of("https://cdn.example.com/new.jpg"));
        assertThat(support.galleryUrlsForColor(product, "navy")).containsExactly("https://cdn.example.com/new.jpg");
    }

    @Test
    void replaceColorGallery_emptyUrlsOnExistingColor_clearsGallery() {
        Product product = new Product()
            .colorsJson("[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/r.jpg\"]}]")
            .colorImagesJson("{\"Red\":[\"https://cdn.example.com/r.jpg\"]}");
        support.replaceColorGallery(product, "Red", List.of());
        assertThat(support.galleryUrlsForColor(product, "Red")).isEmpty();
    }

    @Test
    void galleryUrlsForColor_nullProductOrBlankColor_returnsEmpty() {
        Product product = new Product().colorsJson("[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/r.jpg\"]}]");
        assertThat(support.galleryUrlsForColor(null, "Red")).isEmpty();
        assertThat(support.galleryUrlsForColor(product, "  ")).isEmpty();
    }

    @Test
    void stripUrlsFromImagesJson_blankRemovalSetOrEmptyImages_isNoOp() {
        Product product = new Product().imagesJson("[\"https://cdn.example.com/keep.jpg\"]");
        List<String> blankRemovals = new ArrayList<>();
        blankRemovals.add(" ");
        blankRemovals.add(null);
        support.stripUrlsFromImagesJson(product, blankRemovals);
        assertThat(product.getImagesJson()).contains("keep.jpg");
        Product emptyImages = new Product().imagesJson("[]");
        support.stripUrlsFromImagesJson(emptyImages, List.of("https://cdn.example.com/x.jpg"));
        assertThat(emptyImages.getImagesJson()).isEqualTo("[]");
    }

    @Test
    void ensureProductImagesFromColorGalleries_skipsWhenImagesAlreadyPresent() {
        Product product = new Product()
            .imagesJson("[\"https://cdn.example.com/existing.jpg\"]")
            .colorsJson("[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/n.jpg\"]}]");
        support.ensureProductImagesFromColorGalleries(product);
        assertThat(product.getImagesJson()).doesNotContain("n.jpg");
    }

    @Test
    void collectSwatchAndGalleryUrls_deduplicateAcrossEntries() {
        String colors =
            "[{\"name\":\"A\",\"swatchUrl\":\"https://cdn.example.com/s.jpg\"},{\"name\":\"B\",\"swatchUrl\":\"https://cdn.example.com/s.jpg\"}," +
            "{\"name\":\"C\",\"imageUrls\":[\"https://cdn.example.com/g.jpg\",\"https://cdn.example.com/g.jpg\"]}]";
        assertThat(support.collectSwatchUrlsFromColorsJson(colors)).containsExactly("https://cdn.example.com/s.jpg");
        assertThat(support.collectGalleryUrlsFromColorsJson(colors)).containsExactly("https://cdn.example.com/g.jpg");
    }

    @ParameterizedTest
    @MethodSource("parseImagesJsonUrlsCases")
    void parseImagesJsonUrls_coversNullAndInvalid(String json, int expectedSize) {
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(support, "parseImagesJsonUrls", json);
        assertThat(urls).hasSize(expectedSize);
    }

    private static Stream<Arguments> parseImagesJsonUrlsCases() {
        return Stream.of(
            Arguments.of(null, 0),
            Arguments.of("  ", 0),
            Arguments.of("[null,\" https://cdn.example.com/a.jpg \",\"\"]", 1),
            Arguments.of("{bad", 0)
        );
    }

    @Test
    void isImagesJsonEmpty_nonTextualArrayElementsTreatedAsEmpty() {
        assertThat(support.isImagesJsonEmpty("[{},123]")).isTrue();
    }

    @Test
    void realignColorNamesToGalleryKeys_buildsFromMapWhenColorsEmpty() {
        Map<String, List<String>> gallery = Map.of("Scarlet", List.of("https://cdn.example.com/s.jpg"));
        String realigned = support.realignColorNamesToGalleryKeys("[]", gallery);
        assertThat(realigned).contains("Scarlet").contains("s.jpg");
    }

    @Test
    void realignColorNamesToGalleryKeys_sizeMismatch_keepsPlainNamesWithoutOverlap() {
        Map<String, List<String>> gallery = Map.of(
            "A",
            List.of("https://cdn.example.com/a.jpg"),
            "B",
            List.of("https://cdn.example.com/b.jpg")
        );
        String realigned = support.realignColorNamesToGalleryKeys("[\"OnlyOne\"]", gallery);
        assertThat(realigned).contains("OnlyOne").doesNotContain("a.jpg");
    }

    @Test
    void hasGalleryKeyOverlap_detectsAndIgnoresBlankKeys() {
        Object entries = ReflectionTestUtils.invokeMethod(support, "parseColorEntries", "[{\"name\":\"Navy\",\"imageUrls\":[]}]");
        Boolean overlap = ReflectionTestUtils.invokeMethod(
            support,
            "hasGalleryKeyOverlap",
            entries,
            Map.of("  ", List.of("https://cdn.example.com/x.jpg"), "Other", List.of("https://cdn.example.com/y.jpg"))
        );
        assertThat(overlap.booleanValue()).isFalse();
        Boolean hit = ReflectionTestUtils.invokeMethod(
            support,
            "hasGalleryKeyOverlap",
            entries,
            Map.of("navy", List.of("https://cdn.example.com/n.jpg"))
        );
        assertThat(hit.booleanValue()).isTrue();
    }

    @Test
    void applyGalleryMapToColorsJson_keepsEntryWhenLookupMisses() {
        String colors = "[{\"name\":\"Keep\",\"imageUrls\":[\"https://cdn.example.com/k.jpg\"]}]";
        String merged = support.applyGalleryMapToColorsJson(colors, Map.of("Other", List.of("https://cdn.example.com/o.jpg")));
        assertThat(merged).contains("k.jpg").doesNotContain("o.jpg");
    }

    @Test
    void mergeFrozenColorMedia_skipsRowsWithExistingGallery() {
        String matrix = "[{\"name\":\"A\",\"imageUrls\":[\"https://cdn.example.com/a.jpg\"]},{\"name\":\"B\",\"imageUrls\":[]}]";
        String admin = "[{\"name\":\"A\",\"imageUrls\":[\"https://cdn.example.com/ignored.jpg\"]},{\"name\":\"B\",\"imageUrls\":[\"https://cdn.example.com/b.jpg\"]}]";
        String merged = support.mergeFrozenColorMedia(matrix, admin);
        assertThat(merged).contains("b.jpg");
    }

    @Test
    void freezeSwatchesFromStored_usesNameKeyBeforePosition() {
        String frozen = "[{\"name\":\"Target\",\"swatchUrl\":\"https://cdn.example.com/key.jpg\"}]";
        String current = "[{\"name\":\"Target\",\"imageUrls\":[]}]";
        assertThat(support.freezeSwatchesFromStored(current, frozen)).contains("key.jpg");
    }

    @Test
    void mergeRichColorMetadata_skipsDuplicateMatrixNamesAndUnknownRich() {
        String matrix = "[\"Navy\",\"Navy\",\"Coral\"]";
        String rich = "[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/n.jpg\"]}]";
        String merged = support.mergeRichColorMetadata(matrix, rich);
        assertThat(merged).contains("Navy").contains("Coral").contains("n.jpg");
    }

    @ParameterizedTest
    @MethodSource("galleryMapLookupCases")
    void galleryMapLookup_remainingBranches(Map<String, List<String>> map, String color, int expectedSize) {
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(support, "galleryMapLookup", map, color);
        assertThat(urls).hasSize(expectedSize);
    }

    private static Stream<Arguments> galleryMapLookupCases() {
        Map<String, List<String>> emptyListKey = Map.of("Navy", List.of());
        Map<String, List<String>> caseKey = new LinkedHashMap<>();
        caseKey.put(" Navy ", List.of("https://cdn.example.com/n.jpg"));
        return Stream.of(
            Arguments.of(null, "Navy", 0),
            Arguments.of(Map.of(), "Navy", 0),
            Arguments.of(emptyListKey, "Navy", 0),
            Arguments.of(caseKey, "navy", 1)
        );
    }

    @Test
    void coerceMap_convertsArbitraryObjectViaMapper() {
        Map<String, List<String>> coerced = support.coerceMap(List.of(Map.of("Teal", List.of("https://cdn.example.com/t.jpg"))));
        assertThat(coerced).isEmpty();
        Map<String, Object> raw = Map.of("Teal", List.of("https://cdn.example.com/t.jpg"));
        assertThat(support.coerceMap(raw)).containsKey("Teal");
    }

    @Test
    void sanitizeMap_viaParseMap_skipsBlankKeysAndEmptyLists() {
        Map<String, List<String>> parsed = support.parseMap("{\"  \":[\"https://cdn.example.com/x.jpg\"],\"Ok\":[]}");
        assertThat(parsed).isEmpty();
    }

    @Test
    void parseColorEntries_skipsDuplicateNamesAndInvalidRoot() {
        String dup = "[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/a.jpg\"]},{\"name\":\"red\",\"imageUrls\":[\"https://cdn.example.com/b.jpg\"]}]";
        assertThat(support.galleryMapFromColorsJson(dup)).containsKey("Red").doesNotContainKey("red");
        assertThat(support.galleryMapFromColorsJson("{not-array}")).isEmpty();
    }

    @Test
    void parseColorEntry_andParseGalleryUrls_reflectionBranches() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode textualBlank = mapper.readTree("\"   \"");
        JsonNode numeric = mapper.readTree("99");
        Object blankEntry = ReflectionTestUtils.invokeMethod(support, "parseColorEntry", textualBlank);
        Object numericEntry = ReflectionTestUtils.invokeMethod(support, "parseColorEntry", numeric);
        assertThat(blankEntry).isNull();
        assertThat(numericEntry).isNull();

        JsonNode rich = mapper.readTree(
            "{\"name\":\"Bee\",\"swatchUrl\":\"https://cdn.example.com/s.jpg\",\"imageUrl\":\"https://cdn.example.com/s.jpg\",\"imageUrls\":[\"https://cdn.example.com/g1.jpg\",\"https://cdn.example.com/g2.jpg\"]}"
        );
        @SuppressWarnings("unchecked")
        List<String> gallery = ReflectionTestUtils.invokeMethod(
            support,
            "parseGalleryUrls",
            rich,
            "https://cdn.example.com/s.jpg"
        );
        assertThat(gallery).containsExactly("https://cdn.example.com/g1.jpg", "https://cdn.example.com/g2.jpg");
    }

    @Test
    void writeColorEntries_plainNamesWhenNoRichMetadata() {
        Object entries = ReflectionTestUtils.invokeMethod(support, "parseColorEntries", "[\"PlainA\",\"PlainB\"]");
        String json = ReflectionTestUtils.invokeMethod(support, "writeColorEntries", entries);
        assertThat(json).contains("PlainA").doesNotContain("imageUrl");
    }

    @Test
    void uniqueUrls_truncatesBeyondMax() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add("https://cdn.example.com/u" + i + ".jpg");
        }
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(support, "uniqueUrls", many);
        assertThat(urls).hasSize(ProductColorImagesSupport.MAX_IMAGES_PER_COLOR);
    }

    @Test
    void textOrNull_nonTextualJsonNodeReturnsNull() throws Exception {
        JsonNode objectNode = new ObjectMapper().readTree("{\"x\":1}");
        Object text = ReflectionTestUtils.invokeMethod(support, "textOrNull", objectNode);
        assertThat(text).isNull();
    }

    @Test
    void syncProductColorImages_clearsColorImagesWhenGalleryEmpty() {
        Product product = new Product().colorsJson("[{\"name\":\"Plain\",\"imageUrls\":[]}]");
        support.syncProductColorImages(product);
        assertThat(product.getColorImagesJson()).isNull();
    }

    @Test
    void replaceColorGallery_foundFalseAndEmptyUrls_doesNotAddColor() {
        Product product = new Product().colorsJson("[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/r.jpg\"]}]");
        support.replaceColorGallery(product, "Blue", List.of());
        assertThat(support.galleryUrlsForColor(product, "Blue")).isEmpty();
    }

    @Test
    void galleryUrlsForColor_skipsEntriesWithNullName() {
        Product product = new Product().colorsJson("[\"PlainOnly\"]");
        assertThat(support.galleryUrlsForColor(product, "PlainOnly")).isEmpty();
    }

    @Test
    void stripUrlsFromImagesJson_nullRootArray_stopsWithoutChange() {
        Product product = new Product().imagesJson("null");
        support.stripUrlsFromImagesJson(product, List.of("https://cdn.example.com/x.jpg"));
        assertThat(product.getImagesJson()).isEqualTo("null");
    }

    @Test
    void realignColorNamesToGalleryKeys_positionalRealignWhenSizesMatch() {
        Map<String, List<String>> gallery = Map.of(
            "RealName",
            List.of("https://cdn.example.com/r.jpg"),
            "Second",
            List.of("https://cdn.example.com/s.jpg")
        );
        String colors = "[{\"name\":\"Print 1\",\"imageUrls\":[]},{\"name\":\"Print 2\",\"imageUrls\":[]}]";
        String realigned = support.realignColorNamesToGalleryKeys(colors, gallery);
        assertThat(realigned).contains("RealName").contains("r.jpg");
    }

    @Test
    void applyGalleryMapToColorsJson_emptyColors_buildsFromMapSkippingBlankKeys() {
        Map<String, List<String>> gallery = new LinkedHashMap<>();
        gallery.put("", List.of("https://cdn.example.com/skip.jpg"));
        gallery.put("Ok", List.of("https://cdn.example.com/ok.jpg"));
        String colors = support.applyGalleryMapToColorsJson("[]", gallery);
        assertThat(colors).contains("Ok").doesNotContain("skip.jpg");
    }

    @Test
    void mergeFrozenColorMedia_keepsMatrixRowWhenAdminSlotMissing() {
        String matrix = "[{\"name\":\"Only\",\"imageUrls\":[]}]";
        String admin = "[]";
        assertThat(support.mergeFrozenColorMedia(matrix, admin)).contains("Only");
    }

    @Test
    void freezeSwatchesFromStored_blankFrozenName_usesPositionOnly() {
        String frozen = "[{\"name\":\"OldLabel\",\"swatchUrl\":\"https://cdn.example.com/pos.jpg\"}]";
        String current = "[{\"name\":\"Renamed\",\"imageUrls\":[]}]";
        assertThat(support.freezeSwatchesFromStored(current, frozen)).contains("pos.jpg");
    }

    @Test
    void mergeRichColorMetadata_matrixObjectEntriesWithoutRichMatch() {
        String matrix = "[{\"name\":\"NewPrint\",\"imageUrls\":[]}]";
        String rich = "[{\"name\":\"Other\",\"imageUrls\":[\"https://cdn.example.com/o.jpg\"]}]";
        String merged = support.mergeRichColorMetadata(matrix, rich);
        assertThat(merged).contains("NewPrint").doesNotContain("o.jpg");
    }

    @Test
    void coerceUrlList_nullItemsAndSingleString() {
        @SuppressWarnings("unchecked")
        List<String> fromList = ReflectionTestUtils.invokeMethod(
            support,
            "coerceUrlList",
            java.util.Arrays.asList(null, " https://cdn.example.com/a.jpg ")
        );
        assertThat(fromList).containsExactly("https://cdn.example.com/a.jpg");
    }

    @Test
    void coerceMap_stringJsonPath() {
        assertThat(support.coerceMap("{\"Teal\":[\"https://cdn.example.com/t.jpg\"]}")).containsKey("Teal");
    }

    @Test
    void parseGalleryUrls_truncatesBeyondMax() throws Exception {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            many.add("https://cdn.example.com/g" + i + ".jpg");
        }
        JsonNode node = new ObjectMapper().readTree("{\"name\":\"X\",\"imageUrls\":" + new ObjectMapper().writeValueAsString(many) + "}");
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(support, "parseGalleryUrls", node, null);
        assertThat(urls).hasSize(ProductColorImagesSupport.MAX_IMAGES_PER_COLOR);
    }

    @Test
    void writeColorEntries_emitsImageUrlsArrayWhenMultipleGalleries() {
        Product product = new Product();
        support.replaceColorGallery(
            product,
            "Teal",
            List.of("https://cdn.example.com/t1.jpg", "https://cdn.example.com/t2.jpg")
        );
        assertThat(product.getColorsJson()).contains("imageUrls").contains("t1.jpg").contains("t2.jpg");
    }

    @Test
    void serializeStringList_successViaEnsureProductImages() {
        Product product = new Product()
            .imagesJson("[]")
            .colorsJson("[{\"name\":\"Navy\",\"imageUrl\":\"https://cdn.example.com/n.jpg\"}]");
        support.ensureProductImagesFromColorGalleries(product);
        assertThat(product.getImagesJson()).contains("n.jpg");
    }

    @Test
    void writeColorEntries_filtersBlankNamesInStream() {
        @SuppressWarnings("unchecked")
        List<Object> entries = ReflectionTestUtils.invokeMethod(
            support,
            "parseColorEntries",
            "[{\"name\":\"  \",\"imageUrls\":[\"https://cdn.example.com/x.jpg\"]}]"
        );
        String json = ReflectionTestUtils.invokeMethod(support, "writeColorEntries", entries);
        assertThat(json).isEqualTo("[]");
    }
}
