package dk.matzon.bwusage.domain;

/**
 * Created by Brian Matzon <brian@matzon.dk>.
 */
public interface DataGatherer {
    void init();
    void shutdown();
    boolean isRunning();

    long getTimeForNextJob();

    void downloadData();

}
