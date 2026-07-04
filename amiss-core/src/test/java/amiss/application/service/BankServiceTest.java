package amiss.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import amiss.application.port.UserRepository;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BankService}. The repository is mocked; the conditional
 * deposit/withdraw update is a single atomic statement, so "insufficient funds" is
 * expressed as the port returning {@code false} rather than a separate cash/bank check.
 */
@ExtendWith(MockitoExtension.class)
class BankServiceTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;

    private BankService service() {
        return new BankService(users, USER);
    }

    @Test
    void balance_returnsTheStoredBalance() throws SQLException {
        when(users.getBank(USER)).thenReturn(250);
        assertEquals(250, service().balance());
    }

    @Test
    void balance_returnsMinusOneOnSqlException() throws SQLException {
        when(users.getBank(USER)).thenThrow(new SQLException("boom"));
        assertEquals(-1, service().balance());
    }

    @Test
    void deposit_rejectsNonPositiveAmountsWithoutTouchingTheRepository() throws SQLException {
        BankTransaction result = service().deposit(0);

        assertEquals(BankTransaction.Status.INVALID_AMOUNT, result.status());
        verifyNoInteractions(users);
    }

    @Test
    void deposit_negativeAmountIsAlsoInvalid() throws SQLException {
        BankTransaction result = service().deposit(-5);

        assertEquals(BankTransaction.Status.INVALID_AMOUNT, result.status());
        verifyNoInteractions(users);
    }

    @Test
    void deposit_movesMoneyAndReReadsBothBalancesOnSuccess() throws SQLException {
        when(users.depositToBank(USER, 50)).thenReturn(true);
        when(users.getCash(USER)).thenReturn(70);
        when(users.getBank(USER)).thenReturn(50);

        BankTransaction result = service().deposit(50);

        assertEquals(new BankTransaction(BankTransaction.Status.OK, 70, 50), result);
        verify(users).depositToBank(USER, 50);
    }

    @Test
    void deposit_insufficientCashReportsCurrentBalancesUnchanged() throws SQLException {
        when(users.depositToBank(USER, 500)).thenReturn(false);
        when(users.getCash(USER)).thenReturn(20);
        when(users.getBank(USER)).thenReturn(0);

        BankTransaction result = service().deposit(500);

        assertEquals(new BankTransaction(BankTransaction.Status.INSUFFICIENT_FUNDS, 20, 0), result);
    }

    @Test
    void deposit_sqlExceptionIsReportedAsFailed() throws SQLException {
        when(users.depositToBank(USER, 50)).thenThrow(new SQLException("boom"));

        BankTransaction result = service().deposit(50);

        assertEquals(BankTransaction.Status.FAILED, result.status());
    }

    @Test
    void withdraw_movesMoneyAndReReadsBothBalancesOnSuccess() throws SQLException {
        when(users.withdrawFromBank(USER, 30)).thenReturn(true);
        when(users.getCash(USER)).thenReturn(130);
        when(users.getBank(USER)).thenReturn(20);

        BankTransaction result = service().withdraw(30);

        assertEquals(new BankTransaction(BankTransaction.Status.OK, 130, 20), result);
        verify(users).withdrawFromBank(USER, 30);
        verify(users, never()).depositToBank(USER, 30);
    }

    @Test
    void withdraw_insufficientBankBalanceIsReported() throws SQLException {
        when(users.withdrawFromBank(USER, 999)).thenReturn(false);
        when(users.getCash(USER)).thenReturn(100);
        when(users.getBank(USER)).thenReturn(10);

        BankTransaction result = service().withdraw(999);

        assertEquals(BankTransaction.Status.INSUFFICIENT_FUNDS, result.status());
    }
}
