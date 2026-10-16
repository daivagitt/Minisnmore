package com.miniandmore.gateway.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.gateway.service.EmailAlreadyUsedException;
import com.miniandmore.gateway.service.InvalidPasswordException;
import com.miniandmore.gateway.service.UsernameAlreadyUsedException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.ConcurrencyFailureException;
import java.net.URI;
import org.springframework.web.server.ServerWebExchange;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause;
import tech.jhipster.web.util.HeaderUtil;

class ExceptionTranslatorUnitTest {

    private final MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test").build());

    @Test
    void wrapAndCustomizeProblem_mapsServiceExceptions() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        assertThat(translator.wrapAndCustomizeProblem(new UsernameAlreadyUsedException(), exchange).getTitle()).isNotBlank();
        assertThat(translator.wrapAndCustomizeProblem(new EmailAlreadyUsedException(), exchange).getTitle()).isNotBlank();
        assertThat(translator.wrapAndCustomizeProblem(new InvalidPasswordException(), exchange).getTitle()).isNotBlank();
    }

    @Test
    void wrapAndCustomizeProblem_usernameNotFoundIsUnauthorized() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        ProblemDetailWithCause problem = translator.wrapAndCustomizeProblem(new UsernameNotFoundException("missing"), exchange);
        assertThat(problem.getStatus()).isEqualTo(401);
    }

    @Test
    void wrapAndCustomizeProblem_prodProfileHidesInternalDetails() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_PRODUCTION);
        ExceptionTranslator translator = new ExceptionTranslator(env);

        ProblemDetailWithCause dataAccess = translator.wrapAndCustomizeProblem(
            new DataAccessException("failure") {},
            exchange
        );
        assertThat(dataAccess.getDetail()).isEqualTo("Failure during data access");

        ProblemDetailWithCause conversion = translator.wrapAndCustomizeProblem(new HttpMessageConversionException("failed"), exchange);
        assertThat(conversion.getDetail()).isEqualTo("Unable to convert http message");

        ProblemDetailWithCause runtime = translator.wrapAndCustomizeProblem(new RuntimeException("com.miniandmore.gateway.boom"), exchange);
        assertThat(runtime.getDetail()).isEqualTo("Unexpected runtime exception");
    }

    @Test
    void wrapAndCustomizeProblem_badRequestAlertBuildsHeaders() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        ReflectionTestUtils.setField(translator, "applicationName", "gateway");
        BadRequestAlertException ex = new BadRequestAlertException("Invalid", "user", "invalid");
        assertThat(translator.wrapAndCustomizeProblem(ex, exchange).getStatus()).isEqualTo(400);
    }

    @Test
    void handleExceptionInternal_committedResponse_rethrows() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        MockServerWebExchange committedExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test").build());
        committedExchange.getResponse().setComplete();
        RuntimeException ex = new RuntimeException("boom");

        reactor.test.StepVerifier.create(
            translator.handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, committedExchange)
        )
            .expectErrorMatches(error -> error == ex)
            .verify();
    }

    @Test
    void wrapAndCustomizeProblem_usesCauseMessageWhenPresent() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        RuntimeException ex = new RuntimeException("outer", new IllegalStateException("inner cause"));
        ProblemDetailWithCause problem = translator.wrapAndCustomizeProblem(ex, exchange);
        assertThat(problem.getDetail()).isEqualTo("inner cause");
    }

    @Test
    void wrapAndCustomizeProblem_usesCauseDetailInDev() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        RuntimeException ex = new RuntimeException("outer", new IllegalStateException("inner"));

        assertThat(translator.wrapAndCustomizeProblem(ex, exchange).getDetail()).isEqualTo("inner");
    }

    @Test
    void handleAnyException_returnsProblemResponse() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        RuntimeException ex = new RuntimeException("failure");

        reactor.test.StepVerifier.create(translator.handleAnyException(ex, exchange))
            .assertNext(response -> assertThat(response.getStatusCode().value()).isEqualTo(500))
            .verifyComplete();
    }

    @Test
    void getProblemDetailWithCause_authenticationUsesGenericMessage() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        ProblemDetailWithCause problem = ReflectionTestUtils.invokeMethod(
            translator,
            "getProblemDetailWithCause",
            new org.springframework.security.authentication.BadCredentialsException("secret")
        );
        assertThat(problem.getDetail()).isEqualTo("Invalid credentials");
    }

    @Test
    void getProblemDetailWithCause_notActivatedUsesExplicitMessage() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        ProblemDetailWithCause problem = ReflectionTestUtils.invokeMethod(
            translator,
            "getProblemDetailWithCause",
            new com.miniandmore.gateway.security.UserNotActivatedException("User test was not activated")
        );
        assertThat(problem.getDetail()).isEqualTo("User account is not activated");
    }

    @Test
    void handleAnyException_criticalError_rethrowsWithoutWrapping() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        OutOfMemoryError error = new OutOfMemoryError("heap exhausted");

        reactor.test.StepVerifier.create(translator.handleAnyException(error, exchange))
            .expectErrorMatches(thrown -> thrown == error)
            .verify();
    }

    @Test
    void wrapAndCustomizeProblem_concurrencyFailureUsesMappedMessageKey() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        ProblemDetailWithCause problem = translator.wrapAndCustomizeProblem(new ConcurrencyFailureException("conflict"), exchange);
        assertThat(problem.getProperties()).containsEntry("message", ErrorConstants.ERR_CONCURRENCY_FAILURE);
    }

    @Test
    void getPathValue_nullExchange_returnsAboutBlank() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        URI path = ReflectionTestUtils.invokeMethod(translator, "getPathValue", (ServerWebExchange) null);
        assertThat(path).hasToString("about:blank");
    }

    @Test
    void updateContentType_nullHeaders_setsProblemJson() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        HttpHeaders headers = ReflectionTestUtils.invokeMethod(translator, "updateContentType", (HttpHeaders) null);
        assertThat(headers.getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void wrapAndCustomizeProblem_accessDeniedIsForbidden() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        ProblemDetailWithCause problem = translator.wrapAndCustomizeProblem(new org.springframework.security.access.AccessDeniedException("nope"), exchange);
        assertThat(problem.getStatus()).isEqualTo(403);
    }

    @Test
    void wrapAndCustomizeProblem_webExchangeBindException_includesFieldErrors() throws Exception {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        org.springframework.validation.BeanPropertyBindingResult bindingResult =
            new org.springframework.validation.BeanPropertyBindingResult(
                new ExceptionTranslatorTestController.TestDTO(),
                "testDTO"
            );
        bindingResult.rejectValue("test", "NotNull", "must not be blank");
        org.springframework.core.MethodParameter parameter = new org.springframework.core.MethodParameter(
            ExceptionTranslatorTestController.class.getDeclaredMethod(
                "methodArgument",
                ExceptionTranslatorTestController.TestDTO.class
            ),
            0
        );
        org.springframework.web.bind.support.WebExchangeBindException bindException =
            new org.springframework.web.bind.support.WebExchangeBindException(parameter, bindingResult);

        ProblemDetailWithCause problem = translator.wrapAndCustomizeProblem(bindException, exchange);

        assertThat(problem.getProperties()).containsEntry("message", ErrorConstants.ERR_VALIDATION);
        assertThat(problem.getProperties().get("fieldErrors")).asList().isNotEmpty();
    }

    @Test
    void wrapAndCustomizeProblem_badRequestAlertBuildsFailureAlertHeaders() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        ReflectionTestUtils.setField(translator, "applicationName", "gateway");
        BadRequestAlertException ex = new BadRequestAlertException("Invalid payload", "orderReturn", "invalidStatus");

        HttpHeaders headers = ReflectionTestUtils.invokeMethod(translator, "buildHeaders", ex);
        HttpHeaders expected =
            HeaderUtil.createFailureAlert("gateway", true, "orderReturn", "invalidStatus", "Invalid payload");

        assertThat(headers).isNotNull();
        assertThat(headers.toSingleValueMap()).isEqualTo(expected.toSingleValueMap());
    }

    @Test
    void getCustomizedTitle_badRequestAlertUsesBodyTitle() {
        ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());
        BadRequestAlertException ex = new BadRequestAlertException("Bad", "entity", "errorKey");
        String title = ReflectionTestUtils.invokeMethod(translator, "getCustomizedTitle", ex);
        assertThat(title).isNotBlank();
    }
}
