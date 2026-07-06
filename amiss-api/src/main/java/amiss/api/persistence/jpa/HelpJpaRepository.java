package amiss.api.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data lookup over {@link HelpEntity} ({@code tblhelp}, KAN-34). {@code topic} is
 * both the entity id and the JDBC adapter's lookup key, so the inherited {@link #findById}
 * already matches the core's {@code JdbcHelpRepository#findDescription} 1:1 — no
 * custom query needed.
 */
public interface HelpJpaRepository extends JpaRepository<HelpEntity, String> {
}
