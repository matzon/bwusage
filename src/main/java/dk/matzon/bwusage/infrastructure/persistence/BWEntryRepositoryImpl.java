package dk.matzon.bwusage.infrastructure.persistence;

import dk.matzon.bwusage.domain.BWEntryRepository;
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
public class BWEntryRepositoryImpl implements BWEntryRepository {

    SessionFactory sessionFactory;

    public BWEntryRepositoryImpl(SessionFactory _sessionFactory) {
        sessionFactory = _sessionFactory;
    }

    @SuppressWarnings("unchecked")
    public List<BWEntry> findAll() {
        Session currentSession = null;
        Transaction tx = null;
        try {
            currentSession = sessionFactory.getCurrentSession();
            tx = currentSession.beginTransaction();
            Query query = currentSession.createQuery("from " + BWEntry.class.getName());
            List list = query.list();
            tx.commit();
            return list;
        } catch (HibernateException _he) {
            _he.printStackTrace();
            if (tx != null) {
                tx.rollback();
            }
        } finally {
            if (currentSession != null) {
                currentSession.close();
            }
        }
        return Collections.emptyList();
    }

    public List<BWEntry> findByDate(Date _from, Date _to) {
        Session currentSession = null;
        Transaction tx = null;
        try {
            currentSession = sessionFactory.getCurrentSession();
            tx = currentSession.beginTransaction();
            Query query = currentSession.createQuery("from " + BWEntry.class.getName() + " where date BETWEEN :fromDate AND :endDate");
            query.setParameter("fromDate", _from);
            query.setParameter("endDate", _to);
            List list = query.list();
            tx.commit();
            return list;
        } catch (HibernateException _he) {
            _he.printStackTrace();
            if (tx != null) {
                tx.rollback();
            }
        } finally {
            if (currentSession != null) {
                currentSession.close();
            }
        }
        return Collections.emptyList();
    }

    public BWEntry save(BWEntry _entry) {
        Session currentSession = null;
        Transaction tx = null;
        try {
            currentSession = sessionFactory.getCurrentSession();
            tx = currentSession.beginTransaction();
            currentSession.save(_entry);
            tx.commit();
            return _entry;
        } catch (HibernateException _he) {
            _he.printStackTrace();
            if (tx != null) {
                tx.rollback();
            }
        } finally {
            if (currentSession != null) {
                currentSession.close();
            }
        }
        return null;
    }

    public boolean delete(BWEntry _entry) {
        Session currentSession = null;
        Transaction tx = null;
        try {
            currentSession = sessionFactory.getCurrentSession();
            tx = currentSession.beginTransaction();
            currentSession.delete(_entry);
            tx.commit();
            return true;
        } catch (HibernateException _he) {
            _he.printStackTrace();
            if (tx != null) {
                tx.rollback();
            }
        } finally {
            if (currentSession != null) {
                currentSession.close();
            }
        }
        return false;
    }
}
