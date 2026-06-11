package com.miniandmore.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * A PageVisibility.
 */
@Entity
@Table(name = "page_visibility")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PageVisibility implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 120)
    @Column(name = "page_key", length = 120, nullable = false, unique = true)
    private String pageKey;

    @NotNull
    @Column(name = "visible", nullable = false)
    private Boolean visible;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public PageVisibility id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPageKey() {
        return this.pageKey;
    }

    public PageVisibility pageKey(String pageKey) {
        this.setPageKey(pageKey);
        return this;
    }

    public void setPageKey(String pageKey) {
        this.pageKey = pageKey;
    }

    public Boolean getVisible() {
        return this.visible;
    }

    public PageVisibility visible(Boolean visible) {
        this.setVisible(visible);
        return this;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PageVisibility)) {
            return false;
        }
        return getId() != null && getId().equals(((PageVisibility) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PageVisibility{" +
            "id=" + getId() +
            ", pageKey='" + getPageKey() + "'" +
            ", visible='" + getVisible() + "'" +
            "}";
    }
}
