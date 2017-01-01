package dk.matzon.bwusage.infrastructure.persistence;

import dk.matzon.bwusage.domain.Repository;
import dk.matzon.bwusage.domain.model.BWEntry;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by Brian Matzon <brian@matzon.dk>
 */
public class BWEntryRepositoryImpl implements Repository<BWEntry> {

    private final SessionFactory sessionFactory;

    public BWEntryRepositoryImpl(SessionFactory _sessionFactory) {
        sessionFactory = _sessionFactory;
    }

    @SuppressWarnings("unchecked")
    public List<BWEntry> findAll() {
        List<BWEntry> result = withTransactionableSession(new TransactionableSession<List<BWEntry>>() {
            @Override
            public List<BWEntry> execute(Session _session, Transaction _transaction) {
                Query query = _session.createQuery("from " + BWEntry.class.getName());
                List list = query.list();
                _transaction.commit();
                return list;
            }
        });

        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }

    public List<BWEntry> findByDate(final Date _from, final Date _to) {
        List<BWEntry> result = withTransactionableSession(new TransactionableSession<List<BWEntry>>() {
            @Override
            public List<BWEntry> execute(Session _session, Transaction _transaction) {
                Query query = _session.createQuery("from " + BWEntry.class.getName() + " where date BETWEEN :fromDate AND :endDate");
                query.setParameter("fromDate", _from);
                query.setParameter("endDate", _to);
                List list = query.list();
                _transaction.commit();
                return list;
            }
        });

        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }

    public BWEntry save(final BWEntry _entity) {
        return withTransactionableSession(new TransactionableSession<BWEntry>() {
            @Override
            public BWEntry execute(Session _session, Transaction _transaction) {
                _session.saveOrUpdate(_entity);
                _transaction.commit();
                return _entity;
            }
        });
    }

    public boolean delete(final BWEntry _entity) {
        return withTransactionableSession(new TransactionableSession<Boolean>() {
            @Override
            public Boolean execute(Session _session, Transaction _transaction) {
                _session.delete(_entity);
                _transaction.commit();
                return true;
            }
        });
    }

    @Override
    public boolean saveAll(final List<BWEntry> _entities) {
        return withTransactionableSession(new TransactionableSession<Boolean>() {
            @Override
            public Boolean execute(Session _session, Transaction _transaction) {
                // TODO: 01-01-2017 - batch insert/update
                for (BWEntry entry : _entities) {
                    _session.saveOrUpdate(entry);
                }
                _transaction.commit();
                return true;
            }
        });
    }

    private <T> T withTransactionableSession(TransactionableSession<? extends T> _transactionableSession) {
        Session currentSession = null;
        Transaction tx = null;
        T result = null;
        try {
            currentSession = sessionFactory.getCurrentSession();
            tx = currentSession.beginTransaction();
            result = _transactionableSession.execute(currentSession, tx);
        } catch (HibernateException _he) {
            if (tx != null) {
                tx.rollback();
            }
        } finally {
            if (currentSession != null) {
                currentSession.close();
            }
        }
        return result;
    }

    private interface TransactionableSession<T> {
        T execute(Session _session, Transaction _transaction);
    }
}
