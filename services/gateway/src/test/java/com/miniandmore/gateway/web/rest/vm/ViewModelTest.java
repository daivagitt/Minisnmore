package com.miniandmore.gateway.web.rest.vm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ViewModelTest {

    @Test
    void loginVm_gettersAndSetters() {
        LoginVM vm = new LoginVM();
        vm.setUsername("user");
        vm.setPassword("pass");
        vm.setRememberMe(true);

        assertThat(vm.getUsername()).isEqualTo("user");
        assertThat(vm.getPassword()).isEqualTo("pass");
        assertThat(vm.isRememberMe()).isTrue();
    }

    @Test
    void keyAndPasswordVm_gettersAndSetters() {
        KeyAndPasswordVM vm = new KeyAndPasswordVM();
        vm.setKey("key");
        vm.setNewPassword("password");

        assertThat(vm.getKey()).isEqualTo("key");
        assertThat(vm.getNewPassword()).isEqualTo("password");
    }

    @Test
    void routeVm_gettersAndSetters() {
        RouteVM vm = new RouteVM();
        vm.setPath("/api");
        vm.setServiceId("backend");

        assertThat(vm.getPath()).isEqualTo("/api");
        assertThat(vm.getServiceId()).isEqualTo("backend");
    }

    @Test
    void managedUserVm_extendsAdminDto() {
        ManagedUserVM vm = new ManagedUserVM();
        vm.setLogin("user");
        vm.setPassword("password");
        vm.setSubscribed(true);

        assertThat(vm.getLogin()).isEqualTo("user");
        assertThat(vm.getSubscribed()).isTrue();
        assertThat(ManagedUserVM.PASSWORD_MIN_LENGTH).isPositive();
    }
}
