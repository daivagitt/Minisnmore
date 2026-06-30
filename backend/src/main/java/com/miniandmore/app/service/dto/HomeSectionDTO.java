package com.miniandmore.app.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.HomeSection} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class HomeSectionDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 120)
    private String sectionKey;

    @Size(max = 180)
    private String title;

    @Size(max = 260)
    private String subtitle;

    @NotNull
    private Boolean enabled;

    @NotNull
    @Min(value = 0)
    private Integer sortOrder;

    private String payloadJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSectionKey() {
        return sectionKey;
    }

    public void setSectionKey(String sectionKey) {
        this.sectionKey = sectionKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HomeSectionDTO)) {
            return false;
        }

        HomeSectionDTO homeSectionDTO = (HomeSectionDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, homeSectionDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "HomeSectionDTO{" +
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
