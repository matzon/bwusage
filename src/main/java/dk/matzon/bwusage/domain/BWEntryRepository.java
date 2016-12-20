package dk.matzon.bwusage.domain;

import dk.matzon.bwusage.domain.model.BWEntry;

import java.util.Date;
import java.util.List;

/**
 * Created by Brian Matzon <brian@matzon.dk>
 */
public interface BWEntryRepository {
    List<BWEntry> findAll();

    List<BWEntry> findByDate(Date _from, Date _to);

    BWEntry save(BWEntry _entry);

    boolean delete(BWEntry _entry);
}
