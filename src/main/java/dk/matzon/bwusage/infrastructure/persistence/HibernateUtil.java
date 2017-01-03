package dk.matzon.bwusage.infrastructure.persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.query.Query;

/**
 * From generic Hibernate tutorial
 */
public class HibernateUtil {

    private static final Logger LOGGER = LogManager.getLogger(HibernateUtil.class);

    private static final SessionFactory sessionFactory = buildSessionFactory();

    private static SessionFactory buildSessionFactory() {
        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .configure() // configures settings from hibernate.cfg.xml
                .build();
        try {
            return new MetadataSources(registry).buildMetadata().buildSessionFactory();
        } catch (Exception e) {
            // The registry would be destroyed by the SessionFactory, but we had trouble building the SessionFactory
            // so destroy it manually.
            StandardServiceRegistryBuilder.destroy(registry);
            throw new RuntimeException("Unable to configure hibernate");
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    /**
     * HSQLDB centric backup of db
     */
    public static void backup() {
        Session session = null;
        Transaction transaction = null;
        try {
            session = sessionFactory.getCurrentSession();
            transaction = session.beginTransaction();
            Query backupQuery = session.createNativeQuery(String.format("BACKUP DATABASE TO '%s' NOT BLOCKING", "data/db/backup/"));
            backupQuery.executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            LOGGER.warn("Exception while performing backup: " + e.getMessage(), e);
            if (transaction != null) {
                transaction.rollback();
            }
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }
}