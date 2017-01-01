package dk.matzon.bwusage.infrastructure.persistence;

import dk.matzon.bwusage.domain.model.BWHistoricalEntry;
import org.hibernate.SessionFactory;

/**
 * Created by Brian Matzon <brian@matzon.dk>
 */
public class BWHistoricalEntryRepositoryImpl extends BWAbstractRepositoryImpl<BWHistoricalEntry> {
    public BWHistoricalEntryRepositoryImpl(SessionFactory _sessionFactory) {
        super(_sessionFactory, BWHistoricalEntry.class);
    }
}