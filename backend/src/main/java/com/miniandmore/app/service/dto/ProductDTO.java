package com.miniandmore.app.service.dto;

import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.Product} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ProductDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 180)
    private String slug;

    @NotNull
    @Size(min = 1, max = 200)
    private String name;

    @Size(max = 120)
    private String sku;

    @Size(max = 120)
    private String fabricType;

    private String description;

    private String story;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal price;

    @DecimalMin(value = "0")
    private BigDecimal compareAtPrice;

    @NotNull
    @Size(min = 3, max = 3)
    private String currency;

    @Min(value = 0)
    private Integer ageMinMonths;

    @Min(value = 0)
    private Integer ageMaxMonths;

    private String sizesJson;

    private String colorsJson;

    private String imagesJson;

    @Size(max = 80)
    private String badge;

    @NotNull
    @Min(value = 0)
    private Integer stock;

    @NotNull
    @Min(value = 0)
    private Integer sortOrder;

    @NotNull
    private Boolean featured;

    @NotNull
    private Boolean trending;

    @NotNull
    private Boolean newArrival;

    @NotNull
    private Boolean bestSeller;

    @NotNull
    private Boolean active;

    @Size(max = 120)
    private String ledgerlyItemId;

    private LedgerlySyncStatus ledgerlySyncStatus;

    private Instant ledgerlyLastSyncedAt;

    @Size(max = 1000)
    private String ledgerlySyncError;

    private CategoryDTO category;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getFabricType() {
        return fabricType;
    }

    public void setFabricType(String fabricType) {
        this.fabricType = fabricType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStory() {
        return story;
    }

    public void setStory(String story) {
        this.story = story;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getCompareAtPrice() {
        return compareAtPrice;
    }

    public void setCompareAtPrice(BigDecimal compareAtPrice) {
        this.compareAtPrice = compareAtPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getAgeMinMonths() {
        return ageMinMonths;
    }

    public void setAgeMinMonths(Integer ageMinMonths) {
        this.ageMinMonths = ageMinMonths;
    }

    public Integer getAgeMaxMonths() {
        return ageMaxMonths;
    }

    public void setAgeMaxMonths(Integer ageMaxMonths) {
        this.ageMaxMonths = ageMaxMonths;
    }

    public String getSizesJson() {
        return sizesJson;
    }

    public void setSizesJson(String sizesJson) {
        this.sizesJson = sizesJson;
    }

    public String getColorsJson() {
        return colorsJson;
    }

    public void setColorsJson(String colorsJson) {
        this.colorsJson = colorsJson;
    }

    public String getImagesJson() {
        return imagesJson;
    }

    public void setImagesJson(String imagesJson) {
        this.imagesJson = imagesJson;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getFeatured() {
        return featured;
    }

    public void setFeatured(Boolean featured) {
        this.featured = featured;
    }

    public Boolean getTrending() {
        return trending;
    }

    public void setTrending(Boolean trending) {
        this.trending = trending;
    }

    public Boolean getNewArrival() {
        return newArrival;
    }

    public void setNewArrival(Boolean newArrival) {
        this.newArrival = newArrival;
    }

    public Boolean getBestSeller() {
        return bestSeller;
    }

    public void setBestSeller(Boolean bestSeller) {
        this.bestSeller = bestSeller;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getLedgerlyItemId() {
        return ledgerlyItemId;
    }

    public void setLedgerlyItemId(String ledgerlyItemId) {
        this.ledgerlyItemId = ledgerlyItemId;
    }

    public LedgerlySyncStatus getLedgerlySyncStatus() {
        return ledgerlySyncStatus;
    }

    public void setLedgerlySyncStatus(LedgerlySyncStatus ledgerlySyncStatus) {
        this.ledgerlySyncStatus = ledgerlySyncStatus;
    }

    public Instant getLedgerlyLastSyncedAt() {
        return ledgerlyLastSyncedAt;
    }

    public void setLedgerlyLastSyncedAt(Instant ledgerlyLastSyncedAt) {
        this.ledgerlyLastSyncedAt = ledgerlyLastSyncedAt;
    }

    public String getLedgerlySyncError() {
        return ledgerlySyncError;
    }

    public void setLedgerlySyncError(String ledgerlySyncError) {
        this.ledgerlySyncError = ledgerlySyncError;
    }

    public CategoryDTO getCategory() {
        return category;
    }

    public void setCategory(CategoryDTO category) {
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProductDTO)) {
            return false;
        }

        ProductDTO productDTO = (ProductDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, productDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ProductDTO{" +
            "id=" + getId() +
            ", slug='" + getSlug() + "'" +
            ", name='" + getName() + "'" +
            ", sku='" + getSku() + "'" +
            ", fabricType='" + getFabricType() + "'" +
            ", description='" + getDescription() + "'" +
            ", story='" + getStory() + "'" +
            ", price=" + getPrice() +
            ", compareAtPrice=" + getCompareAtPrice() +
            ", currency='" + getCurrency() + "'" +
            ", ageMinMonths=" + getAgeMinMonths() +
            ", ageMaxMonths=" + getAgeMaxMonths() +
            ", sizesJson='" + getSizesJson() + "'" +
            ", colorsJson='" + getColorsJson() + "'" +
            ", imagesJson='" + getImagesJson() + "'" +
            ", badge='" + getBadge() + "'" +
            ", stock=" + getStock() +
            ", sortOrder=" + getSortOrder() +
            ", featured='" + getFeatured() + "'" +
            ", trending='" + getTrending() + "'" +
            ", newArrival='" + getNewArrival() + "'" +
            ", bestSeller='" + getBestSeller() + "'" +
            ", active='" + getActive() + "'" +
            ", ledgerlyItemId='" + getLedgerlyItemId() + "'" +
            ", ledgerlySyncStatus='" + getLedgerlySyncStatus() + "'" +
            ", ledgerlyLastSyncedAt='" + getLedgerlyLastSyncedAt() + "'" +
            ", ledgerlySyncError='" + getLedgerlySyncError() + "'" +
            ", category=" + getCategory() +
            "}";
    }
}
