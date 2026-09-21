package com.miniandmore.app.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.ContactMessage} entity.
 */
public class ContactMessageDTO implements Serializable {

    private Long id;

    @NotBlank
    @Size(min = 1, max = 120)
    private String name;

    @NotBlank
    @Email
    @Size(min = 3, max = 180)
    private String email;

    @NotBlank
    @Size(min = 10, max = 10)
    private String phone;

    @NotBlank
    @Size(min = 4, max = 1000)
    private String address;

    @NotBlank
    @Size(min = 4, max = 4000)
    private String comment;

    private Instant createdDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContactMessageDTO)) {
            return false;
        }
        ContactMessageDTO other = (ContactMessageDTO) o;
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
        return "ContactMessageDTO{" + "id=" + getId() + ", email='" + getEmail() + "'" + "}";
    }
}
