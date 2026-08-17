package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.domain.CustomerAddress;
import com.miniandmore.app.domain.CustomerCart;
import com.miniandmore.app.domain.WishlistItem;
import com.miniandmore.app.repository.CustomerAddressRepository;
import com.miniandmore.app.repository.CustomerCartRepository;
import com.miniandmore.app.repository.WishlistItemRepository;
import com.miniandmore.app.web.rest.errors.ResponseStatusExceptionHandler;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalAccountResource.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ResponseStatusExceptionHandler.class)
class InternalAccountResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerAddressRepository customerAddressRepository;

    @MockitoBean
    private WishlistItemRepository wishlistItemRepository;

    @MockitoBean
    private CustomerCartRepository customerCartRepository;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("customer1", "n/a"));
    }

    @Test
    void listAddresses_returnsOwnedRows() throws Exception {
        CustomerAddress address = new CustomerAddress()
            .id(1L)
            .userLogin("customer1")
            .label("Home")
            .fullName("Jane")
            .phone("9876543210")
            .line1("Line 1")
            .city("City")
            .state("State")
            .pincode("560001")
            .isDefault(true);
        when(customerAddressRepository.findAllByUserLoginOrderByIsDefaultDescIdAsc("customer1")).thenReturn(List.of(address));

        mockMvc
            .perform(get("/internal/v1/account/addresses").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.addresses[0].full_name").value("Jane"))
            .andExpect(jsonPath("$.data.addresses[0].is_default").value(true));
    }

    @Test
    void createAddress_setsDefaultForFirstRow() throws Exception {
        when(customerAddressRepository.countByUserLogin("customer1")).thenReturn(0L);
        when(customerAddressRepository.save(any(CustomerAddress.class))).thenAnswer(inv -> {
            CustomerAddress address = inv.getArgument(0);
            address.setId(5L);
            return address;
        });

        mockMvc
            .perform(
                post("/internal/v1/account/addresses")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "label":"Home",
                          "full_name":"Jane",
                          "phone":"9876543210",
                          "line1":"Line 1",
                          "city":"City",
                          "state":"State",
                          "pincode":"560001"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.address.id").value(5))
            .andExpect(jsonPath("$.data.address.is_default").value(true));

        ArgumentCaptor<CustomerAddress> captor = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(customerAddressRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getIsDefault()).isTrue();
    }

    @Test
    void wishlist_addAndList() throws Exception {
        when(wishlistItemRepository.existsByUserLoginAndProductId("customer1", "p1")).thenReturn(false);
        when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(wishlistItemRepository.findAllByUserLoginOrderByIdAsc("customer1")).thenReturn(
            List.of(new WishlistItem().id(1L).userLogin("customer1").productId("p1"))
        );

        mockMvc
            .perform(
                post("/internal/v1/account/wishlist/items")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"product_id\":\"p1\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true));

        mockMvc
            .perform(get("/internal/v1/account/wishlist").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.product_ids[0]").value("p1"));
    }

    @Test
    void wishlist_sync_mergesIds() throws Exception {
        when(wishlistItemRepository.findAllByUserLoginOrderByIdAsc("customer1")).thenReturn(
            List.of(new WishlistItem().id(1L).userLogin("customer1").productId("p1"))
        );
        when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(
                post("/internal/v1/account/wishlist/sync")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"product_ids\":[\"p1\",\"p2\"]}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.product_ids[0]").value("p1"))
            .andExpect(jsonPath("$.data.product_ids[1]").value("p2"));
    }

    @Test
    void updateAddress_returnsPersistedFields() throws Exception {
        CustomerAddress existing = new CustomerAddress()
            .id(8L)
            .userLogin("customer1")
            .label("Home")
            .fullName("Jane")
            .phone("9876543210")
            .line1("Old line")
            .city("City")
            .state("State")
            .pincode("560001")
            .isDefault(true);
        when(customerAddressRepository.findOneByIdAndUserLogin(8L, "customer1")).thenReturn(Optional.of(existing));
        when(customerAddressRepository.save(any(CustomerAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(
                put("/internal/v1/account/addresses/8")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "label":"Office",
                          "full_name":"Jane Doe",
                          "phone":"9876543210",
                          "line1":"New line",
                          "line2":"Suite 2",
                          "city":"Bengaluru",
                          "state":"Karnataka",
                          "pincode":"560001",
                          "is_default":true
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.address.id").value(8))
            .andExpect(jsonPath("$.data.address.label").value("Office"))
            .andExpect(jsonPath("$.data.address.full_name").value("Jane Doe"))
            .andExpect(jsonPath("$.data.address.line1").value("New line"))
            .andExpect(jsonPath("$.data.address.city").value("Bengaluru"));
    }

    @Test
    void wishlist_replace_overwritesIds() throws Exception {
        when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(
                put("/internal/v1/account/wishlist")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"product_ids\":[\"p2\",\"p3\"]}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.product_ids[0]").value("p2"))
            .andExpect(jsonPath("$.data.product_ids[1]").value("p3"));

        verify(wishlistItemRepository).deleteByUserLogin("customer1");
    }

    @Test
    void cart_getAndReplace() throws Exception {
        when(customerCartRepository.findOneByUserLogin("customer1")).thenReturn(Optional.empty());
        when(customerCartRepository.save(any(CustomerCart.class))).thenAnswer(inv -> {
            CustomerCart cart = inv.getArgument(0);
            cart.setId(11L);
            return cart;
        });

        mockMvc
            .perform(get("/internal/v1/account/cart").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc
            .perform(
                put("/internal/v1/account/cart")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "items":[{"product":{"id":"p1"},"qty":2,"size":"0-3m","color":"Cream","unitPrice":500}],
                          "coupon":{"code":"WELCOME10","discount":50}
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].product.id").value("p1"))
            .andExpect(jsonPath("$.data.items[0].qty").value(2))
            .andExpect(jsonPath("$.data.coupon.code").value("WELCOME10"));
    }
}
