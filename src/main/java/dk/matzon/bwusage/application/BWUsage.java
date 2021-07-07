package dk.matzon.bwusage.application;

import dk.matzon.bwusage.application.service.DataGathererImpl;
import dk.matzon.bwusage.application.service.ReportGeneratorImpl;
import dk.matzon.bwusage.domain.DataGatherer;
import dk.matzon.bwusage.domain.ReportGenerator;
import dk.matzon.bwusage.domain.Repository;
import dk.matzon.bwusage.domain.model.BWEntry;
import dk.matzon.bwusage.domain.model.BWHistoricalEntry;
import dk.matzon.bwusage.infrastructure.persistence.BWEntryRepositoryImpl;
import dk.matzon.bwusage.infrastructure.persistence.BWHistoricalEntryRepositoryImpl;
import dk.matzon.bwusage.infrastructure.persistence.HibernateUtil;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;
import org.hsqldb.lib.StopWatch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by Brian Matzon <brian@matzon.dk>
 */
public class BWUsage {
    private final Logger LOGGER = LogManager.getLogger(BWUsage.class);

    private final Properties properties;

    private final ScheduledExecutorService scheduledExecutorService;

    private Repository<BWEntry> repository;

    private Repository<BWHistoricalEntry> historicalRepository;

    private ReportGenerator reportGenerator;

    private DataGatherer dataGatherer;

    private volatile boolean active;

    private final StopWatch stopWatch;

    public BWUsage() {
        active = false;
        properties = new Properties();
        scheduledExecutorService = Executors.newScheduledThreadPool(2);
        stopWatch = new StopWatch();
    }

    private void init() throws IOException {
        // prepare directories
        new File("data/db").mkdirs();
        new File("data/reports").mkdirs();

        InputStream configInputStream = BWUsage.class.getResourceAsStream("/config.properties");
        properties.load(configInputStream);

        // configure db
        stopWatch.zero();
        SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
        repository = new BWEntryRepositoryImpl(sessionFactory);
        historicalRepository = new BWHistoricalEntryRepositoryImpl(sessionFactory);
        LOGGER.info(stopWatch.currentElapsedTimeToMessage("Initialized DB"));

        // configure data gather
        dataGatherer = new DataGathererImpl(scheduledExecutorService, repository, historicalRepository, properties);
        dataGatherer.init();

        // configure report generator
        reportGenerator = new ReportGeneratorImpl(scheduledExecutorService, repository, historicalRepository, properties);
        reportGenerator.init();

        // configure backup
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                HibernateUtil.backup();
            }
        }, 1, Long.parseLong(properties.getProperty("app.backupperiod")), TimeUnit.MINUTES);

        active = true;
    }

    private void prepareShutdown() {
        LOGGER.info("prepareShutdown invoked");
        scheduledExecutorService.shutdown();
        active = false;
    }

    public boolean isRunning() {
        return reportGenerator.isRunning() && dataGatherer.isRunning();
    }

    private void handleCommand(String _command) {
        stopWatch.zero();
        String command = _command.trim().toLowerCase();
        switch (command) {
            case "quit":
                prepareShutdown();
                break;
            case "gather":
                dataGatherer.downloadData();
                break;
            case "report":
                reportGenerator.generateReport(ReportGenerator.REPORT_TYPE.TODAY);
                reportGenerator.generateReport(ReportGenerator.REPORT_TYPE.MONTH);
                reportGenerator.generateReport(ReportGenerator.REPORT_TYPE.ALL);
                break;
            case "ltoday":
                reportGenerator.list(ReportGenerator.REPORT_TYPE.TODAY);
                break;
            case "lmonth":
                reportGenerator.list(ReportGenerator.REPORT_TYPE.MONTH);
                break;
            case "lall":
                reportGenerator.list(ReportGenerator.REPORT_TYPE.ALL);
                break;
            case "backup":
                HibernateUtil.backup();
                break;
            case "help":
                printCommands();
            default:
                System.out.println("Unknown command '" + command + "'");
                break;
        }
        LOGGER.info(stopWatch.currentElapsedTimeToMessage("Processed command '" + command + "'"));
    }

    private void printCommands() {
        System.out.println("quit - exit application");
        System.out.println("gather - download data");
        System.out.println("report - generate all reports");
        System.out.println("ltoday - list bandwidth usage for today");
        System.out.println("lmonth - list bandwidth usage for the current month");
        System.out.println("lall - list bandwidth usage for all time");
        System.out.println("backup - perform a backup of the database");
        System.out.println();
    }

    private void shutdown() {
        try {
            scheduledExecutorService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException _e) {
            LOGGER.warn("Exception while waiting for scheduled executor service to terminate: " + _e.getMessage());
        }
        HibernateUtil.getSessionFactory().close();
        reportGenerator.shutdown();
        dataGatherer.shutdown();
    }

    private String timeForNextDataJob() {
        long delay = dataGatherer.getTimeForNextJob();
        String formattedDelay = "00:00";
        if (delay > 0) {
            formattedDelay = DurationFormatUtils.formatDuration(delay, "mm:ss");
        }
        return formattedDelay;
    }


    /**
     * Main entry point for application. General flow:
     */
    public static void main(String[] args) throws Exception {
        BWUsage bwUsage = new BWUsage();
        bwUsage.init();

        System.out.println("BWUsage running...");

        Scanner input = new Scanner(System.in);
        while (bwUsage.isRunning()) {
            System.out.print("(" + bwUsage.timeForNextDataJob() + ") $> ");
            String command = input.nextLine();
            bwUsage.handleCommand(command);
        }

        bwUsage.shutdown();
    }
}
