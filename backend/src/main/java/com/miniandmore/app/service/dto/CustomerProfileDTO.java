package com.miniandmore.app.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.CustomerProfile} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CustomerProfileDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 50)
    private String userLogin;

    @Size(max = 120)
    private String fullName;

    @Size(max = 20)
    private String phone;

    @NotNull
    private Boolean subscribed;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Boolean getSubscribed() {
        return subscribed;
    }

    public void setSubscribed(Boolean subscribed) {
        this.subscribed = subscribed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CustomerProfileDTO)) {
            return false;
        }

        CustomerProfileDTO customerProfileDTO = (CustomerProfileDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, customerProfileDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CustomerProfileDTO{" + "id=" + getId() + ", userLogin='" + getUserLogin() + "'" + "}";
    }
}
