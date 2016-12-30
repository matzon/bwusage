package dk.matzon.bwusage.application;

import com.google.gson.Gson;
import dk.matzon.bwusage.domain.BWEntryRepository;
import dk.matzon.bwusage.domain.model.BWEntry;
import dk.matzon.bwusage.infrastructure.persistence.BWEntryRepositoryImpl;
import dk.matzon.bwusage.infrastructure.persistence.HibernateUtil;
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
import org.hibernate.SessionFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Brian Matzon <brian@matzon.dk>
 */
public class BWUsage {

    private Properties properties = new Properties();

    private BWEntryRepository repository;

    public BWUsage() {
    }

    private void report() throws IOException {
        Date now = new Date();
        reportForMonth(now);

        // handle monthly overlap, by running previous month too, if first of month
        Date yesterday = DateUtils.addDays(now, -1);
        if (DateUtils.toCalendar(now).get(Calendar.MONTH) != DateUtils.toCalendar(yesterday).get(Calendar.MONTH)) {
            reportForMonth(yesterday);
        }
    }

    private void reportForMonth(Date _date) throws IOException {

        Date startOfMonth = DateUtils.truncate(_date, Calendar.MONTH);
        Date endOfMonth = DateUtils.addMinutes(DateUtils.ceiling(_date, Calendar.MONTH), -1);

        Calendar calendar = DateUtils.toCalendar(startOfMonth);
        String month = String.valueOf(calendar.get(Calendar.MONTH) + 1);
        String year = String.valueOf(calendar.get(Calendar.YEAR));

        /* generate monthly report */
        List<BWEntry> monthly = repository.findByDate(startOfMonth, endOfMonth);
        Gson gson = new Gson();
        String json = gson.toJson(monthly);

        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(new File(String.format("data/reports/%s-%s.json", year, month))));
        outputStreamWriter.write(json);
        outputStreamWriter.close();
    }

    private List<BWEntry> extract(String _page) throws Exception {
        List<BWEntry> entries = new ArrayList<>();

        Document dom = Jsoup.parse(_page);

        // get the table with the elements, expecting groups of 3
        Elements bwTable = dom.select("[style*=border:1px dotted #000000]");
        Elements tds = bwTable.select("td");

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");

        int groupCount = tds.size() / 3;

        for (int i = 0; i < groupCount; i++) {
            String date = tds.get(i * 3).text();
            String upload = tds.get(i * 3 + 1).text();
            String download = tds.get(i * 3 + 2).text();
            Date parsedDate = sdf.parse(date);
            BWEntry entry = new BWEntry(parsedDate, upload, download);
            entries.add(entry);
            System.out.println("Parsed entry: " + entry);
        }
        return entries;
    }


    private void save(List<BWEntry> _entries) throws IOException, ParseException {
        for (BWEntry entry : _entries) {
            repository.save(entry);
        }
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
            HttpUriRequest loginRequest = RequestBuilder.post(properties.getProperty("login"))
                    .addParameter("username", properties.getProperty("username"))
                    .addParameter("password", properties.getProperty("password"))
                    .addParameter("command", "login").build();

            CloseableHttpResponse response = client.execute(loginRequest);
            if (response.getStatusLine().getStatusCode() == 200) {
                // acquire form
                HttpUriRequest bwUsageRequest = RequestBuilder.get(properties.getProperty("bwpage")).build();
                CloseableHttpResponse bwUsageResponse = client.execute(bwUsageRequest);
                if (bwUsageResponse.getStatusLine().getStatusCode() == 200) {
                    HttpEntity entity = bwUsageResponse.getEntity();
                    return EntityUtils.toString(entity);
                }
            } else {
                System.out.println("unable to login: " + response.getStatusLine());
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
        return null;
    }

    private void init() throws IOException {
        InputStream configInputStream = BWUsage.class.getResourceAsStream("/config.properties");
        properties.load(configInputStream);

        // prepare directories
        new File("data/db").mkdirs();
        new File("data/reports").mkdirs();

        // configure db
        SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
        repository = new BWEntryRepositoryImpl(sessionFactory);
    }

    private void shutdown() {
        HibernateUtil.getSessionFactory().close();
    }

    /**
     * Main entry point for application. General flow:
     * <ul>
     * <li>gather</li>
     * <li>extract</li>
     * <li>persist</li>
     * <li>report</li>
     * </ul>
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        BWUsage bwUsage = new BWUsage();
        bwUsage.init();

        // gather
        String entity = bwUsage.download();

        // extract
        List<BWEntry> entries = bwUsage.extract(entity);

        // persist
        if (!entries.isEmpty()) {
            bwUsage.save(entries);
        }

        // report
        bwUsage.report();

        bwUsage.shutdown();
    }
}
