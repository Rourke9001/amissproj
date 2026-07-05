package amiss.api.persistence.jpa;

import amiss.application.port.PersistenceFailureException;
import amiss.application.port.UserRepository;
import amiss.domain.model.User;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link UserRepository} ({@code tbluser}), delegating to
 * {@link UserJpaRepository} (KAN-34). Reproduces the core's {@code
 * JdbcUserRepository}'s exact missing-row fallbacks and bank-transfer semantics;
 * each mutating method is its own transaction, mirroring the JDBC adapter's
 * autocommit-per-call behaviour (no broader transaction spans multiple port calls).
 *
 * <p>Not a Spring stereotype bean: wired explicitly by {@code
 * amiss.api.config.PersistenceConfig} so exactly one {@link UserRepository} bean
 * exists, the same way the JDBC adapter was.
 */
public class JpaUserRepository implements UserRepository {

    private final UserJpaRepository users;

    public JpaUserRepository(UserJpaRepository users) {
        this.users = users;
    }

    // ---- reads -------------------------------------------------------------

    @Override
    public Optional<String> findPasswordHash(String name) {
        return translate("find password hash", () -> users.findPasswordHash(name));
    }

    @Override
    public Optional<User> findByName(String name) {
        return translate("find user", () -> users.findById(name).map(JpaUserRepository::toDomain));
    }

    @Override
    public int getXpos(String name) {
        return translate("get xpos", () -> users.findXpos(name).orElse(0));
    }

    @Override
    public int getYpos(String name) {
        return translate("get ypos", () -> users.findYpos(name).orElse(0));
    }

    @Override
    public int getTime(String name) {
        return translate("get time", () -> users.findTime(name).orElse(-1));
    }

    @Override
    public int getRound(String name) {
        return translate("get round", () -> users.findRound(name).orElse(-1));
    }

    @Override
    public int getCash(String name) {
        return translate("get cash", () -> users.findCash(name).orElse(-1));
    }

    @Override
    public int getRent(String name) {
        return translate("get rent", () -> users.findRent(name).orElse(-1));
    }

    @Override
    public int getDebt(String name) {
        return translate("get debt", () -> users.findDebt(name).orElse(-1));
    }

    @Override
    public int getEat(String name) {
        return translate("get eat", () -> users.findEat(name).orElse(0));
    }

    @Override
    public String getJob(String name) {
        return translate("get job", () -> users.findJob(name).orElse(null));
    }

    @Override
    public String getUserClothing(String name) {
        return translate("get clothing", () -> users.findClothing(name).map(String::valueOf).orElse(null));
    }

    @Override
    public List<String[]> highScores() {
        return translate("high scores", () -> users.highScores().stream()
                .map(row -> new String[]{(String) row[0], String.valueOf(row[1])})
                .toList());
    }

    @Override
    public int getBank(String name) {
        return translate("get bank", () -> users.findBank(name).orElse(-1));
    }

    // ---- writes --------------------------------------------------------------

    @Override
    @Transactional
    public void updatePosition(String name, int xpos, int ypos) {
        translateRun("update position", () -> users.updatePosition(name, xpos, ypos));
    }

    @Override
    @Transactional
    public void updateTime(String name, int time) {
        translateRun("update time", () -> users.updateTime(name, time));
    }

    @Override
    @Transactional
    public void updateRound(String name, int round) {
        translateRun("update round", () -> users.updateRound(name, round));
    }

    @Override
    @Transactional
    public void updateCash(String name, int cash) {
        translateRun("update cash", () -> users.updateCash(name, cash));
    }

    @Override
    @Transactional
    public void updateRent(String name, int rent) {
        translateRun("update rent", () -> users.updateRent(name, rent));
    }

    @Override
    @Transactional
    public void addDebt(String name, int amount) {
        translateRun("add debt", () -> users.addDebt(name, amount));
    }

    @Override
    @Transactional
    public void subtractDebt(String name, int amount) {
        translateRun("subtract debt", () -> users.subtractDebt(name, amount));
    }

    @Override
    @Transactional
    public void updateJob(String name, String job) {
        translateRun("update job", () -> users.updateJob(name, job));
    }

    @Override
    @Transactional
    public void updateClothing(String name, int clothing) {
        translateRun("update clothing", () -> users.updateClothing(name, clothing));
    }

    @Override
    @Transactional
    public void updateEat(String name, int eat) {
        translateRun("update eat", () -> users.updateEat(name, eat));
    }

    @Override
    @Transactional
    public void updatePassword(String name, String passwordHash) {
        translateRun("update password", () -> users.updatePassword(name, passwordHash));
    }

    /**
     * Inserts a brand-new player with the game's starting values — the same defaults
     * {@code JdbcUserRepository#insertNewUser}'s column-list INSERT used ({@code bank}
     * falls back to its schema default of 0, matching the JDBC adapter's omission of it).
     */
    @Override
    @Transactional
    public void insertNewUser(String name, String passwordHash) {
        translateRun("insert new user", () -> {
            UserEntity user = new UserEntity();
            user.setName(name);
            user.setPassword(passwordHash);
            user.setXpos(0);
            user.setYpos(2);
            user.setTime(4320);
            user.setCash(100);
            user.setRound(1);
            user.setJob("Unemployed");
            user.setClothing(1);
            user.setRent(1);
            user.setEat(0);
            user.setDebt(0);
            user.setBank(0);
            users.save(user);
        });
    }

    @Override
    @Transactional
    public void resetUser(String name) {
        translateRun("reset user", () -> users.resetUser(name));
    }

    // ---- bank ------------------------------------------------------------------

    @Override
    @Transactional
    public boolean depositToBank(String name, int amount) {
        return translate("deposit to bank", () -> users.depositToBank(name, amount) == 1);
    }

    @Override
    @Transactional
    public boolean withdrawFromBank(String name, int amount) {
        return translate("withdraw from bank", () -> users.withdrawFromBank(name, amount) == 1);
    }

    private static User toDomain(UserEntity e) {
        return new User(e.getName(), e.getXpos(), e.getYpos(), e.getTime(), e.getCash(), e.getRound(),
                e.getJob(), e.getClothing(), e.getRent(), e.getEat(), e.getDebt());
    }

    /** Runs {@code call}, translating any persistence failure into {@link PersistenceFailureException}. */
    private <T> T translate(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException | PersistenceException e) {
            throw new PersistenceFailureException("Failed to " + operation + " (tbluser)", e);
        }
    }

    private void translateRun(String operation, Runnable call) {
        translate(operation, () -> {
            call.run();
            return null;
        });
    }
}
