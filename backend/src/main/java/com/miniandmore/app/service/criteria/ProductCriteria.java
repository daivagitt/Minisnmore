package com.miniandmore.app.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.miniandmore.app.domain.Product} entity. This class is used
 * in {@link com.miniandmore.app.web.rest.ProductResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /products?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ProductCriteria implements Serializable, Criteria {

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter slug;

    private StringFilter name;

    private StringFilter sku;

    private StringFilter fabricType;

    private BigDecimalFilter price;

    private BigDecimalFilter compareAtPrice;

    private StringFilter currency;

    private IntegerFilter ageMinMonths;

    private IntegerFilter ageMaxMonths;

    private StringFilter badge;

    private IntegerFilter stock;

    private IntegerFilter sortOrder;

    private BooleanFilter featured;

    private BooleanFilter trending;

    private BooleanFilter newArrival;

    private BooleanFilter bestSeller;

    private BooleanFilter active;

    private LongFilter categoryId;

    private Boolean distinct;

    public ProductCriteria() {}

    public ProductCriteria(ProductCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.slug = other.optionalSlug().map(StringFilter::copy).orElse(null);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.sku = other.optionalSku().map(StringFilter::copy).orElse(null);
        this.fabricType = other.optionalFabricType().map(StringFilter::copy).orElse(null);
        this.price = other.optionalPrice().map(BigDecimalFilter::copy).orElse(null);
        this.compareAtPrice = other.optionalCompareAtPrice().map(BigDecimalFilter::copy).orElse(null);
        this.currency = other.optionalCurrency().map(StringFilter::copy).orElse(null);
        this.ageMinMonths = other.optionalAgeMinMonths().map(IntegerFilter::copy).orElse(null);
        this.ageMaxMonths = other.optionalAgeMaxMonths().map(IntegerFilter::copy).orElse(null);
        this.badge = other.optionalBadge().map(StringFilter::copy).orElse(null);
        this.stock = other.optionalStock().map(IntegerFilter::copy).orElse(null);
        this.sortOrder = other.optionalSortOrder().map(IntegerFilter::copy).orElse(null);
        this.featured = other.optionalFeatured().map(BooleanFilter::copy).orElse(null);
        this.trending = other.optionalTrending().map(BooleanFilter::copy).orElse(null);
        this.newArrival = other.optionalNewArrival().map(BooleanFilter::copy).orElse(null);
        this.bestSeller = other.optionalBestSeller().map(BooleanFilter::copy).orElse(null);
        this.active = other.optionalActive().map(BooleanFilter::copy).orElse(null);
        this.categoryId = other.optionalCategoryId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public ProductCriteria copy() {
        return new ProductCriteria(this);
    }

    public LongFilter getId() {
        return id;
    }

    public Optional<LongFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public LongFilter id() {
        if (id == null) {
            setId(new LongFilter());
        }
        return id;
    }

    public void setId(LongFilter id) {
        this.id = id;
    }

    public StringFilter getSlug() {
        return slug;
    }

    public Optional<StringFilter> optionalSlug() {
        return Optional.ofNullable(slug);
    }

    public StringFilter slug() {
        if (slug == null) {
            setSlug(new StringFilter());
        }
        return slug;
    }

    public void setSlug(StringFilter slug) {
        this.slug = slug;
    }

    public StringFilter getName() {
        return name;
    }

    public Optional<StringFilter> optionalName() {
        return Optional.ofNullable(name);
    }

    public StringFilter name() {
        if (name == null) {
            setName(new StringFilter());
        }
        return name;
    }

    public void setName(StringFilter name) {
        this.name = name;
    }

    public StringFilter getSku() {
        return sku;
    }

    public Optional<StringFilter> optionalSku() {
        return Optional.ofNullable(sku);
    }

    public StringFilter sku() {
        if (sku == null) {
            setSku(new StringFilter());
        }
        return sku;
    }

    public void setSku(StringFilter sku) {
        this.sku = sku;
    }

    public StringFilter getFabricType() {
        return fabricType;
    }

    public Optional<StringFilter> optionalFabricType() {
        return Optional.ofNullable(fabricType);
    }

    public StringFilter fabricType() {
        if (fabricType == null) {
            setFabricType(new StringFilter());
        }
        return fabricType;
    }

    public void setFabricType(StringFilter fabricType) {
        this.fabricType = fabricType;
    }

    public BigDecimalFilter getPrice() {
        return price;
    }

    public Optional<BigDecimalFilter> optionalPrice() {
        return Optional.ofNullable(price);
    }

    public BigDecimalFilter price() {
        if (price == null) {
            setPrice(new BigDecimalFilter());
        }
        return price;
    }

    public void setPrice(BigDecimalFilter price) {
        this.price = price;
    }

    public BigDecimalFilter getCompareAtPrice() {
        return compareAtPrice;
    }

    public Optional<BigDecimalFilter> optionalCompareAtPrice() {
        return Optional.ofNullable(compareAtPrice);
    }

    public BigDecimalFilter compareAtPrice() {
        if (compareAtPrice == null) {
            setCompareAtPrice(new BigDecimalFilter());
        }
        return compareAtPrice;
    }

    public void setCompareAtPrice(BigDecimalFilter compareAtPrice) {
        this.compareAtPrice = compareAtPrice;
    }

    public StringFilter getCurrency() {
        return currency;
    }

    public Optional<StringFilter> optionalCurrency() {
        return Optional.ofNullable(currency);
    }

    public StringFilter currency() {
        if (currency == null) {
            setCurrency(new StringFilter());
        }
        return currency;
    }

    public void setCurrency(StringFilter currency) {
        this.currency = currency;
    }

    public IntegerFilter getAgeMinMonths() {
        return ageMinMonths;
    }

    public Optional<IntegerFilter> optionalAgeMinMonths() {
        return Optional.ofNullable(ageMinMonths);
    }

    public IntegerFilter ageMinMonths() {
        if (ageMinMonths == null) {
            setAgeMinMonths(new IntegerFilter());
        }
        return ageMinMonths;
    }

    public void setAgeMinMonths(IntegerFilter ageMinMonths) {
        this.ageMinMonths = ageMinMonths;
    }

    public IntegerFilter getAgeMaxMonths() {
        return ageMaxMonths;
    }

    public Optional<IntegerFilter> optionalAgeMaxMonths() {
        return Optional.ofNullable(ageMaxMonths);
    }

    public IntegerFilter ageMaxMonths() {
        if (ageMaxMonths == null) {
            setAgeMaxMonths(new IntegerFilter());
        }
        return ageMaxMonths;
    }

    public void setAgeMaxMonths(IntegerFilter ageMaxMonths) {
        this.ageMaxMonths = ageMaxMonths;
    }

    public StringFilter getBadge() {
        return badge;
    }

    public Optional<StringFilter> optionalBadge() {
        return Optional.ofNullable(badge);
    }

    public StringFilter badge() {
        if (badge == null) {
            setBadge(new StringFilter());
        }
        return badge;
    }

    public void setBadge(StringFilter badge) {
        this.badge = badge;
    }

    public IntegerFilter getStock() {
        return stock;
    }

    public Optional<IntegerFilter> optionalStock() {
        return Optional.ofNullable(stock);
    }

    public IntegerFilter stock() {
        if (stock == null) {
            setStock(new IntegerFilter());
        }
        return stock;
    }

    public void setStock(IntegerFilter stock) {
        this.stock = stock;
    }

    public IntegerFilter getSortOrder() {
        return sortOrder;
    }

    public Optional<IntegerFilter> optionalSortOrder() {
        return Optional.ofNullable(sortOrder);
    }

    public IntegerFilter sortOrder() {
        if (sortOrder == null) {
            setSortOrder(new IntegerFilter());
        }
        return sortOrder;
    }

    public void setSortOrder(IntegerFilter sortOrder) {
        this.sortOrder = sortOrder;
    }

    public BooleanFilter getFeatured() {
        return featured;
    }

    public Optional<BooleanFilter> optionalFeatured() {
        return Optional.ofNullable(featured);
    }

    public BooleanFilter featured() {
        if (featured == null) {
            setFeatured(new BooleanFilter());
        }
        return featured;
    }

    public void setFeatured(BooleanFilter featured) {
        this.featured = featured;
    }

    public BooleanFilter getTrending() {
        return trending;
    }

    public Optional<BooleanFilter> optionalTrending() {
        return Optional.ofNullable(trending);
    }

    public BooleanFilter trending() {
        if (trending == null) {
            setTrending(new BooleanFilter());
        }
        return trending;
    }

    public void setTrending(BooleanFilter trending) {
        this.trending = trending;
    }

    public BooleanFilter getNewArrival() {
        return newArrival;
    }

    public Optional<BooleanFilter> optionalNewArrival() {
        return Optional.ofNullable(newArrival);
    }

    public BooleanFilter newArrival() {
        if (newArrival == null) {
            setNewArrival(new BooleanFilter());
        }
        return newArrival;
    }

    public void setNewArrival(BooleanFilter newArrival) {
        this.newArrival = newArrival;
    }

    public BooleanFilter getBestSeller() {
        return bestSeller;
    }

    public Optional<BooleanFilter> optionalBestSeller() {
        return Optional.ofNullable(bestSeller);
    }

    public BooleanFilter bestSeller() {
        if (bestSeller == null) {
            setBestSeller(new BooleanFilter());
        }
        return bestSeller;
    }

    public void setBestSeller(BooleanFilter bestSeller) {
        this.bestSeller = bestSeller;
    }

    public BooleanFilter getActive() {
        return active;
    }

    public Optional<BooleanFilter> optionalActive() {
        return Optional.ofNullable(active);
    }

    public BooleanFilter active() {
        if (active == null) {
            setActive(new BooleanFilter());
        }
        return active;
    }

    public void setActive(BooleanFilter active) {
        this.active = active;
    }

    public LongFilter getCategoryId() {
        return categoryId;
    }

    public Optional<LongFilter> optionalCategoryId() {
        return Optional.ofNullable(categoryId);
    }

    public LongFilter categoryId() {
        if (categoryId == null) {
            setCategoryId(new LongFilter());
        }
        return categoryId;
    }

    public void setCategoryId(LongFilter categoryId) {
        this.categoryId = categoryId;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProductCriteria that = (ProductCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(slug, that.slug) &&
            Objects.equals(name, that.name) &&
            Objects.equals(sku, that.sku) &&
            Objects.equals(fabricType, that.fabricType) &&
            Objects.equals(price, that.price) &&
            Objects.equals(compareAtPrice, that.compareAtPrice) &&
            Objects.equals(currency, that.currency) &&
            Objects.equals(ageMinMonths, that.ageMinMonths) &&
            Objects.equals(ageMaxMonths, that.ageMaxMonths) &&
            Objects.equals(badge, that.badge) &&
            Objects.equals(stock, that.stock) &&
            Objects.equals(sortOrder, that.sortOrder) &&
            Objects.equals(featured, that.featured) &&
            Objects.equals(trending, that.trending) &&
            Objects.equals(newArrival, that.newArrival) &&
            Objects.equals(bestSeller, that.bestSeller) &&
            Objects.equals(active, that.active) &&
            Objects.equals(categoryId, that.categoryId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            slug,
            name,
            sku,
            fabricType,
            price,
            compareAtPrice,
            currency,
            ageMinMonths,
            ageMaxMonths,
            badge,
            stock,
            sortOrder,
            featured,
            trending,
            newArrival,
            bestSeller,
            active,
            categoryId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ProductCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalSlug().map(f -> "slug=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalSku().map(f -> "sku=" + f + ", ").orElse("") +
            optionalFabricType().map(f -> "fabricType=" + f + ", ").orElse("") +
            optionalPrice().map(f -> "price=" + f + ", ").orElse("") +
            optionalCompareAtPrice().map(f -> "compareAtPrice=" + f + ", ").orElse("") +
            optionalCurrency().map(f -> "currency=" + f + ", ").orElse("") +
            optionalAgeMinMonths().map(f -> "ageMinMonths=" + f + ", ").orElse("") +
            optionalAgeMaxMonths().map(f -> "ageMaxMonths=" + f + ", ").orElse("") +
            optionalBadge().map(f -> "badge=" + f + ", ").orElse("") +
            optionalStock().map(f -> "stock=" + f + ", ").orElse("") +
            optionalSortOrder().map(f -> "sortOrder=" + f + ", ").orElse("") +
            optionalFeatured().map(f -> "featured=" + f + ", ").orElse("") +
            optionalTrending().map(f -> "trending=" + f + ", ").orElse("") +
            optionalNewArrival().map(f -> "newArrival=" + f + ", ").orElse("") +
            optionalBestSeller().map(f -> "bestSeller=" + f + ", ").orElse("") +
            optionalActive().map(f -> "active=" + f + ", ").orElse("") +
            optionalCategoryId().map(f -> "categoryId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
