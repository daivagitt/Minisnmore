package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.ZohoSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZohoCredentialResolverTest {

    private ApplicationProperties applicationProperties;
    private ZohoCredentialResolver resolver;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        resolver = new ZohoCredentialResolver(applicationProperties);
    }

    @Test
    void isServerConfigured_whenAllPresent() {
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        oauth.setClientId("id");
        oauth.setClientSecret("secret");
        oauth.setOrgId("org");
        assertThat(resolver.isServerConfigured()).isTrue();
    }

    @Test
    void isServerConfigured_falseWhenIncomplete() {
        assertThat(resolver.isServerConfigured()).isFalse();
    }

    @Test
    void resolve_nullSettingThrows() {
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(ZohoBooksException.class).hasMessageContaining("not connected");
    }

    @Test
    void resolve_prefersServerConfig() {
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        oauth.setClientId("srv-id");
        oauth.setClientSecret("srv-secret");
        oauth.setOrgId("srv-org");
        oauth.setDefaultDataCenter("eu");

        ZohoSetting setting = new ZohoSetting();
        setting.setClientId("db-id");
        setting.setRefreshToken("refresh");
        setting.setDataCenter("in");

        ZohoCredentialResolver.Resolved resolved = resolver.resolve(setting);

        assertThat(resolved.clientId()).isEqualTo("srv-id");
        assertThat(resolved.clientSecret()).isEqualTo("srv-secret");
        assertThat(resolved.orgId()).isEqualTo("srv-org");
        assertThat(resolved.dataCenter()).isEqualTo("in");
        assertThat(resolved.isComplete()).isTrue();
    }

    @Test
    void resolve_fromDatabaseWhenServerNotConfigured() {
        ZohoSetting setting = new ZohoSetting();
        setting.setClientId("db-id");
        setting.setClientSecret("db-secret");
        setting.setRefreshToken("refresh");
        setting.setOrgId("db-org");
        setting.setDataCenter("com");

        ZohoCredentialResolver.Resolved resolved = resolver.resolve(setting);

        assertThat(resolved.clientId()).isEqualTo("db-id");
        assertThat(resolved.dataCenter()).isEqualTo("com");
        assertThat(resolved.isComplete()).isTrue();
    }
}
