package dk.matzon.bwusage.application.service;

import com.google.gson.Gson;
import dk.matzon.bwusage.domain.ReportGenerator;
import dk.matzon.bwusage.domain.Repository;
import dk.matzon.bwusage.domain.model.BWEntry;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by Brian Matzon <brian@matzon.dk>.
 */
public class ReportGeneratorImpl implements ReportGenerator {
    private final Logger LOGGER = LogManager.getLogger(ReportGeneratorImpl.class);
    private final ScheduledExecutorService scheduledExecutorService;
    private final Repository<BWEntry> repository;
    private final Properties properties;

    private ScheduledFuture<?> scheduledTodayFuture;
    private ScheduledFuture<?> scheduledMonthFuture;
    private ScheduledFuture<?> scheduledAllFuture;
    private int errorCount = 0;

    private Date lastRun;

    public ReportGeneratorImpl(ScheduledExecutorService _scheduledExecutorService, Repository<BWEntry> _repository, Properties _properties) {
        scheduledExecutorService = _scheduledExecutorService;
        repository = _repository;
        properties = _properties;
    }

    @Override
    public void init() {
        LOGGER.info("initializing");
        Long delay = Long.parseLong(properties.getProperty("reportgenerator.delay"));
        scheduledTodayFuture = scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                generateReport(REPORT_TYPE.TODAY);
            }
        }, delay, Long.parseLong(properties.getProperty("reportgenerator.today.period")), TimeUnit.MINUTES);

        scheduledMonthFuture = scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                generateReport(REPORT_TYPE.MONTH);
            }
        }, delay, Long.parseLong(properties.getProperty("reportgenerator.month.period")), TimeUnit.MINUTES);

        scheduledAllFuture = scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                generateReport(REPORT_TYPE.ALL);
            }
        }, delay, Long.parseLong(properties.getProperty("reportgenerator.all.period")), TimeUnit.MINUTES);
    }

    @Override
    public void shutdown() {
        LOGGER.info(String.format("shutting down [errorCount: %d]", errorCount));
        scheduledTodayFuture.cancel(true);
        scheduledMonthFuture.cancel(true);
        scheduledAllFuture.cancel(true);
    }

    @Override
    public boolean isRunning() {
        return !scheduledTodayFuture.isCancelled() && !scheduledMonthFuture.isCancelled() && !scheduledAllFuture.isCancelled();
    }

    @Override
    public synchronized void generateReport(REPORT_TYPE _reportType) {
        try {
            Date now = new Date();
            switch (_reportType) {
                case TODAY:
                    reportForToday(now, true);
                    break;
                case MONTH:
                    reportForMonth(now, true);
                    // handle monthly overlap, by running previous month too, if last run was for previous month
                    if (lastRun != null && DateUtils.toCalendar(now).get(Calendar.MONTH) != DateUtils.toCalendar(lastRun).get(Calendar.MONTH)) {
                        reportForMonth(lastRun, true);
                    }
                    lastRun = now;
                    break;
                case ALL:
                    reportForAll(true);
                    break;
            }
            errorCount = 0;
        } catch (Exception _e) {
            LOGGER.warn("Exception occurred while executing main block of reportgenerator: " + _e.getMessage(), _e);
            if (++errorCount == Integer.valueOf(properties.getProperty("reportgenerator.maxerrorcount"))) {
                shutdown();
            }
        }
    }

    @Override
    public void list(REPORT_TYPE _reportType) {
        List<BWEntry> entries = null;

        try {
            switch (_reportType) {
                case ALL:
                    entries = reportForAll(false);
                    break;
                case MONTH:
                    entries = reportForMonth(new Date(), false);
                    break;
                case TODAY:
                    entries = reportForToday(new Date(), false);
                    break;
            }
        } catch (IOException _e) {
            entries = Collections.emptyList();
        }

        for (BWEntry entry : entries) {
            System.out.println(entry);
        }
    }

    public List<BWEntry> reportForAll(boolean _writeReport) throws IOException {
        List<BWEntry> all = repository.findAll();
        if (_writeReport) {
            writeReport(all, "data/reports/all.json");
        }
        return all;
    }

    public List<BWEntry> reportForMonth(Date _date, boolean _writeReport) throws IOException {
        Date startOfMonth = DateUtils.truncate(_date, Calendar.MONTH);
        Date endOfMonth = DateUtils.addSeconds(DateUtils.ceiling(_date, Calendar.MONTH), -1);

        Calendar calendar = DateUtils.toCalendar(startOfMonth);
        String month = String.valueOf(calendar.get(Calendar.MONTH) + 1);
        String year = String.valueOf(calendar.get(Calendar.YEAR));

        List<BWEntry> monthly = repository.findByDate(startOfMonth, endOfMonth);
        if (_writeReport) {
            writeReport(monthly, String.format("data/reports/%s-%s.json", year, month));
        }
        return monthly;
    }

    private List<BWEntry> reportForToday(Date _date, boolean _writeReport) throws IOException {
        Date startOfDay = DateUtils.truncate(_date, Calendar.DAY_OF_MONTH);
        Date endOfDay = DateUtils.addSeconds(DateUtils.ceiling(_date, Calendar.DAY_OF_MONTH), -1);

        Calendar calendar = DateUtils.toCalendar(startOfDay);
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        String month = String.valueOf(calendar.get(Calendar.MONTH) + 1);
        String day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));

        List<BWEntry> today = repository.findByDate(startOfDay, endOfDay);
        if (_writeReport) {
            writeReport(today, String.format("data/reports/%s-%s-%s.json", year, month, day));
        }
        return today;
    }

    private void writeReport(List<BWEntry> _entries, String name) throws IOException {
        Gson gson = new Gson();
        String json = gson.toJson(_entries);

        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(new File(name)));
        outputStreamWriter.write(json);
        outputStreamWriter.close();
    }
}
