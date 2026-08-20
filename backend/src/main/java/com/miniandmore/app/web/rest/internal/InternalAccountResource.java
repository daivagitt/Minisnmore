package com.miniandmore.app.web.rest.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerAddress;
import com.miniandmore.app.domain.CustomerCart;
import com.miniandmore.app.domain.WishlistItem;
import com.miniandmore.app.repository.CustomerAddressRepository;
import com.miniandmore.app.repository.CustomerCartRepository;
import com.miniandmore.app.repository.WishlistItemRepository;
import com.miniandmore.app.security.SecurityUtils;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/account")
public class InternalAccountResource {

    private static final int MAX_CART_ITEMS = 100;
    private static final int MAX_CART_JSON_CHARS = 400_000;

    private final CustomerAddressRepository customerAddressRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final CustomerCartRepository customerCartRepository;
    private final ObjectMapper objectMapper;

    public InternalAccountResource(
        CustomerAddressRepository customerAddressRepository,
        WishlistItemRepository wishlistItemRepository,
        CustomerCartRepository customerCartRepository,
        ObjectMapper objectMapper
    ) {
        this.customerAddressRepository = customerAddressRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.customerCartRepository = customerCartRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/addresses")
    public ApiResponse<Map<String, Object>> listAddresses(HttpServletResponse response) {
        noStore(response);
        String login = requireLogin();
        List<Map<String, Object>> addresses = customerAddressRepository
            .findAllByUserLoginOrderByIsDefaultDescIdAsc(login)
            .stream()
            .map(this::addressSummary)
            .toList();
        return ApiResponse.ok(Map.of("addresses", addresses));
    }

    @PostMapping("/addresses")
    @Transactional
    public ApiResponse<Map<String, Object>> createAddress(@Valid @RequestBody AddressUpsertRequest request) {
        String login = requireLogin();
        boolean first = customerAddressRepository.countByUserLogin(login) == 0;
        boolean makeDefault = Boolean.TRUE.equals(request.is_default()) || first;
        if (makeDefault) {
            clearDefaultAddress(login);
        }
        CustomerAddress saved = customerAddressRepository.save(fromRequest(new CustomerAddress().userLogin(login), request, makeDefault));
        customerAddressRepository.flush();
        return ApiResponse.ok(Map.of("address", addressSummary(saved)));
    }

    @PutMapping("/addresses/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateAddress(@PathVariable Long id, @Valid @RequestBody AddressUpsertRequest request) {
        String login = requireLogin();
        CustomerAddress address = requireOwnedAddress(id, login);
        boolean makeDefault = Boolean.TRUE.equals(request.is_default());
        if (makeDefault) {
            clearDefaultAddress(login);
        }
        CustomerAddress saved = customerAddressRepository.save(
            fromRequest(address, request, makeDefault || Boolean.TRUE.equals(address.getIsDefault()))
        );
        customerAddressRepository.flush();
        return ApiResponse.ok(Map.of("address", addressSummary(saved)));
    }

    @DeleteMapping("/addresses/{id}")
    @Transactional
    public ApiResponse<Map<String, Boolean>> deleteAddress(@PathVariable Long id) {
        String login = requireLogin();
        CustomerAddress address = requireOwnedAddress(id, login);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        customerAddressRepository.delete(address);
        if (wasDefault) {
            customerAddressRepository
                .findAllByUserLoginOrderByIsDefaultDescIdAsc(login)
                .stream()
                .findFirst()
                .ifPresent(next -> {
                    next.setIsDefault(true);
                    customerAddressRepository.save(next);
                });
        }
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PutMapping("/addresses/{id}/default")
    @Transactional
    public ApiResponse<Map<String, Object>> setDefaultAddress(@PathVariable Long id) {
        String login = requireLogin();
        CustomerAddress address = requireOwnedAddress(id, login);
        clearDefaultAddress(login);
        address.setIsDefault(true);
        CustomerAddress saved = customerAddressRepository.save(address);
        customerAddressRepository.flush();
        return ApiResponse.ok(Map.of("address", addressSummary(saved)));
    }

    /** Import device-local addresses once — skips rows that already match line1 + pincode + city. */
    @PostMapping("/addresses/sync")
    @Transactional
    public ApiResponse<Map<String, Object>> syncAddresses(@Valid @RequestBody AddressSyncRequest request) {
        String login = requireLogin();
        List<CustomerAddress> existing = customerAddressRepository.findAllByUserLoginOrderByIsDefaultDescIdAsc(login);
        boolean hasDefault = existing.stream().anyMatch(a -> Boolean.TRUE.equals(a.getIsDefault()));
        List<CustomerAddress> created = new ArrayList<>();
        for (AddressUpsertRequest row : request.addresses()) {
            if (row == null) {
                continue;
            }
            boolean duplicate = existing
                .stream()
                .anyMatch(
                    a ->
                        StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(a.getLine1()), StringUtils.trimToEmpty(row.line1())) &&
                        StringUtils.equals(StringUtils.trimToEmpty(a.getPincode()), StringUtils.trimToEmpty(row.pincode())) &&
                        StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(a.getCity()), StringUtils.trimToEmpty(row.city()))
                );
            if (duplicate) {
                continue;
            }
            boolean makeDefault = Boolean.TRUE.equals(row.is_default()) || (!hasDefault && created.isEmpty() && existing.isEmpty());
            if (makeDefault) {
                clearDefaultAddress(login);
                hasDefault = true;
            }
            CustomerAddress saved = customerAddressRepository.save(
                fromRequest(new CustomerAddress().userLogin(login), row, makeDefault)
            );
            created.add(saved);
            existing = customerAddressRepository.findAllByUserLoginOrderByIsDefaultDescIdAsc(login);
        }
        List<Map<String, Object>> addresses = customerAddressRepository
            .findAllByUserLoginOrderByIsDefaultDescIdAsc(login)
            .stream()
            .map(this::addressSummary)
            .toList();
        return ApiResponse.ok(Map.of("addresses", addresses));
    }

