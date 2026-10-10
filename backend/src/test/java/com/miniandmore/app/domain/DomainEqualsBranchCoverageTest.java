package com.miniandmore.app.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Exercises equals/hashCode branches on simple id-based entities. */
class DomainEqualsBranchCoverageTest {

    @Test
    void customerAddress_equalsBranches() {
        CustomerAddress a = new CustomerAddress().id(1L).line1("A");
        CustomerAddress same = new CustomerAddress().id(1L).line1("B");
        CustomerAddress other = new CustomerAddress().id(2L);
        CustomerAddress noId = new CustomerAddress();

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(same);
        assertThat(a).isNotEqualTo(other);
        assertThat(a).isNotEqualTo(noId);
        assertThat(a).isNotEqualTo("not-address");
        assertThat(a.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    void customerCart_equalsBranches() {
        CustomerCart a = new CustomerCart().id(10L);
        CustomerCart same = new CustomerCart().id(10L);
        CustomerCart other = new CustomerCart().id(11L);
        CustomerCart noId = new CustomerCart();

        assertThat(a).isEqualTo(same);
        assertThat(a).isNotEqualTo(other);
        assertThat(a).isNotEqualTo(noId);
        assertThat(a).isNotEqualTo(null);
    }

    @Test
    void wishlistItem_equalsBranches() {
        WishlistItem a = new WishlistItem().id(5L);
        WishlistItem same = new WishlistItem().id(5L);
        WishlistItem other = new WishlistItem().id(6L);

        assertThat(a).isEqualTo(same);
        assertThat(a).isNotEqualTo(other);
        assertThat(a).isNotEqualTo(new WishlistItem());
    }
}
