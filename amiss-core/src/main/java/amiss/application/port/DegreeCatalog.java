package amiss.application.port;

import amiss.domain.model.DegreeSpec;
import java.util.List;
import java.util.Optional;

/** Read-only port over the {@code tbldegrees} catalog (KAN-53). */
public interface DegreeCatalog {

    List<DegreeSpec> all();

    Optional<DegreeSpec> byId(int degreeId);
}
