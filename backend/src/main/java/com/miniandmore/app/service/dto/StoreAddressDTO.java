package com.miniandmore.app.service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.miniandmore.app.domain.enumeration.StoreAddressUsage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.StoreAddress} entity.
 */
public class StoreAddressDTO implements Serializable {

    private Long id;

    @Size(max = 80)
    private String label;

    @NotBlank
    @Size(min = 1, max = 120)
    private String fullName;

    @NotBlank
    @Size(min = 10, max = 20)
    private String phone;

    @NotBlank
    @Size(min = 5, max = 200)
    private String line1;

    @Size(max = 200)
    private String line2;

    @NotBlank
    @Size(min = 2, max = 80)
    private String city;

    @NotBlank
    @Size(min = 2, max = 80)
    private String state;

    @NotBlank
    @Size(min = 6, max = 6)
    private String pincode;

    @Size(max = 80)
    private String country;

    @NotNull
    private StoreAddressUsage usage;

    @JsonProperty("isDefault")
    @JsonAlias("is_default")
    private Boolean isDefault = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public String getLine1() {
        return line1;
    }

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public String getLine2() {
        return line2;
    }

    public void setLine2(String line2) {
        this.line2 = line2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public StoreAddressUsage getUsage() {
        return usage;
    }

    public void setUsage(StoreAddressUsage usage) {
        this.usage = usage;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StoreAddressDTO)) {
            return false;
        }
        StoreAddressDTO other = (StoreAddressDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public String toString() {
        return "StoreAddressDTO{" + "id=" + getId() + ", usage='" + getUsage() + "'" + "}";
    }
}
