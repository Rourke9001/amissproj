package amiss.api.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA mapping of {@code tblhelp} (the in-game help text, keyed by topic), validated
 * against the Flyway-owned schema ({@code ddl-auto=validate}, KAN-33). Reference
 * data — the game never inserts or updates rows in this table.
 *
 * <p>{@code topic} is the natural primary key — no surrogate id, matching the schema.
 *
 * <p>Backed by {@link HelpJpaRepository} / {@link JpaHelpRepository}, the {@code
 * HelpRepository} port adapter (KAN-34). The Swing client still reads {@code tblhelp}
 * through the core's {@code JdbcHelpRepository}.
 */
@Entity
@Table(name = "tblhelp")
public class HelpEntity {

    @Id
    @Column(name = "topic", length = 50)
    private String topic;

    @Column(name = "description", nullable = false)
    private String description;

    protected HelpEntity() {
        // JPA
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
