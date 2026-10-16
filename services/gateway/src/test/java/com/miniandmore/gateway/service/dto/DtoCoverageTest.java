package com.miniandmore.gateway.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.gateway.domain.User;
import org.junit.jupiter.api.Test;

class DtoCoverageTest {

    @Test
    void adminUserDto_fromUserAndSetters() {
        User user = new User();
        user.setLogin("user");
        user.setEmail("user@localhost");
        AdminUserDTO dto = new AdminUserDTO(user);
        dto.setFirstName("John");

        assertThat(dto.getLogin()).isEqualTo("user");
        assertThat(dto.getFirstName()).isEqualTo("John");
    }

    @Test
    void userDto_fromUser() {
        User user = new User();
        user.setLogin("user");
        user.setId(1L);

        UserDTO dto = new UserDTO(user);

        assertThat(dto.getLogin()).isEqualTo("user");
        assertThat(dto.getId()).isEqualTo(1L);
    }

    @Test
    void userDto_equalsAndHashCode() {
        UserDTO first = new UserDTO();
        first.setId(1L);
        first.setLogin("user");
        UserDTO second = new UserDTO();
        second.setId(1L);
        second.setLogin("user");
        UserDTO other = new UserDTO();
        other.setId(2L);
        other.setLogin("other");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(other).isNotEqualTo(null).isNotEqualTo("user");
        assertThat(first.toString()).contains("user");
    }

    @Test
    void passwordChangeDto_gettersAndSetters() {
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setCurrentPassword("old");
        dto.setNewPassword("new");

        assertThat(dto.getCurrentPassword()).isEqualTo("old");
        assertThat(dto.getNewPassword()).isEqualTo("new");
    }

    @Test
    void passwordChangeDto_constructorWithValues() {
        PasswordChangeDTO dto = new PasswordChangeDTO("old", "new");

        assertThat(dto.getCurrentPassword()).isEqualTo("old");
        assertThat(dto.getNewPassword()).isEqualTo("new");
    }
}
