package com.miniandmore.integration.service;

import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.ZohoSetting;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Resolves Zoho OAuth credentials for API calls. When the server is configured via env
 * ({@code ZOHO_CLIENT_ID}, {@code ZOHO_CLIENT_SECRET}, {@code ZOHO_ORG_ID}), those values
 * always win over stale copies in {@code zoho_setting} so token refresh matches the app
 * that issued the refresh token.
 */
@Component
public class ZohoCredentialResolver {

    private final ApplicationProperties applicationProperties;

    public ZohoCredentialResolver(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public boolean isServerConfigured() {
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        return StringUtils.isNoneBlank(oauth.getClientId(), oauth.getClientSecret(), oauth.getOrgId());
    }

    public Resolved resolve(ZohoSetting setting) {
        if (setting == null) {
            throw new ZohoBooksException("Zoho Books is not connected");
        }
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        String dataCenter = StringUtils.isNotBlank(setting.getDataCenter())
            ? ZohoDataCenterHosts.normalize(setting.getDataCenter())
            : ZohoDataCenterHosts.normalize(oauth.getDefaultDataCenter());

        if (isServerConfigured()) {
            return new Resolved(
                oauth.getClientId().trim(),
                oauth.getClientSecret().trim(),
                StringUtils.trimToEmpty(setting.getRefreshToken()),
                oauth.getOrgId().trim(),
                dataCenter
            );
        }

        return new Resolved(
            StringUtils.trimToEmpty(setting.getClientId()),
            StringUtils.trimToEmpty(setting.getClientSecret()),
            StringUtils.trimToEmpty(setting.getRefreshToken()),
            StringUtils.trimToEmpty(setting.getOrgId()),
            dataCenter
        );
    }

    public record Resolved(String clientId, String clientSecret, String refreshToken, String orgId, String dataCenter) {
        public boolean isComplete() {
            return StringUtils.isNoneBlank(clientId, clientSecret, refreshToken, orgId, dataCenter);
        }
    }
}
