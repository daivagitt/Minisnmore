package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class ZohoBooksClientBenignDeleteBranchTest {

    @ParameterizedTest
    @MethodSource("benignDeleteCases")
    void isBenignItemImagesDeleteFailure(int statusCode, String body, boolean expectedBenign) {
        Boolean benign = ReflectionTestUtils.invokeMethod(
            ZohoBooksClient.class,
            "isBenignItemImagesDeleteFailure",
            statusCode,
            body
        );
        assertThat(benign).isEqualTo(expectedBenign);
    }

    private static Stream<Arguments> benignDeleteCases() {
        return Stream.of(
            Arguments.of(400, "{\"code\":33008}", true),
            Arguments.of(400, "{\"code\": 33008}", true),
            Arguments.of(400, "document file already deleted", true),
            Arguments.of(400, "The file is invalid", true),
            Arguments.of(400, "file not found", true),
            Arguments.of(400, "random validation error", false),
            Arguments.of(404, "document not found", false),
            Arguments.of(500, "document does not exist", false),
            Arguments.of(400, "", false),
            Arguments.of(400, "document missing but no keyword", false)
        );
    }
}
