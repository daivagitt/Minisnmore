package com.miniandmore.gateway.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.security.AuthoritiesConstants;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdminUserDTOTest {

    @Test
    void constructorFromUser_mapsFieldsAndPendingInviteWhenActivationKeyPresent() {
        User user = new User();
        user.setId(42L);
        user.setLogin("staff1");
        user.setFirstName("Staff");
        user.setLastName("One");
        user.setEmail("staff1@localhost");
        user.setActivated(false);
        user.setActivationKey("invite-key");
        user.setSubscribed(true);
        user.setImageUrl("https://cdn.example.com/a.png");
        user.setLangKey("en");
        user.setCreatedBy("system");
        user.setCreatedDate(Instant.parse("2026-01-01T00:00:00Z"));
        user.setLastModifiedBy("admin");
        user.setLastModifiedDate(Instant.parse("2026-02-01T00:00:00Z"));
        Authority sales = new Authority();
        sales.setName(AuthoritiesConstants.SALES);
        user.setAuthorities(Set.of(sales));

        AdminUserDTO dto = new AdminUserDTO(user);

        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getLogin()).isEqualTo("staff1");
        assertThat(dto.getEmail()).isEqualTo("staff1@localhost");
        assertThat(dto.isActivated()).isFalse();
        assertThat(dto.isPendingInvite()).isTrue();
        assertThat(dto.getSubscribed()).isTrue();
        assertThat(dto.getAuthorities()).containsExactly(AuthoritiesConstants.SALES);
        assertThat(dto.toString()).contains("staff1").contains("SALES");
    }

    @Test
    void settersAndGetters_roundTripScalarFields() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(1L);
        dto.setLogin("user");
        dto.setFirstName("First");
        dto.setLastName("Last");
        dto.setEmail("user@localhost");
        dto.setImageUrl("img");
        dto.setActivated(true);
        dto.setPendingInvite(false);
        dto.setSubscribed(false);
        dto.setLangKey("hi");
        dto.setCreatedBy("creator");
        dto.setCreatedDate(Instant.EPOCH);
        dto.setLastModifiedBy("editor");
        dto.setLastModifiedDate(Instant.EPOCH);
        dto.setAuthorities(Set.of(AuthoritiesConstants.USER));

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getLogin()).isEqualTo("user");
        assertThat(dto.getFirstName()).isEqualTo("First");
        assertThat(dto.getLastName()).isEqualTo("Last");
        assertThat(dto.getEmail()).isEqualTo("user@localhost");
        assertThat(dto.getImageUrl()).isEqualTo("img");
        assertThat(dto.isActivated()).isTrue();
        assertThat(dto.isPendingInvite()).isFalse();
        assertThat(dto.getSubscribed()).isFalse();
        assertThat(dto.getLangKey()).isEqualTo("hi");
        assertThat(dto.getCreatedBy()).isEqualTo("creator");
        assertThat(dto.getCreatedDate()).isEqualTo(Instant.EPOCH);
        assertThat(dto.getLastModifiedBy()).isEqualTo("editor");
        assertThat(dto.getLastModifiedDate()).isEqualTo(Instant.EPOCH);
        assertThat(dto.getAuthorities()).containsExactly(AuthoritiesConstants.USER);
    }
}
