package dk.matzon.bwusage.domain;

import java.util.Date;
import java.util.List;

/**
 * Created by Brian Matzon <brian@matzon.dk>
 */
public interface Repository<T> {
    /**
     * @return List of all Ts in the repository
     */
    List<T> findAll();

    /**
     * @param _from Date (inclusive) to list Ts from
     * @param _to   Date (exclusive) to list Ts to
     * @return List of all T's in the repository between the supplied dates
     */
    List<T> findByDate(Date _from, Date _to);

    /**
     * @param _entity T to save
     * @return Saved T
     */
    T save(T _entity);

    /***
     * @param _entity T to save
     * @return true if T was saved
     */
    boolean delete(T _entity);

    /**
     * @param _entities List of Ts to save
     * @return true if T was saved
     */
    boolean saveAll(List<T> _entities);
}
