package com.miniandmore.app.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.PageVisibility} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PageVisibilityDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 120)
    private String pageKey;

    @NotNull
    private Boolean visible;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPageKey() {
        return pageKey;
    }

    public void setPageKey(String pageKey) {
        this.pageKey = pageKey;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PageVisibilityDTO)) {
            return false;
        }

        PageVisibilityDTO pageVisibilityDTO = (PageVisibilityDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, pageVisibilityDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PageVisibilityDTO{" +
            "id=" + getId() +
            ", pageKey='" + getPageKey() + "'" +
            ", visible='" + getVisible() + "'" +
            "}";
    }
}
