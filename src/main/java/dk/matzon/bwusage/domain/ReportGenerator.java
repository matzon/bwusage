package dk.matzon.bwusage.domain;

/**
 * Created by Brian Matzon <brian@matzon.dk>.
 */
public interface ReportGenerator {

    /**
     * Type of reports to list
     */
    enum REPORT_TYPE {
        TODAY,
        MONTH,
        ALL
    }

    void init();

    void shutdown();

    boolean isRunning();

    /**
     * Generate the standard reports
     */
    void generateReport(REPORT_TYPE _reportType);

    /**
     * List report for REPORT_TYPE
     *
     * @param _reportType REPORT_TYPE to list elements for
     */
    void list(REPORT_TYPE _reportType);
}
