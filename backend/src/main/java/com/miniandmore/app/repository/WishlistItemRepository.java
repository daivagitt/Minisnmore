package com.miniandmore.app.repository;

import com.miniandmore.app.domain.WishlistItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findAllByUserLoginOrderByIdAsc(String userLogin);

    Optional<WishlistItem> findOneByUserLoginAndProductId(String userLogin, String productId);

    void deleteByUserLoginAndProductId(String userLogin, String productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    void deleteByUserLogin(String userLogin);

    boolean existsByUserLoginAndProductId(String userLogin, String productId);
}
