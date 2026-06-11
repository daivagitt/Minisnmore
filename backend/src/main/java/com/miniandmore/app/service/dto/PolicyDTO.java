package com.miniandmore.app.service.dto;

import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.Policy} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PolicyDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 160)
    private String slug;

    @NotNull
    @Size(min = 1, max = 220)
    private String title;

    private String body;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PolicyDTO)) {
            return false;
        }

        PolicyDTO policyDTO = (PolicyDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, policyDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PolicyDTO{" +
            "id=" + getId() +
            ", slug='" + getSlug() + "'" +
            ", title='" + getTitle() + "'" +
            ", body='" + getBody() + "'" +
            "}";
    }
}
