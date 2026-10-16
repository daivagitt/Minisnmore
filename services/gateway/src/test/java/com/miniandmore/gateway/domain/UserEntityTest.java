package com.miniandmore.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.gateway.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class UserEntityTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(User.class);
        User user1 = new User();
        user1.setId(1L);
        User user2 = new User();
        user2.setId(user1.getId());
        assertThat(user1).isEqualTo(user2);
        user2.setId(2L);
        assertThat(user1).isNotEqualTo(user2);
        user1.setId(null);
        assertThat(user1).isNotEqualTo(user2);
    }

    @Test
    void loginIsLowerCased() {
        User user = new User();
        user.setLogin("LoGiN");
        assertThat(user.getLogin()).isEqualTo("login");
    }
}
