package com.miniandmore.integration.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class IntegrationProductionValidatorTest {

    @Mock
    private Environment environment;

    @Test
    void nonProdProfile_noValidation() {
        when(environment.getActiveProfiles()).thenReturn(new String[] { "dev" });
        ApplicationProperties props = new ApplicationProperties();
        assertThatCode(() -> new IntegrationProductionValidator(environment, props)).doesNotThrowAnyException();
    }

    @Test
    void prod_requiresInternalApiKey() {
        when(environment.getActiveProfiles()).thenReturn(new String[] { "prod" });
        ApplicationProperties props = new ApplicationProperties();
        assertThatThrownBy(() -> new IntegrationProductionValidator(environment, props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("internal-api-key");
    }

    @Test
    void prod_rejectsHttpAndLocalhost() {
        when(environment.getActiveProfiles()).thenReturn(new String[] { "prod" });
        ApplicationProperties props = new ApplicationProperties();
        props.setInternalApiKey("secret");
        ApplicationProperties.Zoho.OAuth oauth = props.getZoho().getOauth();
        oauth.setHomepageUrl("http://localhost:5173");
        assertThatThrownBy(() -> new IntegrationProductionValidator(environment, props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("https");
    }

    @Test
    void prod_acceptsHttpsOrigins() {
        when(environment.getActiveProfiles()).thenReturn(new String[] { "prod" });
        ApplicationProperties props = new ApplicationProperties();
        props.setInternalApiKey("secret");
        ApplicationProperties.Zoho.OAuth oauth = props.getZoho().getOauth();
        oauth.setHomepageUrl("https://shop.example.com");
        oauth.setRedirectUris(List.of("https://shop.example.com/admin/zoho"));
        oauth.setJavascriptDomains(List.of("https://shop.example.com"));
        assertThatCode(() -> new IntegrationProductionValidator(environment, props)).doesNotThrowAnyException();
    }
}
