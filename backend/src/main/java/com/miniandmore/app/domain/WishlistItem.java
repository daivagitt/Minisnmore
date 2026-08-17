package com.miniandmore.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(
    name = "wishlist_item",
    uniqueConstraints = @UniqueConstraint(name = "ux_wishlist_item__user_login_product_id", columnNames = { "user_login", "product_id" })
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class WishlistItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 50)
    @Column(name = "user_login", length = 50, nullable = false)
    private String userLogin;

    @NotNull
    @Size(max = 120)
    @Column(name = "product_id", length = 120, nullable = false)
    private String productId;

    public Long getId() {
        return id;
    }

    public WishlistItem id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public WishlistItem userLogin(String userLogin) {
        this.setUserLogin(userLogin);
        return this;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public String getProductId() {
        return productId;
    }

    public WishlistItem productId(String productId) {
        this.setProductId(productId);
        return this;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WishlistItem)) {
            return false;
        }
        return getId() != null && getId().equals(((WishlistItem) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
