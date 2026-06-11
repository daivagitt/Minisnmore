package com.miniandmore.integration.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.integration.domain.ZohoSetting} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ZohoSettingDTO implements Serializable {

    private Long id;

    @Size(max = 120)
    private String orgId;

    @Size(max = 220)
    private String clientId;

    @Size(max = 500)
    private String clientSecret;

    @Size(max = 800)
    private String refreshToken;

    @Size(max = 40)
    private String dataCenter;

    @NotNull
    private Boolean connected;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getDataCenter() {
        return dataCenter;
    }

    public void setDataCenter(String dataCenter) {
        this.dataCenter = dataCenter;
    }

    public Boolean getConnected() {
        return connected;
    }

    public void setConnected(Boolean connected) {
        this.connected = connected;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ZohoSettingDTO)) {
            return false;
        }

        ZohoSettingDTO zohoSettingDTO = (ZohoSettingDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, zohoSettingDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ZohoSettingDTO{" +
            "id=" + getId() +
            ", orgId='" + getOrgId() + "'" +
            ", clientId='" + getClientId() + "'" +
            ", clientSecret='[REDACTED]'" +
            ", refreshToken='[REDACTED]'" +
            ", dataCenter='" + getDataCenter() + "'" +
            ", connected='" + getConnected() + "'" +
            "}";
    }
}
