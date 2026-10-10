package com.miniandmore.gateway.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;

class ExceptionTranslatorCustomizeProblemMatrixTest {

    private final ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
    private final MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test").build());

    @Test
    void customizeProblem_fillsMissingStatusTypeTitleDetailAndProperties() {
        ProblemDetailWithCause problem = ProblemDetailWithCauseBuilder.instance().withStatus(0).build();
        problem.setType(URI.create("about:blank"));
        problem.setTitle(null);
        problem.setDetail(null);

        ProblemDetailWithCause customized = ReflectionTestUtils.invokeMethod(
            translator,
            "customizeProblem",
            problem,
            new RuntimeException("boom"),
            exchange
        );

        assertThat(customized.getStatus()).isGreaterThan(0);
        assertThat(customized.getType()).isNotNull();
        assertThat(customized.getTitle()).isNotBlank();
        assertThat(customized.getDetail()).isNotBlank();
        assertThat(customized.getProperties()).containsKeys("message", "path");
    }

    @Test
    void customizeProblem_keepsExistingTitleDetailAndPropertyKeys() {
        ProblemDetailWithCause problem = ProblemDetailWithCauseBuilder.instance().withStatus(HttpStatus.BAD_REQUEST.value()).build();
        problem.setType(URI.create("https://example.test/problem"));
        problem.setTitle("Existing title");
        problem.setDetail("Existing detail");
        problem.setProperty("message", "keep-message");
        problem.setProperty("path", "keep-path");

        ProblemDetailWithCause customized = ReflectionTestUtils.invokeMethod(
            translator,
            "customizeProblem",
            problem,
            new IllegalArgumentException("ignored"),
            exchange
        );

        assertThat(customized.getType()).isEqualTo(URI.create("https://example.test/problem"));
        assertThat(customized.getDetail()).isEqualTo("Existing detail");
        assertThat(customized.getProperties().get("message")).isEqualTo("keep-message");
        assertThat(customized.getProperties().get("path")).isEqualTo("keep-path");
    }

    @Test
    void customizeProblem_replacesMismatchedTitle() {
        ProblemDetailWithCause problem = ProblemDetailWithCauseBuilder.instance().withStatus(500).build();
        problem.setTitle("wrong");

        ProblemDetailWithCause customized = ReflectionTestUtils.invokeMethod(
            translator,
            "customizeProblem",
            problem,
            new RuntimeException("x"),
            exchange
        );

        assertThat(customized.getTitle()).isNotEqualTo("wrong");
    }
}
