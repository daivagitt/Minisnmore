package com.miniandmore.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * A HomeSection.
 */
@Entity
@Table(name = "home_section")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class HomeSection implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 120)
    @Column(name = "section_key", length = 120, nullable = false, unique = true)
    private String sectionKey;

    @Size(max = 180)
    @Column(name = "title", length = 180)
    private String title;

    @Size(max = 260)
    @Column(name = "subtitle", length = 260)
    private String subtitle;

    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @NotNull
    @Min(value = 0)
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public HomeSection id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSectionKey() {
        return this.sectionKey;
    }

    public HomeSection sectionKey(String sectionKey) {
        this.setSectionKey(sectionKey);
        return this;
    }

    public void setSectionKey(String sectionKey) {
        this.sectionKey = sectionKey;
    }

    public String getTitle() {
        return this.title;
    }

    public HomeSection title(String title) {
        this.setTitle(title);
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return this.subtitle;
    }

    public HomeSection subtitle(String subtitle) {
        this.setSubtitle(subtitle);
        return this;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public HomeSection enabled(Boolean enabled) {
        this.setEnabled(enabled);
        return this;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return this.sortOrder;
    }

    public HomeSection sortOrder(Integer sortOrder) {
        this.setSortOrder(sortOrder);
        return this;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getPayloadJson() {
        return this.payloadJson;
    }

    public HomeSection payloadJson(String payloadJson) {
        this.setPayloadJson(payloadJson);
        return this;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HomeSection)) {
            return false;
        }
        return getId() != null && getId().equals(((HomeSection) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "HomeSection{" +
            "id=" + getId() +
            ", sectionKey='" + getSectionKey() + "'" +
            ", title='" + getTitle() + "'" +
            ", subtitle='" + getSubtitle() + "'" +
            ", enabled='" + getEnabled() + "'" +
            ", sortOrder=" + getSortOrder() +
            ", payloadJson='" + getPayloadJson() + "'" +
            "}";
    }
}
