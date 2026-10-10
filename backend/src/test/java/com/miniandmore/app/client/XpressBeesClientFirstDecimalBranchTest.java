package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class XpressBeesClientFirstDecimalBranchTest {

    private XpressBeesClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.XpressBees xb = properties.getXpressbees();
        xb.setBaseUrl("https://api.xpressbees.example");
        xb.setOriginPincode("560001");
        client = new XpressBeesClient(properties, objectMapper);
    }

    @Test
    void firstDecimal_returnsNonPositiveNumericFallback() throws Exception {
        var node = objectMapper.readTree("{\"rate\":0,\"freight_charge\":\"12.50\"}");
        BigDecimal rate = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstDecimal",
            node,
            new String[] { "rate", "freight_charge" }
        );
        assertThat(rate).isEqualByComparingTo("12.50");
    }

    @Test
    void decimalValue_rejectsArrayNodes() throws Exception {
        var arrayNode = objectMapper.readTree("[1,2]");
        BigDecimal parsed = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "decimalValue",
            arrayNode,
            new String[] { "rate" }
        );
        assertThat(parsed).isNull();
    }

    @Test
    void firstPositiveDecimal_parsesNestedObjectField() throws Exception {
        var node = objectMapper.readTree("{\"charges\":{\"rate\":\"45.00\"}}");
        BigDecimal positive = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstPositiveDecimal",
            node.path("charges"),
            new String[] { "rate" }
        );
        assertThat(positive).isEqualByComparingTo("45.00");
    }

    @ParameterizedTest
    @MethodSource("decimalValueTextCases")
    void decimalValue_parsesSanitizedTextOrRejectsInvalid(String json, BigDecimal expected) throws Exception {
        var node = objectMapper.readTree(json);
        BigDecimal parsed = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "decimalValue",
            node,
            new String[] { "rate" }
        );
        if (expected == null) {
            assertThat(parsed).isNull();
        } else {
            assertThat(parsed).isEqualByComparingTo(expected);
        }
    }

    private static Stream<Arguments> decimalValueTextCases() {
        return Stream.of(
            Arguments.of("\"₹1,234.50\"", new BigDecimal("1234.50")),
            Arguments.of("\"\"", null),
            Arguments.of("\"not-a-number\"", null),
            Arguments.of("-12.5", new BigDecimal("-12.5"))
        );
    }

    @Test
    void firstPositiveDecimal_skipsZeroAndNegative() throws Exception {
        var node = objectMapper.readTree("{\"rate\":-5,\"freight\":\"0\",\"amount\":\"8.00\"}");
        BigDecimal positive = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstPositiveDecimal",
            node,
            new String[] { "rate", "freight", "amount" }
        );
        assertThat(positive).isEqualByComparingTo("8.00");
    }

    @Test
    void firstDecimal_nullNode_returnsNull() {
        BigDecimal value = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstDecimal",
            null,
            new String[] { "rate" }
        );
        assertThat(value).isNull();
    }

    @Test
    void firstDecimal_returnsZeroWhenNoPositiveValuesExist() throws Exception {
        var node = objectMapper.readTree("{\"rate\":0,\"freight\":-1}");
        BigDecimal value = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstDecimal",
            node,
            new String[] { "rate", "freight" }
        );
        assertThat(value).isEqualByComparingTo("0");
    }

    @Test
    void decimalValue_recursesIntoNestedObjectForPositiveRate() throws Exception {
        var nested = objectMapper.readTree("{\"rate\":\"15.25\",\"freight\":0}");
        BigDecimal parsed = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "decimalValue",
            nested,
            new String[] { "rate", "freight" }
        );
        assertThat(parsed).isEqualByComparingTo("15.25");
    }

    @Test
    void firstPositiveDecimal_missingNode_returnsNull() {
        BigDecimal positive = ReflectionTestUtils.invokeMethod(
            XpressBeesClient.class,
            "firstPositiveDecimal",
            objectMapper.missingNode(),
            new String[] { "rate" }
        );
        assertThat(positive).isNull();
    }
}
