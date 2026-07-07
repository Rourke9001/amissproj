package amiss.api.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import amiss.application.port.JobRepository;
import amiss.domain.model.JobListing;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for {@link JpaJobRepository} (the {@link JobRepository} port,
 * {@code tbljobs}) against a real, freshly-migrated MySQL container (KAN-35). {@code tbljobs}
 * is reference data owned by {@code V2__seed_reference_data.sql} — read-only, so no
 * {@code @AfterEach} cleanup is needed here. See {@link MySqlITSupport} for the
 * container/transaction wiring.
 */
class JobRepositoryIT extends MySqlITSupport {

    @Autowired
    private JobRepository jobs;

    @Test
    void listAll_returnsAllSeededJobs_orderedByLocationThenEducation() {
        List<JobListing> all = jobs.listAll();

        assertEquals(17, all.size());
        // "Bank" sorts first alphabetically among the six locations, and within it
        // education 0 is the lowest — Bank Janitor is unambiguously first.
        JobListing first = all.get(0);
        assertEquals("Bank Janitor", first.name());
        assertEquals("Bank", first.location());
        assertEquals(0, first.requiredEducation());
        // "Socket City" sorts last, and within it Store Manager (education 3) is the
        // only job at the group's highest education — unambiguously last.
        JobListing last = all.get(all.size() - 1);
        assertEquals("Store Manager", last.name());
        assertEquals("Socket City", last.location());
        assertEquals(3, last.requiredEducation());
    }

    @Test
    void getters_matchTheSeededValues_forAKnownJob() {
        assertEquals(3, jobs.getRequiredEducation("Broker"));
        assertEquals(40, jobs.getSalary("Broker"));
        assertEquals("Bank", jobs.getLocation("Broker"));
        assertEquals("3", jobs.getRequiredClothing("Broker"));
    }

    @Test
    void getters_fallBackForAnUnknownJob() {
        String unknown = "Not A Real Job";

        assertEquals(-1, jobs.getRequiredEducation(unknown));
        assertEquals(-1, jobs.getSalary(unknown));
        assertNull(jobs.getLocation(unknown));
        assertNull(jobs.getRequiredClothing(unknown));
    }
}
