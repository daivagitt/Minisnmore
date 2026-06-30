package com.miniandmore.app.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.BlogPost} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BlogPostDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 180)
    private String slug;

    @NotNull
    @Size(min = 1, max = 220)
    private String title;

    private String excerpt;

    private String content;

    @Size(max = 600)
    private String coverUrl;

    @NotNull
    private Boolean published;

    private Instant publishedAt;

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

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Boolean getPublished() {
        return published;
    }

    public void setPublished(Boolean published) {
        this.published = published;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlogPostDTO)) {
            return false;
        }

        BlogPostDTO blogPostDTO = (BlogPostDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, blogPostDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BlogPostDTO{" +
            "id=" + getId() +
            ", slug='" + getSlug() + "'" +
            ", title='" + getTitle() + "'" +
            ", excerpt='" + getExcerpt() + "'" +
            ", content='" + getContent() + "'" +
            ", coverUrl='" + getCoverUrl() + "'" +
            ", published='" + getPublished() + "'" +
            ", publishedAt='" + getPublishedAt() + "'" +
            "}";
    }
}
