package com.miniandmore.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * A storefront contact / communication message.
 */
@Entity
@Table(name = "contact_message")
public class ContactMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 120)
    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @NotNull
    @Size(min = 3, max = 180)
    @Column(name = "email", length = 180, nullable = false)
    private String email;

    @NotNull
    @Size(min = 10, max = 10)
    @Column(name = "phone", length = 10, nullable = false)
    private String phone;

    @NotNull
    @Size(min = 4, max = 1000)
    @Column(name = "address", columnDefinition = "text", nullable = false)
    private String address;

    @NotNull
    @Size(min = 4, max = 4000)
    @Column(name = "comment", columnDefinition = "text", nullable = false)
    private String comment;

    @NotNull
    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    public Long getId() {
        return this.id;
    }

    public ContactMessage id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public ContactMessage name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return this.email;
    }

    public ContactMessage email(String email) {
        this.setEmail(email);
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return this.phone;
    }

    public ContactMessage phone(String phone) {
        this.setPhone(phone);
        return this;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return this.address;
    }

    public ContactMessage address(String address) {
        this.setAddress(address);
        return this;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getComment() {
        return this.comment;
    }

    public ContactMessage comment(String comment) {
        this.setComment(comment);
        return this;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreatedDate() {
        return this.createdDate;
    }

    public ContactMessage createdDate(Instant createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContactMessage)) {
            return false;
        }
        return getId() != null && getId().equals(((ContactMessage) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ContactMessage{" + "id=" + getId() + ", email='" + getEmail() + "'" + "}";
    }
}
