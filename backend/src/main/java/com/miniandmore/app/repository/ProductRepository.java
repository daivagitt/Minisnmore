package com.miniandmore.app.repository;

import com.miniandmore.app.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Product entity.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    default Optional<Product> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<Product> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<Product> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select product from Product product left join fetch product.category",
        countQuery = "select count(product) from Product product"
    )
    Page<Product> findAllWithToOneRelationships(Pageable pageable);

    @Query("select product from Product product left join fetch product.category")
    List<Product> findAllWithToOneRelationships();

    @Query("select product from Product product left join fetch product.category where product.id =:id")
    Optional<Product> findOneWithToOneRelationships(@Param("id") Long id);

    @Query("select product from Product product left join fetch product.category where product.active = true order by product.sortOrder asc")
    List<Product> findAllActiveWithCategoryOrderBySortOrder();

    @Query("select product from Product product left join fetch product.category order by product.sortOrder asc")
    List<Product> findAllWithCategoryOrderBySortOrder();

    @Query("select product from Product product left join fetch product.category where product.slug = :slug and product.active = true")
    Optional<Product> findOneActiveBySlugWithCategory(@Param("slug") String slug);

    Optional<Product> findOneBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.id = :id")
    Optional<Product> findOneForInventoryUpdate(@Param("id") Long id);

    Optional<Product> findOneBySku(String sku);

    Optional<Product> findOneByLedgerlyItemId(String ledgerlyItemId);

    @Query("select coalesce(max(product.sortOrder), 0) from Product product")
    Integer findMaxSortOrder();

    @Query("select count(product) from Product product where product.sku is not null")
    long countWithSku();

    List<Product> findAllByActiveTrue();

    List<Product> findAllByLedgerlyItemIdIsNotNull();

    @Query(
        "select product from Product product where product.active = true and (product.ledgerlyItemId is not null or product.sku is not null)"
    )
    List<Product> findAllActiveEligibleForLedgerlyInventoryPull();

    long countByCategory_Id(Long categoryId);
}