    @GetMapping("/wishlist")
    public ApiResponse<Map<String, Object>> listWishlist(HttpServletResponse response) {
        noStore(response);
        String login = requireLogin();
        return ApiResponse.ok(Map.of("product_ids", wishlistIds(login)));
    }

    @PostMapping("/wishlist/items")
    @Transactional
    public ApiResponse<Map<String, Boolean>> addWishlistItem(@Valid @RequestBody WishlistProductRequest request) {
        String login = requireLogin();
        String productId = StringUtils.trimToEmpty(request.product_id());
        if (wishlistItemRepository.existsByUserLoginAndProductId(login, productId)) {
            return ApiResponse.ok(Map.of("ok", true));
        }
        wishlistItemRepository.save(new WishlistItem().userLogin(login).productId(productId));
        return ApiResponse.ok(Map.of("ok", true));
    }

    @DeleteMapping("/wishlist/items/{productId}")
    @Transactional
    public ApiResponse<Map<String, Boolean>> removeWishlistItem(@PathVariable String productId) {
        String login = requireLogin();
        wishlistItemRepository.deleteByUserLoginAndProductId(login, StringUtils.trimToEmpty(productId));
        return ApiResponse.ok(Map.of("ok", true));
    }

    /** Merge guest/device wishlist ids into the account and return the full list. */
    @PostMapping("/wishlist/sync")
    @Transactional
    public ApiResponse<Map<String, Object>> syncWishlist(@Valid @RequestBody WishlistSyncRequest request) {
        String login = requireLogin();
        Set<String> ids = new LinkedHashSet<>(wishlistIds(login));
        for (String productId : request.product_ids()) {
            String normalized = StringUtils.trimToEmpty(productId);
            if (normalized.isEmpty() || ids.contains(normalized)) {
                continue;
            }
            wishlistItemRepository.save(new WishlistItem().userLogin(login).productId(normalized));
            ids.add(normalized);
        }
        return ApiResponse.ok(Map.of("product_ids", List.copyOf(ids)));
    }

    /** Replace the account wishlist with the given ids (additions and removals). */
    @PutMapping("/wishlist")
    @Transactional
    public ApiResponse<Map<String, Object>> replaceWishlist(@Valid @RequestBody WishlistSyncRequest request) {
        String login = requireLogin();
        Set<String> incoming = new LinkedHashSet<>();
        for (String productId : request.product_ids()) {
            String normalized = StringUtils.trimToEmpty(productId);
            if (!normalized.isEmpty()) {
                incoming.add(normalized);
            }
        }
        Set<String> existing = new LinkedHashSet<>(wishlistIds(login));
        for (String productId : existing) {
            if (!incoming.contains(productId)) {
                wishlistItemRepository.deleteByUserLoginAndProductId(login, productId);
            }
        }
        for (String productId : incoming) {
            if (!existing.contains(productId) && !wishlistItemRepository.existsByUserLoginAndProductId(login, productId)) {
                wishlistItemRepository.save(new WishlistItem().userLogin(login).productId(productId));
            }
        }
        return ApiResponse.ok(Map.of("product_ids", List.copyOf(incoming)));
    }

