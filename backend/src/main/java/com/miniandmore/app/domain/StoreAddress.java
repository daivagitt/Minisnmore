package com.miniandmore.app.domain;

import com.miniandmore.app.domain.enumeration.StoreAddressUsage;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "store_address")
public class StoreAddress implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Size(max = 80)
    @Column(name = "label", length = 80)
    private String label;

    @NotNull
    @Size(max = 120)
    @Column(name = "full_name", length = 120, nullable = false)
    private String fullName;

    @NotNull
    @Size(max = 20)
    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @NotNull
    @Size(max = 200)
    @Column(name = "line1", length = 200, nullable = false)
    private String line1;

    @Size(max = 200)
    @Column(name = "line2", length = 200)
    private String line2;

    @NotNull
    @Size(max = 80)
    @Column(name = "city", length = 80, nullable = false)
    private String city;

    @NotNull
    @Size(max = 80)
    @Column(name = "state", length = 80, nullable = false)
    private String state;

    @NotNull
    @Size(max = 6)
    @Column(name = "pincode", length = 6, nullable = false)
    private String pincode;

    @NotNull
    @Size(max = 80)
    @Column(name = "country", length = 80, nullable = false)
    private String country = "India";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "usage", length = 20, nullable = false)
    private StoreAddressUsage usage;

    @NotNull
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    public Long getId() {
        return id;
    }

    public StoreAddress id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public StoreAddress label(String label) {
        this.setLabel(label);
        return this;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getFullName() {
        return fullName;
    }

    public StoreAddress fullName(String fullName) {
        this.setFullName(fullName);
        return this;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public StoreAddress phone(String phone) {
        this.setPhone(phone);
        return this;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLine1() {
        return line1;
    }

    public StoreAddress line1(String line1) {
        this.setLine1(line1);
        return this;
    }

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public String getLine2() {
        return line2;
    }

    public StoreAddress line2(String line2) {
        this.setLine2(line2);
        return this;
    }

    public void setLine2(String line2) {
        this.line2 = line2;
    }

    public String getCity() {
        return city;
    }

    public StoreAddress city(String city) {
        this.setCity(city);
        return this;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public StoreAddress state(String state) {
        this.setState(state);
        return this;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPincode() {
        return pincode;
    }

    public StoreAddress pincode(String pincode) {
        this.setPincode(pincode);
        return this;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getCountry() {
        return country;
    }

    public StoreAddress country(String country) {
        this.setCountry(country);
        return this;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public StoreAddressUsage getUsage() {
        return usage;
    }

    public StoreAddress usage(StoreAddressUsage usage) {
        this.setUsage(usage);
        return this;
    }

    public void setUsage(StoreAddressUsage usage) {
        this.usage = usage;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public StoreAddress isDefault(Boolean isDefault) {
        this.setIsDefault(isDefault);
        return this;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StoreAddress)) {
            return false;
        }
        return getId() != null && getId().equals(((StoreAddress) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "StoreAddress{" + "id=" + getId() + ", usage='" + getUsage() + "'" + "}";
    }
}
