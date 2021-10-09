package dk.matzon.bwusage.infrastructure.persistence;

import dk.matzon.bwusage.domain.Repository;
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
public abstract class BWAbstractRepositoryImpl<T> implements Repository<T> {

    private final SessionFactory sessionFactory;
    private final Class<T> clazz;

    public BWAbstractRepositoryImpl(SessionFactory _sessionFactory, Class<T> _class) {
        sessionFactory = _sessionFactory;
        clazz = _class;
    }

    public List<T> findAll() {
        List<T> result = withTransactionableSession((_session, _transaction) -> {
            Query<T> query = _session.createQuery("from " + clazz.getName(), clazz);
            List<T> list = query.list();
            _transaction.commit();
            return list;
        });

        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }

    public List<T> findByDate(final Date _from, final Date _to) {
        List<T> result = withTransactionableSession((_session, _transaction) -> {
            Query<T> query = _session.createQuery("from " + clazz.getName() + " where date BETWEEN :fromDate AND :endDate", clazz);
            query.setParameter("fromDate", _from);
            query.setParameter("endDate", _to);
            List<T> list = query.list();
            _transaction.commit();
            return list;
        });

        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }

    public T save(final T _entity) {
        return withTransactionableSession((_session, _transaction) -> {
            _session.saveOrUpdate(_entity);
            _transaction.commit();
            return _entity;
        });
    }

    public boolean delete(final T _entity) {
        return withTransactionableSession((_session, _transaction) -> {
            _session.delete(_entity);
            _transaction.commit();
            return true;
        });
    }

    @Override
    public boolean saveAll(final List<T> _entities) {
        return withTransactionableSession((_session, _transaction) -> {
            // TODO: 01-01-2017 - batch insert/update
            for (T entry : _entities) {
                _session.saveOrUpdate(entry);
            }
            _transaction.commit();
            return true;
        });
    }

    private <S> S withTransactionableSession(TransactionableSession<S> _transactionableSession) {
        Session currentSession = null;
        Transaction tx = null;
        S result = null;
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

    private interface TransactionableSession<S> {
        S execute(Session _session, Transaction _transaction);
    }
}
