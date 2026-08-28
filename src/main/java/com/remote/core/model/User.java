package com.remote.core.model;

import com.remote.pc.model.Pc;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_users_email",
                        columnNames = "email"
                )
        }
)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 254)
    @Column(
            name = "email",
            nullable = false,
            length = 254
    )
    private String email;

    @NotBlank
    @Size(max = 100)
    @Column(
            name = "display_name",
            nullable = false,
            length = 100
    )
    private String displayName;

    @NotBlank
    @Size(max = 255)
    @Column(
            name = "password_hash",
            nullable = false,
            length = 255
    )
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "account_status",
            nullable = false,
            length = 30
    )
    private AccountStatus status =
            AccountStatus.EMAIL_NOT_VERIFIED;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(
            name = "password_changed_at",
            nullable = false
    )
    private Instant passwordChangedAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @OneToMany(mappedBy = "user")
    private List<Pc> pcs;

    /*
     * Временный compatibility-конструктор.
     *
     * Старый первый аргумент исторически назывался username,
     * но теперь фактически является email.
     */
    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (passwordChangedAt == null) {
            passwordChangedAt = now;
        }

        if (status == null) {
            status = AccountStatus.EMAIL_NOT_VERIFIED;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_USER")
        );
    }

    /*
     * Название метода определено интерфейсом UserDetails.
     *
     * В нашей системе principal теперь является email.
     */
    @Override
    public String getUsername() {
        return email;
    }

    /*
     * Временный compatibility-метод для старого backend-кода.
     *
     * Физического поля username и колонки username уже нет.
     */
    public void setUsername(String username) {
        this.email = username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /*
     * Временный compatibility-метод.
     *
     * Финальное Java-поле уже называется passwordHash.
     */
    public void setPassword(String password) {
        this.passwordHash = password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != AccountStatus.BLOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == AccountStatus.ACTIVE;
    }
}