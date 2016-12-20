package dk.matzon.bwusage.application;

import dk.matzon.bwusage.domain.BWEntryRepository;
import dk.matzon.bwusage.domain.model.BWEntry;
import dk.matzon.bwusage.infrastructure.persistence.BWEntryRepositoryImpl;
import dk.matzon.bwusage.infrastructure.persistence.HibernateUtil;
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
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Created by Brian Matzon <brian@matzon.dk>
 */
public class BWUsage {

    private Properties properties = new Properties();

    private BWEntryRepository repository;

    public BWUsage() {
    }


    private void save(String _page) throws IOException, ParseException {
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
            repository.save(new BWEntry(parsedDate, upload, download));
        }

        System.out.println("all done, listing from DB:");
        List<BWEntry> all = repository.findAll();
        for (BWEntry bwEntry : all) {
            System.out.println(" * " + bwEntry);
        }

        System.out.println("listing between dates:");
        List<BWEntry> byDate = repository.findByDate(sdf.parse("16-12-2016 00:00:00"), sdf.parse("19-12-2016 00:00:00"));
        for (BWEntry bwEntry : byDate) {
            System.out.println(" * " + bwEntry);
        }
    }

    private String download() throws IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
        CloseableHttpClient client = null;
        try {

            // setup
            BasicCookieStore cookieStore = new BasicCookieStore();

            // allow self-signed
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

        // configure db
        SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
        repository = new BWEntryRepositoryImpl(sessionFactory);
    }

    private void shutdown() {
        HibernateUtil.getSessionFactory().close();
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, ParseException {
        BWUsage bwUsage = new BWUsage();
        bwUsage.init();
        String entity = bwUsage.download();
        bwUsage.save(entity);
        bwUsage.shutdown();
    }
}