    @GetMapping("/cart")
    public ApiResponse<Map<String, Object>> getCart(HttpServletResponse response) {
        noStore(response);
        String login = requireLogin();
        CustomerCart cart = customerCartRepository.findOneByUserLogin(login).orElse(null);
        return ApiResponse.ok(cartPayload(cart));
    }

    @PutMapping("/cart")
    @Transactional
    public ApiResponse<Map<String, Object>> replaceCart(@Valid @RequestBody CartReplaceRequest request) {
        String login = requireLogin();
        List<?> items = request.items() == null ? List.of() : request.items();
        if (items.size() > MAX_CART_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is too large");
        }
        String itemsJson;
        try {
            itemsJson = objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart items are invalid");
        }
        if (itemsJson.length() > MAX_CART_JSON_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is too large");
        }
        String couponJson = null;
        if (request.coupon() != null) {
            try {
                couponJson = objectMapper.writeValueAsString(request.coupon());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon payload is invalid");
            }
            if (couponJson.length() > 4000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon payload is too large");
            }
        }

        CustomerCart cart = customerCartRepository.findOneByUserLogin(login).orElseGet(() -> new CustomerCart().userLogin(login));
        cart.setItemsJson(itemsJson);
        cart.setCouponJson(couponJson);
        CustomerCart saved = customerCartRepository.save(cart);
        customerCartRepository.flush();
        return ApiResponse.ok(cartPayload(saved));
    }

    private Map<String, Object> cartPayload(CustomerCart cart) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", readJsonValue(cart == null ? null : cart.getItemsJson(), List.of()));
        data.put("coupon", readJsonValue(cart == null ? null : cart.getCouponJson(), null));
        return data;
    }

    private Object readJsonValue(String raw, Object fallback) {
        if (StringUtils.isBlank(raw)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception e) {
            return fallback;
        }
    }

    private List<String> wishlistIds(String login) {
        return wishlistItemRepository.findAllByUserLoginOrderByIdAsc(login).stream().map(WishlistItem::getProductId).toList();
    }

    private CustomerAddress requireOwnedAddress(Long id, String login) {
        return customerAddressRepository
            .findOneByIdAndUserLogin(id, login)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    private void clearDefaultAddress(String login) {
        customerAddressRepository
            .findAllByUserLoginOrderByIsDefaultDescIdAsc(login)
            .forEach(address -> {
                if (Boolean.TRUE.equals(address.getIsDefault())) {
                    address.setIsDefault(false);
                    customerAddressRepository.save(address);
                }
            });
    }

    private CustomerAddress fromRequest(CustomerAddress address, AddressUpsertRequest request, boolean isDefault) {
        return address
            .label(StringUtils.trimToNull(request.label()))
            .fullName(StringUtils.trimToEmpty(request.full_name()))
            .phone(StringUtils.trimToEmpty(request.phone()))
            .line1(StringUtils.trimToEmpty(request.line1()))
            .line2(StringUtils.trimToNull(request.line2()))
            .city(StringUtils.trimToEmpty(request.city()))
            .state(StringUtils.trimToEmpty(request.state()))
            .pincode(StringUtils.trimToEmpty(request.pincode()))
            .isDefault(isDefault);
    }

    private Map<String, Object> addressSummary(CustomerAddress address) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", address.getId());
        data.put("label", StringUtils.defaultString(address.getLabel(), "Home"));
        data.put("full_name", address.getFullName());
        data.put("phone", address.getPhone());
        data.put("line1", address.getLine1());
        data.put("line2", StringUtils.defaultString(address.getLine2()));
        data.put("city", address.getCity());
        data.put("state", address.getState());
        data.put("pincode", address.getPincode());
        data.put("is_default", Boolean.TRUE.equals(address.getIsDefault()));
        return data;
    }

    private String requireLogin() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
    }

    public record AddressUpsertRequest(
        @Size(max = 40) String label,
        @NotBlank @Size(max = 120) String full_name,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String state,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String pincode,
        Boolean is_default
    ) {}

    public record AddressSyncRequest(@NotNull List<@Valid AddressUpsertRequest> addresses) {}

    public record WishlistProductRequest(@NotBlank @Size(max = 120) String product_id) {}

    public record WishlistSyncRequest(@NotNull List<@NotBlank @Size(max = 120) String> product_ids) {}

    public record CartReplaceRequest(@NotNull List<Object> items, Object coupon) {}
}
