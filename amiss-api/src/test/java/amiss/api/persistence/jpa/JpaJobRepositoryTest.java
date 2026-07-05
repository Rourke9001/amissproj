package amiss.api.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import amiss.application.port.PersistenceFailureException;
import amiss.domain.model.JobListing;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests for {@link JpaJobRepository} over a mocked {@link JobJpaRepository} — no
 * database. Covers the missing-job fallbacks the JDBC adapter always used, the
 * entity-to-{@link JobListing} mapping, and exception translation (KAN-34).
 */
@ExtendWith(MockitoExtension.class)
class JpaJobRepositoryTest {

    @Mock
    private JobJpaRepository jobs;

    private JpaJobRepository adapter() {
        return new JpaJobRepository(jobs);
    }

    @Test
    void getRequiredEducation_returnsMinusOne_whenUnknown() {
        when(jobs.findRequiredEducation("Ghost")).thenReturn(Optional.empty());
        assertEquals(-1, adapter().getRequiredEducation("Ghost"));
    }

    @Test
    void getRequiredEducation_returnsStoredValue_whenKnown() {
        when(jobs.findRequiredEducation("Chef")).thenReturn(Optional.of(2));
        assertEquals(2, adapter().getRequiredEducation("Chef"));
    }

    @Test
    void getSalary_returnsMinusOne_whenUnknown() {
        when(jobs.findSalary("Ghost")).thenReturn(Optional.empty());
        assertEquals(-1, adapter().getSalary("Ghost"));
    }

    @Test
    void getSalary_returnsStoredValue_whenKnown() {
        when(jobs.findSalary("Chef")).thenReturn(Optional.of(20));
        assertEquals(20, adapter().getSalary("Chef"));
    }

    @Test
    void getLocation_returnsNull_whenUnknown() {
        when(jobs.findLocation("Ghost")).thenReturn(Optional.empty());
        assertNull(adapter().getLocation("Ghost"));
    }

    @Test
    void getLocation_returnsStoredValue_whenKnown() {
        when(jobs.findLocation("Chef")).thenReturn(Optional.of("Restaurant"));
        assertEquals("Restaurant", adapter().getLocation("Chef"));
    }

    @Test
    void getRequiredClothing_returnsNull_whenUnknown() {
        when(jobs.findRequiredClothing("Ghost")).thenReturn(Optional.empty());
        assertNull(adapter().getRequiredClothing("Ghost"));
    }

    @Test
    void getRequiredClothing_returnsStringifiedInt_whenKnown() {
        when(jobs.findRequiredClothing("Chef")).thenReturn(Optional.of(2));
        assertEquals("2", adapter().getRequiredClothing("Chef"));
    }

    @Test
    void listAll_mapsEntitiesToJobListingsPreservingOrder() {
        JobEntity chef = new JobEntity();
        chef.setJob("Chef");
        chef.setEducation(2);
        chef.setSalary(20);
        chef.setLocation("Restaurant");
        chef.setClothing(2);
        JobEntity clerk = new JobEntity();
        clerk.setJob("Clerk");
        clerk.setEducation(1);
        clerk.setSalary(10);
        clerk.setLocation("Shop");
        clerk.setClothing(1);
        when(jobs.findAllOrdered()).thenReturn(List.of(chef, clerk));

        List<JobListing> listing = adapter().listAll();

        assertEquals(2, listing.size());
        assertEquals(new JobListing("Chef", 2, 20, "Restaurant", 2), listing.get(0));
        assertEquals(new JobListing("Clerk", 1, 10, "Shop", 1), listing.get(1));
    }

    @Test
    void read_translatesDataAccessException() {
        when(jobs.findSalary(anyString())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(PersistenceFailureException.class, () -> adapter().getSalary("Chef"));
    }

    @Test
    void read_translatesPersistenceExceptionThatEscapesUnwrapped() {
        when(jobs.findAllOrdered()).thenThrow(new PersistenceException("boom"));

        assertThrows(PersistenceFailureException.class, () -> adapter().listAll());
    }
}
