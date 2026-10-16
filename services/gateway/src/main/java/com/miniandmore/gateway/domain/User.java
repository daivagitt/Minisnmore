package com.miniandmore.gateway.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("jhi_user")
public class User extends AbstractAuditingEntity<Long> implements Persistable<Long>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String LOGIN_REGEX = "^(?>[a-zA-Z0-9!$&*+=?^_`{|}~.-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*)|(?>[_.@A-Za-z0-9-]+)$";

    @Id
    private Long id;

    @NotNull
    @Pattern(regexp = LOGIN_REGEX)
    @Size(min = 1, max = 50)
    @Column("login")
    private String login;

    @JsonIgnore
    @NotNull
    @Size(min = 60, max = 60)
    @Column("password_hash")
    private String password;

    @Size(max = 50)
    @Column("first_name")
    private String firstName;

    @Size(max = 50)
    @Column("last_name")
    private String lastName;

    @Email
    @Size(min = 5, max = 254)
    @Column("email")
    private String email;

    @NotNull
    @Column("activated")
    private boolean activated = false;

    @NotNull
    @Column("subscribed")
    private boolean subscribed = false;

    @Size(min = 2, max = 10)
    @Column("lang_key")
    private String langKey;

    @Size(max = 256)
    @Column("image_url")
    private String imageUrl;

    @Size(max = 20)
    @Column("activation_key")
    @JsonIgnore
    private String activationKey;

    @Size(max = 20)
    @Column("reset_key")
    @JsonIgnore
    private String resetKey;

    @Column("reset_date")
    private Instant resetDate = null;

    @Transient
    private boolean isPersisted;

    @JsonIgnore
    @Transient
    private Set<Authority> authorities = new HashSet<>();

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login != null ? login.toLowerCase() : null;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }

    public String getLangKey() {
        return langKey;
    }

    public void setLangKey(String langKey) {
        this.langKey = langKey;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
    }

    public String getResetKey() {
        return resetKey;
    }

    public void setResetKey(String resetKey) {
        this.resetKey = resetKey;
    }

    public Instant getResetDate() {
        return resetDate;
    }

    public void setResetDate(Instant resetDate) {
        this.resetDate = resetDate;
    }

    public Set<Authority> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<Authority> authorities) {
        this.authorities = authorities;
    }

    public User setIsPersisted() {
        this.isPersisted = true;
        return this;
    }

    @Override
    public boolean isNew() {
        return !isPersisted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "User{login='" + login + "', email='" + email + "', activated=" + activated + "}";
    }
}
