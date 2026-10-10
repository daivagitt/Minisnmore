package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterProperties;

class CatalogProductionValidatorProdMatrixTest {

    private static final String SAMPLE_JWT =
        "NWNhYWRmODZlZDAzNzg4NGY2NTA0MzAyZTdjNjI2ZjYzYWI2YmQ3Yjk4ZDA5NGVkODNhYWY2ZmExOTJmZjA3YmJhNDdiYTYxMTdjZmM2ZWUyMmMwMzQyMTY1NDBjNTc5ODg4MjNkZjExODFmZjUwNDdlNjVhOTlhMWE5MjRjYTU=";

    @Test
    void prodWithSampleJwtSecret_throws() {
        assertThatThrownBy(() -> validator("prod", SAMPLE_JWT, "internal-key", "rzp_live", "secret", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sample JWT");
    }

    @Test
    void prodWithTrimmedSampleJwtSecret_throws() {
        assertThatThrownBy(() -> validator("prod", "  " + SAMPLE_JWT + "  ", "internal-key", "rzp_live", "secret", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sample JWT");
    }

    @Test
    void prodWithBlankJwtSecret_continuesToLaterChecks() {
        assertThatThrownBy(() -> validator("prod", "   ", "internal-key", "DEMO", "", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("razorpay");
    }

    @Test
    void prodWithPaymentDemo_skipsRazorpayCredentialCheck() {
        assertThatCode(() -> validator("prod", "custom-secret", "internal-key", "DEMO", "", true)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({ "DEMO,secret", "demo,secret", "rzp_live,''", "rzp_live,'   '" })
    void prodWithoutDemo_rejectsMissingRazorpayCredentials(String keyId, String keySecret) {
        assertThatThrownBy(() -> validator("prod", "custom-secret", "internal-key", keyId, keySecret, false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("razorpay");
    }

    @Test
    void prodWithLiveRazorpayCredentials_succeeds() {
        assertThatCode(() -> validator("prod", "custom-secret", "internal-key", "rzp_live", "live-secret", false))
            .doesNotThrowAnyException();
    }

    private static CatalogProductionValidator validator(
        String profile,
        String jwtSecret,
        String internalKey,
        String razorpayKeyId,
        String razorpayKeySecret,
        boolean paymentDemo
    ) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.setInternalApiKey(internalKey);
        return new CatalogProductionValidator(
            environment,
            jhipsterProperties(jwtSecret),
            applicationProperties,
            razorpayKeyId,
            razorpayKeySecret,
            paymentDemo
        );
    }

    private static JHipsterProperties jhipsterProperties(String secret) {
        JHipsterProperties properties = new JHipsterProperties();
        properties.getSecurity().getAuthentication().getJwt().setBase64Secret(secret);
        return properties;
    }
}
