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

    Optional<Product> findOneByZohoItemId(String zohoItemId);

    Optional<Product> findOneByLedgerlyItemId(String ledgerlyItemId);

    @Query("select coalesce(max(product.sortOrder), 0) from Product product")
    Integer findMaxSortOrder();

    @Query("select count(product) from Product product where product.sku is not null")
    long countWithSku();

    @Query("select count(product) from Product product where product.active = true")
    long countByActiveTrue();

    List<Product> findAllByActiveTrue();

    List<Product> findAllByZohoItemIdIsNotNull();

    @Query(
        "select product from Product product where product.active = true and (product.zohoItemId is not null or product.sku is not null)"
    )
    List<Product> findAllActiveEligibleForZohoInventoryPull();

    List<Product> findAllByLedgerlyItemIdIsNotNull();

    @Query(
        "select product from Product product where product.active = true and " +
        "(product.ledgerlyItemId is not null or product.ledgerlySyncStatus = com.miniandmore.app.domain.enumeration.LedgerlySyncStatus.SYNCED)"
    )
    List<Product> findAllActiveLedgerlyLinked();

    @Query(
        "select product from Product product where product.active = true and (product.ledgerlyItemId is not null or product.sku is not null)"
    )
    List<Product> findAllActiveEligibleForLedgerlyInventoryPull();

    long countByCategory_Id(Long categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Product p
           set p.zohoItemId = :zohoItemId,
               p.zohoSyncStatus = :status,
               p.zohoLastSyncedAt = :syncedAt,
               p.zohoSyncError = null
         where p.id = :id
        """
    )
    int updateZohoSyncSuccess(
        @Param("id") Long id,
        @Param("zohoItemId") String zohoItemId,
        @Param("status") com.miniandmore.app.domain.enumeration.ZohoSyncStatus status,
        @Param("syncedAt") java.time.Instant syncedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Product p
           set p.zohoSyncStatus = :status,
               p.zohoSyncError = :error
         where p.id = :id
        """
    )
    int updateZohoSyncFailure(
        @Param("id") Long id,
        @Param("status") com.miniandmore.app.domain.enumeration.ZohoSyncStatus status,
        @Param("error") String error
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Product p
           set p.zohoLastSyncedAt = :syncedAt,
               p.zohoSyncError = null,
               p.zohoItemId = case when :clearItemId = true then null else p.zohoItemId end
         where p.id = :id
        """
    )
    int updateZohoDeactivateMeta(
        @Param("id") Long id,
        @Param("syncedAt") java.time.Instant syncedAt,
        @Param("clearItemId") boolean clearItemId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Product p
           set p.ledgerlyItemId = :ledgerlyItemId,
               p.ledgerlySyncStatus = :status,
               p.ledgerlyLastSyncedAt = :syncedAt,
               p.ledgerlySyncError = null
         where p.id = :id
        """
    )
    int updateLedgerlySyncSuccess(
        @Param("id") Long id,
        @Param("ledgerlyItemId") String ledgerlyItemId,
        @Param("status") com.miniandmore.app.domain.enumeration.LedgerlySyncStatus status,
        @Param("syncedAt") java.time.Instant syncedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Product p
           set p.ledgerlySyncStatus = :status,
               p.ledgerlySyncError = :error
         where p.id = :id
        """
    )
    int updateLedgerlySyncFailure(
        @Param("id") Long id,
        @Param("status") com.miniandmore.app.domain.enumeration.LedgerlySyncStatus status,
        @Param("error") String error
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Product p
           set p.ledgerlyLastSyncedAt = :syncedAt,
               p.ledgerlySyncError = null,
               p.ledgerlyItemId = case when :clearItemId = true then null else p.ledgerlyItemId end
         where p.id = :id
        """
    )
    int updateLedgerlyDeactivateMeta(
        @Param("id") Long id,
        @Param("syncedAt") java.time.Instant syncedAt,
        @Param("clearItemId") boolean clearItemId
    );
}
