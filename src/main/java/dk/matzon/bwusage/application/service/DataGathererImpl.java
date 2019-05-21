package dk.matzon.bwusage.application.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dk.matzon.bwusage.domain.DataGatherer;
import dk.matzon.bwusage.domain.Repository;
import dk.matzon.bwusage.domain.model.BWEntry;
import dk.matzon.bwusage.domain.model.BWHistoricalEntry;
import okhttp3.*;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by Brian Matzon <brian@matzon.dk>.
 */
public class DataGathererImpl implements DataGatherer {

    private final Logger LOGGER = LogManager.getLogger(DataGathererImpl.class);
    private final ScheduledExecutorService scheduledExecutorService;
    private final Repository<BWEntry> repository;
    private Repository<BWHistoricalEntry> historicalRepository;
    private final Properties properties;

    private ScheduledFuture<?> scheduledFuture;
    private int errorCount = 0;

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public DataGathererImpl(ScheduledExecutorService _scheduledExecutorService, Repository<BWEntry> _repository, Repository<BWHistoricalEntry> _historicalRepository, Properties _properties) {
        scheduledExecutorService = _scheduledExecutorService;
        repository = _repository;
        historicalRepository = _historicalRepository;
        properties = _properties;
    }

    @Override
    public void init() {
        LOGGER.info("initializing");
        scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                downloadData();
            }
        }, Long.parseLong(properties.getProperty("datagatherer.datadelay")), Long.parseLong(properties.getProperty("datagatherer.dataperiod")), TimeUnit.MINUTES);
    }

    @Override
    public void shutdown() {
        LOGGER.info(String.format("shutting down [errorCount: %d]", errorCount));
        scheduledFuture.cancel(true);
    }

    @Override
    public boolean isRunning() {
        return !scheduledFuture.isCancelled();
    }

    @Override
    public long getTimeForNextJob() {
        if (scheduledFuture != null) {
            return scheduledFuture.getDelay(TimeUnit.MILLISECONDS);
        }
        return -1;
    }

    @Override
    public synchronized void downloadData() {
        String body = null;
        List<BWEntry> entries = null;
        try {
            // anything in this flow, which is out of order, should result in error count increase due to exceptions being thrown
            Date now = new Date();
            body = download();
            entries = extract(body);
            persist(now, entries);
            errorCount = 0;
        } catch (Exception _e) {
            LOGGER.warn("Exception occurred while executing main block of datagatherer: " + _e.getMessage(), _e);
            if (body != null) {
                LOGGER.debug(body);
            }
            if (++errorCount == Integer.valueOf(properties.getProperty("datagatherer.maxerrorcount"))) {
                shutdown();
            }
        }
    }

    private void persist(Date _now, List<BWEntry> _entries) {
        repository.saveAll(_entries);

        // add historical too
        for (BWEntry entry : _entries) {
            if (DateUtils.isSameDay(_now, entry.getDate())) {
                historicalRepository.save(new BWHistoricalEntry(_now, entry.getUpload(), entry.getDownload()));
            }
        }
    }

    private List<BWEntry> extract(String _page) throws Exception {
        List<BWEntry> entries = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        JsonElement jsonElement = new JsonParser().parse(_page);
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        Set<Map.Entry<String, JsonElement>> sourceEntries = jsonObject.entrySet();
        for (Map.Entry<String, JsonElement> sourceEntry : sourceEntries) {
            JsonElement download = sourceEntry.getValue().getAsJsonObject().get("down");
            JsonElement upload = sourceEntry.getValue().getAsJsonObject().get("up");
            Date parsedDate = sdf.parse(sourceEntry.getKey());
            BWEntry reportEntry = new BWEntry(parsedDate, upload.getAsString(), download.getAsString());
            entries.add(reportEntry);
        }
        return entries;
    }

    private String download() {
        String loginUrl = properties.getProperty("datagatherer.login");
        String username = properties.getProperty("datagatherer.username");
        String password = properties.getProperty("datagatherer.password");

        String bandwidthUrl = properties.getProperty("datagatherer.bwpage");
        String bandwidthMac = properties.getProperty("datagatherer.mac");
        String bandwidthBuid = properties.getProperty("datagatherer.buid");
        String bandwidthCase = properties.getProperty("datagatherer.case");

        try {
            OkHttpClient okHttpClient = new OkHttpClient();

            // login
            JsonObject loginJsonObject = new JsonObject();
            loginJsonObject.addProperty("username", username);
            loginJsonObject.addProperty("password", password);
            loginJsonObject.addProperty("bu_id", bandwidthBuid);
            String loginPayload = loginJsonObject.toString();

            RequestBody loginRequestBody = RequestBody.create(JSON, loginPayload);
            Request loginRequest = new Request.Builder()
                    .url(loginUrl)
                    .post(loginRequestBody)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "text/plain")
                    .build();
            Response loginResponse = okHttpClient.newCall(loginRequest).execute();

            String loginResponseBody = loginResponse.body().string();
            JsonElement loginResponseJson = new JsonParser().parse(loginResponseBody);

            String jwt = loginResponseJson.getAsJsonObject().get("token").getAsString();

            // make API request for BW data
            JsonObject bwJsonObject = new JsonObject();
            bwJsonObject.addProperty("account_user_id", username);
            bwJsonObject.addProperty("case", bandwidthCase);
            bwJsonObject.addProperty("bu_id", bandwidthBuid);
            bwJsonObject.addProperty("mac", bandwidthMac);
            String bwPayload = bwJsonObject.toString();

            RequestBody bwRequestBody = RequestBody.create(JSON, bwPayload);
            Request bwRequest = new Request.Builder()
                    .url(bandwidthUrl)
                    .post(bwRequestBody)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "text/plain")
                    .addHeader("authorization", "Bearer " + jwt)
                    .build();

            Response bwResponse = okHttpClient.newCall(bwRequest).execute();
            if (bwResponse.isSuccessful() && bwResponse.body() != null) {
                return bwResponse.body().string();
            }
        } catch (Exception e) {
            LOGGER.error("Exception while downloading BW data: " + e.getMessage(), e);
        }
        return null;
    }

}
