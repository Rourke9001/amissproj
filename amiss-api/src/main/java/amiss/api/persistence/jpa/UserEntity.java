package amiss.api.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA mapping of {@code tbluser} (the account's credentials), validated against the
 * Flyway-owned schema ({@code ddl-auto=validate}, KAN-33/KAN-54).
 *
 * <p>{@code name} is the natural primary key — no surrogate id, matching the schema.
 * Post-V6, {@code tbluser} holds nothing but the credential pair: per-save game state
 * lives in {@code tblsave} ({@link SaveEntity}) instead.
 *
 * <p>Backed by {@link UserJpaRepository} / {@link JpaUserRepository}, the {@code
 * UserRepository} port adapter (KAN-34).
 */
@Entity
@Table(name = "tbluser")
public class UserEntity {

    @Id
    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "password", length = 60, nullable = false)
    private String password;

    protected UserEntity() {
        // JPA
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
