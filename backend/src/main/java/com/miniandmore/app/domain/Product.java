package com.miniandmore.app.domain;

import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A Product.
 */
@Entity
@Table(name = "product")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Product implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 180)
    @Column(name = "slug", length = 180, nullable = false, unique = true)
    private String slug;

    @NotNull
    @Size(min = 1, max = 200)
    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Size(max = 120)
    @Column(name = "sku", length = 120, unique = true)
    private String sku;

    @Size(max = 120)
    @Column(name = "fabric_type", length = 120)
    private String fabricType;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "story", columnDefinition = "text")
    private String story;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "price", precision = 21, scale = 2, nullable = false)
    private BigDecimal price;

    @DecimalMin(value = "0")
    @Column(name = "compare_at_price", precision = 21, scale = 2)
    private BigDecimal compareAtPrice;

    @NotNull
    @Size(min = 3, max = 3)
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Min(value = 0)
    @Column(name = "age_min_months")
    private Integer ageMinMonths;

    @Min(value = 0)
    @Column(name = "age_max_months")
    private Integer ageMaxMonths;

    @Column(name = "sizes_json", columnDefinition = "text")
    private String sizesJson;

    @Column(name = "colors_json", columnDefinition = "text")
    private String colorsJson;

    @Column(name = "images_json", columnDefinition = "text")
    private String imagesJson;

    @Size(max = 80)
    @Column(name = "badge", length = 80)
    private String badge;

    @NotNull
    @Min(value = 0)
    @Column(name = "stock", nullable = false)
    private Integer stock;

    @NotNull
    @Min(value = 0)
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @NotNull
    @Column(name = "featured", nullable = false)
    private Boolean featured;

    @NotNull
    @Column(name = "trending", nullable = false)
    private Boolean trending;

    @NotNull
    @Column(name = "new_arrival", nullable = false)
    private Boolean newArrival;

    @NotNull
    @Column(name = "best_seller", nullable = false)
    private Boolean bestSeller;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active;

    @Size(max = 120)
    @Column(name = "ledgerly_item_id", length = 120, unique = true)
    private String zohoItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledgerly_sync_status")
    private ZohoSyncStatus zohoSyncStatus;

    @Column(name = "ledgerly_last_synced_at")
    private Instant zohoLastSyncedAt;

    @Size(max = 1000)
    @Column(name = "ledgerly_sync_error", length = 1000)
    private String zohoSyncError;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Product id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSlug() {
        return this.slug;
    }

    public Product slug(String slug) {
        this.setSlug(slug);
        return this;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return this.name;
    }

    public Product name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSku() {
        return this.sku;
    }

    public Product sku(String sku) {
        this.setSku(sku);
        return this;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getFabricType() {
        return this.fabricType;
    }

    public Product fabricType(String fabricType) {
        this.setFabricType(fabricType);
        return this;
    }

    public void setFabricType(String fabricType) {
        this.fabricType = fabricType;
    }

    public String getDescription() {
        return this.description;
    }

    public Product description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStory() {
        return this.story;
    }

    public Product story(String story) {
        this.setStory(story);
        return this;
    }

    public void setStory(String story) {
        this.story = story;
    }

    public BigDecimal getPrice() {
        return this.price;
    }

    public Product price(BigDecimal price) {
        this.setPrice(price);
        return this;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getCompareAtPrice() {
        return this.compareAtPrice;
    }

    public Product compareAtPrice(BigDecimal compareAtPrice) {
        this.setCompareAtPrice(compareAtPrice);
        return this;
    }

    public void setCompareAtPrice(BigDecimal compareAtPrice) {
        this.compareAtPrice = compareAtPrice;
    }

    public String getCurrency() {
        return this.currency;
    }

    public Product currency(String currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getAgeMinMonths() {
        return this.ageMinMonths;
    }

    public Product ageMinMonths(Integer ageMinMonths) {
        this.setAgeMinMonths(ageMinMonths);
        return this;
    }

    public void setAgeMinMonths(Integer ageMinMonths) {
        this.ageMinMonths = ageMinMonths;
    }

    public Integer getAgeMaxMonths() {
        return this.ageMaxMonths;
    }

    public Product ageMaxMonths(Integer ageMaxMonths) {
        this.setAgeMaxMonths(ageMaxMonths);
        return this;
    }

    public void setAgeMaxMonths(Integer ageMaxMonths) {
        this.ageMaxMonths = ageMaxMonths;
    }

    public String getSizesJson() {
        return this.sizesJson;
    }

    public Product sizesJson(String sizesJson) {
        this.setSizesJson(sizesJson);
        return this;
    }

    public void setSizesJson(String sizesJson) {
        this.sizesJson = sizesJson;
    }

    public String getColorsJson() {
        return this.colorsJson;
    }

    public Product colorsJson(String colorsJson) {
        this.setColorsJson(colorsJson);
        return this;
    }

    public void setColorsJson(String colorsJson) {
        this.colorsJson = colorsJson;
    }

    public String getImagesJson() {
        return this.imagesJson;
    }

    public Product imagesJson(String imagesJson) {
        this.setImagesJson(imagesJson);
        return this;
    }

    public void setImagesJson(String imagesJson) {
        this.imagesJson = imagesJson;
    }

    public String getBadge() {
        return this.badge;
    }

    public Product badge(String badge) {
        this.setBadge(badge);
        return this;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public Integer getStock() {
        return this.stock;
    }

    public Product stock(Integer stock) {
        this.setStock(stock);
        return this;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getSortOrder() {
        return this.sortOrder;
    }

    public Product sortOrder(Integer sortOrder) {
        this.setSortOrder(sortOrder);
        return this;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getFeatured() {
        return this.featured;
    }

    public Product featured(Boolean featured) {
        this.setFeatured(featured);
        return this;
    }

    public void setFeatured(Boolean featured) {
        this.featured = featured;
    }

    public Boolean getTrending() {
        return this.trending;
    }

    public Product trending(Boolean trending) {
        this.setTrending(trending);
        return this;
    }

    public void setTrending(Boolean trending) {
        this.trending = trending;
    }

    public Boolean getNewArrival() {
        return this.newArrival;
    }

    public Product newArrival(Boolean newArrival) {
        this.setNewArrival(newArrival);
        return this;
    }

    public void setNewArrival(Boolean newArrival) {
        this.newArrival = newArrival;
    }

    public Boolean getBestSeller() {
        return this.bestSeller;
    }

    public Product bestSeller(Boolean bestSeller) {
        this.setBestSeller(bestSeller);
        return this;
    }

    public void setBestSeller(Boolean bestSeller) {
        this.bestSeller = bestSeller;
    }

    public Boolean getActive() {
        return this.active;
    }

    public Product active(Boolean active) {
        this.setActive(active);
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getZohoItemId() {
        return this.zohoItemId;
    }

    public Product zohoItemId(String zohoItemId) {
        this.setZohoItemId(zohoItemId);
        return this;
    }

    public void setZohoItemId(String zohoItemId) {
        this.zohoItemId = zohoItemId;
    }

    public ZohoSyncStatus getZohoSyncStatus() {
        return this.zohoSyncStatus;
    }

    public Product zohoSyncStatus(ZohoSyncStatus zohoSyncStatus) {
        this.setZohoSyncStatus(zohoSyncStatus);
        return this;
    }

    public void setZohoSyncStatus(ZohoSyncStatus zohoSyncStatus) {
        this.zohoSyncStatus = zohoSyncStatus;
    }

    public Instant getZohoLastSyncedAt() {
        return this.zohoLastSyncedAt;
    }

    public Product zohoLastSyncedAt(Instant zohoLastSyncedAt) {
        this.setZohoLastSyncedAt(zohoLastSyncedAt);
        return this;
    }

    public void setZohoLastSyncedAt(Instant zohoLastSyncedAt) {
        this.zohoLastSyncedAt = zohoLastSyncedAt;
    }

    public String getZohoSyncError() {
        return this.zohoSyncError;
    }

    public Product zohoSyncError(String zohoSyncError) {
        this.setZohoSyncError(zohoSyncError);
        return this;
    }

    public void setZohoSyncError(String zohoSyncError) {
        this.zohoSyncError = zohoSyncError;
    }

    public Category getCategory() {
        return this.category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Product category(Category category) {
        this.setCategory(category);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Product)) {
            return false;
        }
        return getId() != null && getId().equals(((Product) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Product{" +
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
            "}";
    }
}
