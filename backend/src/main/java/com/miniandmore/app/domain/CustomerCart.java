package com.miniandmore.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "customer_cart")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CustomerCart implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 50)
    @Column(name = "user_login", length = 50, nullable = false, unique = true)
    private String userLogin;

    @NotNull
    @Column(name = "items_json", columnDefinition = "text", nullable = false)
    private String itemsJson = "[]";

    @Column(name = "coupon_json", columnDefinition = "text")
    private String couponJson;

    public Long getId() {
        return id;
    }

    public CustomerCart id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public CustomerCart userLogin(String userLogin) {
        this.setUserLogin(userLogin);
        return this;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public CustomerCart itemsJson(String itemsJson) {
        this.setItemsJson(itemsJson);
        return this;
    }

    public void setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
    }

    public String getCouponJson() {
        return couponJson;
    }

    public CustomerCart couponJson(String couponJson) {
        this.setCouponJson(couponJson);
        return this;
    }

    public void setCouponJson(String couponJson) {
        this.couponJson = couponJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CustomerCart)) {
            return false;
        }
        return getId() != null && getId().equals(((CustomerCart) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
