package amiss.api.persistence.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data projection/mutation methods over {@link UserEntity} ({@code tbluser}),
 * mirroring the core's {@code JdbcUserRepository}'s SQL 1:1 (KAN-34). {@code name} is
 * the entity id, so single-row lookups by name use the inherited {@link #findById}.
 *
 * <p>Every {@code find*} projection returns {@link Optional#empty()} when no row
 * matches; {@link JpaUserRepository} (the port adapter) applies the same fallback
 * defaults the JDBC adapter always used. Every {@code @Modifying} query is a single
 * UPDATE statement — no read-modify-write — so each stays the atomic, one-statement
 * operation the JDBC version was.
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, String> {

    // ---- single-column reads -------------------------------------------------

    @Query("select u.password from UserEntity u where u.name = :name")
    Optional<String> findPasswordHash(@Param("name") String name);

    @Query("select u.xpos from UserEntity u where u.name = :name")
    Optional<Integer> findXpos(@Param("name") String name);

    @Query("select u.ypos from UserEntity u where u.name = :name")
    Optional<Integer> findYpos(@Param("name") String name);

    @Query("select u.time from UserEntity u where u.name = :name")
    Optional<Integer> findTime(@Param("name") String name);

    @Query("select u.round from UserEntity u where u.name = :name")
    Optional<Integer> findRound(@Param("name") String name);

    @Query("select u.cash from UserEntity u where u.name = :name")
    Optional<Integer> findCash(@Param("name") String name);

    @Query("select u.rent from UserEntity u where u.name = :name")
    Optional<Integer> findRent(@Param("name") String name);

    @Query("select u.debt from UserEntity u where u.name = :name")
    Optional<Integer> findDebt(@Param("name") String name);

    @Query("select u.eat from UserEntity u where u.name = :name")
    Optional<Integer> findEat(@Param("name") String name);

    @Query("select u.job from UserEntity u where u.name = :name")
    Optional<String> findJob(@Param("name") String name);

    @Query("select u.clothing from UserEntity u where u.name = :name")
    Optional<Integer> findClothing(@Param("name") String name);

    @Query("select u.bank from UserEntity u where u.name = :name")
    Optional<Integer> findBank(@Param("name") String name);

    /** {name, round} pairs for every player, highest round first. */
    @Query("select u.name, u.round from UserEntity u order by u.round desc")
    List<Object[]> highScores();

    // ---- writes ---------------------------------------------------------------

    @Modifying
    @Query("update UserEntity u set u.xpos = :xpos, u.ypos = :ypos where u.name = :name")
    void updatePosition(@Param("name") String name, @Param("xpos") int xpos, @Param("ypos") int ypos);

    @Modifying
    @Query("update UserEntity u set u.time = :time where u.name = :name")
    void updateTime(@Param("name") String name, @Param("time") int time);

    @Modifying
    @Query("update UserEntity u set u.round = :round where u.name = :name")
    void updateRound(@Param("name") String name, @Param("round") int round);

    @Modifying
    @Query("update UserEntity u set u.cash = :cash where u.name = :name")
    void updateCash(@Param("name") String name, @Param("cash") int cash);

    @Modifying
    @Query("update UserEntity u set u.rent = :rent where u.name = :name")
    void updateRent(@Param("name") String name, @Param("rent") int rent);

    @Modifying
    @Query("update UserEntity u set u.debt = u.debt + :amount where u.name = :name")
    void addDebt(@Param("name") String name, @Param("amount") int amount);

    @Modifying
    @Query("update UserEntity u set u.debt = u.debt - :amount where u.name = :name")
    void subtractDebt(@Param("name") String name, @Param("amount") int amount);

    @Modifying
    @Query("update UserEntity u set u.job = :job where u.name = :name")
    void updateJob(@Param("name") String name, @Param("job") String job);

    @Modifying
    @Query("update UserEntity u set u.clothing = :clothing where u.name = :name")
    void updateClothing(@Param("name") String name, @Param("clothing") int clothing);

    @Modifying
    @Query("update UserEntity u set u.eat = :eat where u.name = :name")
    void updateEat(@Param("name") String name, @Param("eat") int eat);

    @Modifying
    @Query("update UserEntity u set u.password = :password where u.name = :name")
    void updatePassword(@Param("name") String name, @Param("password") String password);

    /** Resets every saved-state column to the game's starting values (leaves password). */
    @Modifying
    @Query("update UserEntity u set u.xpos = 0, u.ypos = 2, u.time = 4320, u.cash = 100, u.round = 1, "
            + "u.job = 'Unemployed', u.clothing = 1, u.eat = 0, u.rent = 1, u.debt = 0, u.bank = 0 "
            + "where u.name = :name")
    void resetUser(@Param("name") String name);

    /**
     * Atomic conditional cash-to-bank transfer.
     * @return the number of rows updated (0 or 1) — 0 means insufficient cash.
     */
    @Modifying
    @Query("update UserEntity u set u.cash = u.cash - :amount, u.bank = u.bank + :amount "
            + "where u.name = :name and u.cash >= :amount")
    int depositToBank(@Param("name") String name, @Param("amount") int amount);

    /**
     * Atomic conditional bank-to-cash transfer.
     * @return the number of rows updated (0 or 1) — 0 means insufficient bank balance.
     */
    @Modifying
    @Query("update UserEntity u set u.cash = u.cash + :amount, u.bank = u.bank - :amount "
            + "where u.name = :name and u.bank >= :amount")
    int withdrawFromBank(@Param("name") String name, @Param("amount") int amount);
}
