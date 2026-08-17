package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterProperties;

class CatalogProductionValidatorTest {

    @Test
    void nonProdProfile_doesNotValidate() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.setInternalApiKey("secret");

        new CatalogProductionValidator(environment, jhipsterProperties("custom-secret"), applicationProperties, "rzp_live", "secret", false);
    }

    @Test
    void prodWithSecretSamplesProfile_throws() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "secret-samples");
        ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.setInternalApiKey("secret");

        assertThatThrownBy(
            () ->
                new CatalogProductionValidator(
                    environment,
                    jhipsterProperties("custom-secret"),
                    applicationProperties,
                    "rzp_live",
                    "secret",
                    false
                )
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodWithoutInternalApiKey_throws() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ApplicationProperties applicationProperties = new ApplicationProperties();

        assertThatThrownBy(
            () ->
                new CatalogProductionValidator(
                    environment,
                    jhipsterProperties("custom-secret"),
                    applicationProperties,
                    "rzp_live",
                    "secret",
                    false
                )
        ).isInstanceOf(IllegalStateException.class);
    }

  private static JHipsterProperties jhipsterProperties(String secret) {
    JHipsterProperties properties = new JHipsterProperties();
    properties.getSecurity().getAuthentication().getJwt().setBase64Secret(secret);
    return properties;
  }
}
