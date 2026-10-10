package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Targets JaCoCo branch gaps in package-private/static helpers on {@link XpressBeesClient}.
 */
class XpressBeesClientPrivateHelpersBranchTest {

    private static final String[] RATE_FIELDS = { "rate", "freight_charge", "amount" };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void firstDecimal_returnsEarlyWhenPositiveFieldExists() throws Exception {
        JsonNode node = objectMapper.readTree("{\"rate\":\"99.00\",\"freight_charge\":\"0\"}");
        BigDecimal value = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "firstDecimal", node, RATE_FIELDS);
        assertThat(value).isEqualByComparingTo("99.00");
    }

    @Test
    void firstDecimal_nullLiteralNode_returnsNull() throws Exception {
        JsonNode nullNode = objectMapper.readTree("null");
        BigDecimal value = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "firstDecimal", nullNode, RATE_FIELDS);
        assertThat(value).isNull();
    }

    @Test
    void firstDecimal_missingNode_returnsNull() {
        BigDecimal value = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstDecimal",
            objectMapper.missingNode(),
            RATE_FIELDS
        );
        assertThat(value).isNull();
    }

    @Test
    void firstDecimal_noMatchingFields_returnsNull() throws Exception {
        JsonNode node = objectMapper.readTree("{\"other\":1}");
        BigDecimal value = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "firstDecimal", node, RATE_FIELDS);
        assertThat(value).isNull();
    }

    @Test
    void firstDecimal_fallbackReturnsNegativeWhenNoPositiveExists() throws Exception {
        JsonNode node = objectMapper.readTree("{\"rate\":-3.25}");
        BigDecimal value = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "firstDecimal", node, RATE_FIELDS);
        assertThat(value).isEqualByComparingTo("-3.25");
    }

    @Test
    void firstPositiveDecimal_nullLiteralNode_returnsNull() throws Exception {
        JsonNode nullNode = objectMapper.readTree("null");
        BigDecimal value = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstPositiveDecimal",
            nullNode,
            RATE_FIELDS
        );
        assertThat(value).isNull();
    }

    @Test
    void firstPositiveDecimal_onlyNonPositiveValues_returnsNull() throws Exception {
        JsonNode node = objectMapper.readTree("{\"rate\":0,\"freight_charge\":-1}");
        BigDecimal value = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstPositiveDecimal",
            node,
            RATE_FIELDS
        );
        assertThat(value).isNull();
    }

    @Test
    void decimalValue_missingNode_returnsNull() {
        BigDecimal parsed = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "decimalValue",
            objectMapper.missingNode(),
            RATE_FIELDS
        );
        assertThat(parsed).isNull();
    }

    @Test
    void decimalValue_objectWithoutPositiveRate_returnsNull() throws Exception {
        JsonNode nested = objectMapper.readTree("{\"rate\":0,\"freight\":-2}");
        BigDecimal parsed = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "decimalValue", nested, RATE_FIELDS);
        assertThat(parsed).isNull();
    }

    @Test
    void decimalValue_numericNode_parsesIntegralNumber() throws Exception {
        JsonNode node = objectMapper.readTree("42");
        BigDecimal parsed = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "decimalValue", node, RATE_FIELDS);
        assertThat(parsed).isEqualByComparingTo("42");
    }

    @Test
    void decimalValue_nullLiteralNode_returnsNull() throws Exception {
        JsonNode nullNode = objectMapper.readTree("null");
        BigDecimal parsed = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "decimalValue", nullNode, RATE_FIELDS);
        assertThat(parsed).isNull();
    }

    @Test
    void decimalValue_nullReference_returnsNull() {
        BigDecimal parsed = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "decimalValue", null, RATE_FIELDS);
        assertThat(parsed).isNull();
    }

    @Test
    void firstPositiveInteger_usesFallbackWhenNullOrNonPositive() {
        int fromNull = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "firstPositive", null, 10);
        int fromZero = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "firstPositive", 0, 10);
        int fromValue = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "firstPositive", 25, 10);
        assertThat(fromNull).isEqualTo(10);
        assertThat(fromZero).isEqualTo(10);
        assertThat(fromValue).isEqualTo(25);
    }

    @Test
    void firstPositiveAmount_nullPreferredWithZeroFallback_returnsZero() {
        BigDecimal value = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstPositiveAmount",
            null,
            BigDecimal.ZERO
        );
        assertThat(value).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @MethodSource("joinAddressCases")
    void joinAddress_combinesOrTrims(String line1, String line2, String expected) {
        String joined = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "joinAddress", line1, line2);
        assertThat(joined).isEqualTo(expected);
    }

    private static Stream<Arguments> joinAddressCases() {
        return Stream.of(
            Arguments.of("Road 1", "Block B", "Road 1 Block B"),
            Arguments.of("  Road 1  ", "Suite", "Road 1   Suite"),
            Arguments.of(null, null, ""),
            Arguments.of("Only line", null, "Only line")
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "5123456789", "1234567890" })
    void indianMobile10_rejectsBlankOrInvalid(String raw) {
        assertThat(XpressBeesClient.indianMobile10(raw)).isNull();
    }

    @Test
    void indianMobile10_extraLeadingDigits_usesLastTenValidMobile() {
        assertThat(XpressBeesClient.indianMobile10("009876543210")).isEqualTo("9876543210");
    }

    @Test
    void courierRate_readsTopLevelPositiveRate() throws Exception {
        JsonNode root = objectMapper.readTree("{\"freight_charge\":\"22.00\"}");
        assertThat(XpressBeesClient.courierRate(root)).isEqualByComparingTo("22.00");
    }

    @Test
    void courierRate_skipsNonObjectNestedValues() throws Exception {
        JsonNode root = objectMapper.readTree(
            """
            {"note":"text","count":3,"meta":{"freight_charge":"11.50"}}
            """
        );
        assertThat(XpressBeesClient.courierRate(root)).isEqualByComparingTo("11.50");
    }

    @Test
    void courierRate_nestedObjectsWithoutPositive_fallsBackToZeroRate() throws Exception {
        JsonNode root = objectMapper.readTree(
            """
            {"meta":{"freight_charge":0},"shipping_charge":"0","amount":"7.75"}
            """
        );
        assertThat(XpressBeesClient.courierRate(root)).isEqualByComparingTo("7.75");
    }

    @Test
    void courierRate_nestedScanContinuesUntilMatch() throws Exception {
        JsonNode root = objectMapper.readTree(
            """
            {"wrapper":{"freight_charge":0},"details":{"total_charges":"33.00"}}
            """
        );
        assertThat(XpressBeesClient.courierRate(root)).isEqualByComparingTo("33.00");
    }

    @Test
    void courierRate_noRatesAnywhere_returnsNull() throws Exception {
        JsonNode root = objectMapper.readTree("{\"note\":\"none\",\"meta\":{\"other\":1}}");
        assertThat(XpressBeesClient.courierRate(root)).isNull();
    }

    @Test
    void courierRate_skipsNullAndTextNestedEntries() throws Exception {
        JsonNode root = objectMapper.readTree(
            """
            {"label":"flat","empty":null,"nested":{"freight_charge":"9.99"}}
            """
        );
        assertThat(XpressBeesClient.courierRate(root)).isEqualByComparingTo("9.99");
    }

    @Test
    void firstPositiveAmount_prefersPositivePreferredOverFallback() {
        BigDecimal value = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstPositiveAmount",
            new BigDecimal("50.00"),
            new BigDecimal("10.00")
        );
        assertThat(value).isEqualByComparingTo("50.00");
    }

    @Test
    void firstPositiveAmount_bothNull_returnsNull() {
        BigDecimal value = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstPositiveAmount",
            null,
            null
        );
        assertThat(value).isNull();
    }

    @Test
    void text_missingField_returnsEmptyString() {
        ObjectNode node = objectMapper.createObjectNode();
        String value = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "text", node, "missing");
        assertThat(value).isEmpty();
    }

    @Test
    void text_nullJsonField_returnsEmptyString() throws Exception {
        JsonNode node = objectMapper.readTree("{\"field\":null}");
        String value = ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "text", node, "field");
        assertThat(value).isEmpty();
    }

    @Test
    void truncateProviderOrderNumber_nonPositiveMaxLen_returnsFullValue() {
        assertThat(XpressBeesClient.truncateProviderOrderNumber("ABCDEF", 0)).isEqualTo("ABCDEF");
        assertThat(XpressBeesClient.truncateProviderOrderNumber("SHORT", 20)).isEqualTo("SHORT");
    }

    @Test
    void writeAddress_omitsOptionalEmailAndLine2WhenBlank() {
        ObjectNode node = objectMapper.createObjectNode();
        XpressBeesClient.ReversePickupAddress address = new XpressBeesClient.ReversePickupAddress(
            "Jane",
            "9876543210",
            "  ",
            "Line 1",
            "",
            "City",
            "State",
            "560001"
        );
        ReflectionTestUtils.invokeMethod(XpressBeesClient.class, "writeAddress", node, address);
        assertThat(node.has("email")).isFalse();
        assertThat(node.has("address_line2")).isFalse();
        assertThat(node.path("phone").asText()).isEqualTo("9876543210");
    }
}
