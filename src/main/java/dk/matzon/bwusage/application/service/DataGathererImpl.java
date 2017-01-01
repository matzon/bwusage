package dk.matzon.bwusage.application.service;

import dk.matzon.bwusage.domain.DataGatherer;
import dk.matzon.bwusage.domain.Repository;
import dk.matzon.bwusage.domain.model.BWEntry;
import dk.matzon.bwusage.domain.model.BWHistoricalEntry;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
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
            if(DateUtils.isSameDay(_now, entry.getDate())) {
                historicalRepository.save(new BWHistoricalEntry(_now, entry.getUpload(), entry.getDownload()));
            }
        }
    }

    private List<BWEntry> extract(String _page) throws Exception {
        List<BWEntry> entries = new ArrayList<>();

        Document dom = Jsoup.parse(_page);

        // get the table with the elements, expecting groups of 3 (yes, shitty html, nothing sane to select by)
        Elements bwTable = dom.select("[style*=border:1px dotted #000000]");

        // tr seem more reliable than tds directly?
        Elements trs = bwTable.select("tr");

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");

        for (Element tr : trs) {
            Elements tds = tr.select("td");
            String date = tds.get(0).text();
            String upload = tds.get(1).text();
            String download = tds.get(2).text();
            Date parsedDate = sdf.parse(date);
            BWEntry entry = new BWEntry(parsedDate, upload, download);
            entries.add(entry);
            LOGGER.info("Parsed entry: " + entry);
        }
        return entries;
    }

    private String download() throws IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
        CloseableHttpClient client = null;
        try {

            // setup
            BasicCookieStore cookieStore = new BasicCookieStore();

            // allow self-signed and untrusted certs
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            }).build();
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext);

            client = HttpClientBuilder.create()
                    .setDefaultCookieStore(cookieStore)
                    .setRedirectStrategy(new LaxRedirectStrategy())
                    .setSSLSocketFactory(sslConnectionSocketFactory)
                    .build();

            // login
            HttpUriRequest loginRequest = RequestBuilder.post(properties.getProperty("datagatherer.login"))
                    .addParameter("username", properties.getProperty("datagatherer.username"))
                    .addParameter("password", properties.getProperty("datagatherer.password"))
                    .addParameter("command", "login").build();

            CloseableHttpResponse loginResponse = client.execute(loginRequest);
            if (loginResponse.getStatusLine().getStatusCode() == 200) {
                // acquire form
                HttpUriRequest bwUsageRequest = RequestBuilder.get(properties.getProperty("datagatherer.bwpage")).build();
                CloseableHttpResponse bwUsageResponse = client.execute(bwUsageRequest);
                if (bwUsageResponse.getStatusLine().getStatusCode() == 200) {
                    HttpEntity entity = bwUsageResponse.getEntity();
                    return EntityUtils.toString(entity);
                } else {
                    logWithBody(bwUsageResponse, "Unable to login");
                }
            } else {
                logWithBody(loginResponse, "Unable to login");
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
        return null;
    }

    private void logWithBody(CloseableHttpResponse _response, String _message) {
        String body = "";
        try {
            HttpEntity entity = _response.getEntity();
            body = EntityUtils.toString(entity);
        } catch (Exception _e) {
            /* ignored */
        }
        throw new RuntimeException(_message + ". StatusLine: " + _response.getStatusLine() + ", Body: " + body);
    }
}
