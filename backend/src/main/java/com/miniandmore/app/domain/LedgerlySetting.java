package com.miniandmore.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "ledgerly_setting")
public class LedgerlySetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Size(max = 500)
    @Column(name = "api_base_url", length = 500)
    private String apiBaseUrl;

    @Size(max = 500)
    @Column(name = "api_token", length = 500)
    private String apiToken;

    @Size(max = 120)
    @Column(name = "org_id", length = 120)
    private String orgId;

    @NotNull
    @Column(name = "connected", nullable = false)
    private Boolean connected = false;

    @NotNull
    @Column(name = "pull_products_auto_create_category", nullable = false)
    private Boolean pullProductsAutoCreateCategory = false;

    @Size(max = 200)
    @Column(name = "webhook_secret", length = 200)
    private String webhookSecret;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public Boolean getConnected() {
        return connected;
    }

    public void setConnected(Boolean connected) {
        this.connected = connected;
    }

    public Boolean getPullProductsAutoCreateCategory() {
        return pullProductsAutoCreateCategory;
    }

    public void setPullProductsAutoCreateCategory(Boolean pullProductsAutoCreateCategory) {
        this.pullProductsAutoCreateCategory = pullProductsAutoCreateCategory;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerlySetting)) {
            return false;
        }
        return getId() != null && getId().equals(((LedgerlySetting) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
