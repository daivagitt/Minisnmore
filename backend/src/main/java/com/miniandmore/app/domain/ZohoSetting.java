package com.miniandmore.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * A ZohoSetting.
 */
@Entity
@Table(name = "zoho_setting")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ZohoSetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Size(max = 120)
    @Column(name = "org_id", length = 120)
    private String orgId;

    @Size(max = 220)
    @Column(name = "client_id", length = 220)
    private String clientId;

    @Size(max = 500)
    @Column(name = "client_secret", length = 500)
    private String clientSecret;

    @Size(max = 800)
    @Column(name = "refresh_token", length = 800)
    private String refreshToken;

    @Size(max = 40)
    @Column(name = "data_center", length = 40)
    private String dataCenter;

    @NotNull
    @Column(name = "connected", nullable = false)
    private Boolean connected;

    @NotNull
    @Column(name = "pull_products_auto_create_category", nullable = false)
    private Boolean pullProductsAutoCreateCategory = false;

    @Column(name = "last_product_pull_at")
    private Instant lastProductPullAt;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ZohoSetting id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrgId() {
        return this.orgId;
    }

    public ZohoSetting orgId(String orgId) {
        this.setOrgId(orgId);
        return this;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getClientId() {
        return this.clientId;
    }

    public ZohoSetting clientId(String clientId) {
        this.setClientId(clientId);
        return this;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return this.clientSecret;
    }

    public ZohoSetting clientSecret(String clientSecret) {
        this.setClientSecret(clientSecret);
        return this;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRefreshToken() {
        return this.refreshToken;
    }

    public ZohoSetting refreshToken(String refreshToken) {
        this.setRefreshToken(refreshToken);
        return this;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getDataCenter() {
        return this.dataCenter;
    }

    public ZohoSetting dataCenter(String dataCenter) {
        this.setDataCenter(dataCenter);
        return this;
    }

    public void setDataCenter(String dataCenter) {
        this.dataCenter = dataCenter;
    }

    public Boolean getConnected() {
        return this.connected;
    }

    public ZohoSetting connected(Boolean connected) {
        this.setConnected(connected);
        return this;
    }

    public void setConnected(Boolean connected) {
        this.connected = connected;
    }

    public Boolean getPullProductsAutoCreateCategory() {
        return this.pullProductsAutoCreateCategory;
    }

    public ZohoSetting pullProductsAutoCreateCategory(Boolean pullProductsAutoCreateCategory) {
        this.setPullProductsAutoCreateCategory(pullProductsAutoCreateCategory);
        return this;
    }

    public void setPullProductsAutoCreateCategory(Boolean pullProductsAutoCreateCategory) {
        this.pullProductsAutoCreateCategory = pullProductsAutoCreateCategory;
    }

    public Instant getLastProductPullAt() {
        return this.lastProductPullAt;
    }

    public ZohoSetting lastProductPullAt(Instant lastProductPullAt) {
        this.setLastProductPullAt(lastProductPullAt);
        return this;
    }

    public void setLastProductPullAt(Instant lastProductPullAt) {
        this.lastProductPullAt = lastProductPullAt;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ZohoSetting)) {
            return false;
        }
        return getId() != null && getId().equals(((ZohoSetting) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ZohoSetting{" +
            "id=" + getId() +
            ", orgId='" + getOrgId() + "'" +
            ", clientId='" + getClientId() + "'" +
            ", clientSecret='[REDACTED]'" +
            ", refreshToken='[REDACTED]'" +
            ", dataCenter='" + getDataCenter() + "'" +
            ", connected='" + getConnected() + "'" +
            ", pullProductsAutoCreateCategory='" + getPullProductsAutoCreateCategory() + "'" +
            "}";
    }
}
