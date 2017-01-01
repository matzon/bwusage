package dk.matzon.bwusage.infrastructure.persistence;

import dk.matzon.bwusage.domain.model.BWEntry;
import org.hibernate.SessionFactory;

/**
 * Created by Brian Matzon <brian@matzon.dk>
 */
public class BWEntryRepositoryImpl extends BWAbstractRepositoryImpl<BWEntry> {

    public BWEntryRepositoryImpl(SessionFactory _sessionFactory) {
        super(_sessionFactory, BWEntry.class);
    }
}
