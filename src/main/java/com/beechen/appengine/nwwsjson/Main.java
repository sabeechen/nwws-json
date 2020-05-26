package com.beechen.appengine.nwwsjson;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutionException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.StandardExtensionElement;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.muc.MucEnterConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.json.simple.JSONArray;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.staticFiles;
import spark.Request;
import spark.Response;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;

public class Main {

    private static final String XMPP_HOST = getEnv("XMPP_HOST", "nwws-oi.weather.gov");
    private static final String XMPP_BACKUP_HOST = getEnv("XMPP_BACKUP_HOST", "nwws-oi-md.weather.gov");
    private static final int XMPP_PORT = Integer.parseInt(getEnv("XMPP_PORT", "5222"));

    private static final String DOMAIN = getEnv("XMPP_DOMAIN", "nwws-oi.weather.gov");
    private static final String RESOURCE = getEnv("XMPP_RESOURCE", "nwws");
    private static final int PORT = Integer.parseInt(getEnv("PORT", "8080"));

    private static final SimpleDateFormat ISO8601_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final String JSON_MIME_TYPE = "application/json";

    private static final String ATTR_STATION = "cccc";
    private static final String ATTR_OWMS_PRODUCT_ID = "ttaaii";
    private static final String ATTR_DATE = "issue";
    private static final String ATTR_ID = "id";
    private static final String ATTR_AWPSID = "awipsid";

    private Cache cache = new Cache();

    private final String user;
    private final String password;

    public Main(String user, String password) {
        this.user = user;
        this.password = password;
    }

    private static String getEnv(String key, String missingDefault) {
        String value = System.getenv(key);
        if (value != null) {
            return value;
        } else if (missingDefault != null) {
            return missingDefault;
        } else {
            throw new RuntimeException(String.format("Environment variable %s is required", key));
        }
    }

    public void run() throws InterruptedException, ExecutionException {
        // Warm up the cache.
        this.cache.warmup();

        // Set up webserver paths
        staticFiles.location("/static");
        port(PORT);
        get("/message/*/*", (a, b) -> this.getProduct(a, b));
        get("/centers", (a, b) -> this.getCenters(a, b));
        get("/center/*", (a, b) -> this.getCenter(a, b));
        spark.Spark.redirect.get("/", "/index.html");

        // Loop connect to the XMPP server
        while (true) {
            try {
                this.connectAndWait();
            } catch (SmackException | IOException | XMPPException e) {
                e.printStackTrace();
            }
            Thread.sleep(5000);
        }
    }

    private Object getProduct(Request res, Response resp) {
        String station = res.splat()[0];
        String productId = res.splat()[1];
        resp.type(JSON_MIME_TYPE);
        Entry entry = this.cache.lookup(station, productId);
        if (entry != null) {
            return entry.toJson().toJSONString();
        } else {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Object getCenter(Request res, Response resp) {
        String station = res.splat()[0];
        JSONArray list = new JSONArray();
        resp.type(JSON_MIME_TYPE);
        for (Entry entry : this.cache.lookup(station)) {
            list.add(entry.toJson());
        }
        return list.toJSONString();
    }

    private Object getCenters(Request res, Response resp) {
        StringBuilder page = new StringBuilder();
        page.append("<html><body>");

        page.append("<h2>NWWS JSON API Station List</h2>");
        for (String center : this.cache.stations()) {
            page.append(String.format("<a href=\"/center/%s\">%s</a><br>", center, center));
        }
        page.append("</body></html>");
        return page.toString();
    }

    private void connectAndWait() throws SmackException, IOException, XMPPException, InterruptedException {
        Jid user = JidCreate.from(this.user, DOMAIN, RESOURCE);
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setUsernameAndPassword(this.user, this.password)
                .setXmppDomain(DOMAIN)
                .setHost(XMPP_HOST)
                .setPort(XMPP_PORT)
                .build();
        final AbstractXMPPConnection connection = new XMPPTCPConnection(config);
        try {
            connection.connect();
            connection.login();
            System.out.println("Connected");

            MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
            EntityBareJid chatJid = JidCreate.entityBareFrom("nwws@conference.nwws-oi.weather.gov");
            MultiUserChat muc = manager.getMultiUserChat(chatJid);
            muc.addMessageListener(m -> this.handleMessage(m));

            MucEnterConfiguration enter_config = muc
                    .getEnterConfigurationBuilder(Resourcepart.from(user.getLocalpartOrNull().toString()))
                    .withPassword(this.password).requestMaxStanzasHistory(1000).timeoutAfter(60 * 60 * 1000).build();

            muc.join(enter_config);
            System.out.println("Joined chatroom");

            Thread.sleep(60 * 60 * 1000);
            System.out.println("Done");
        } finally {
            connection.disconnect();
        }
    }

    private void handleMessage(Message message) {
        try {
            System.out.println(message.getBody());
            StandardExtensionElement data = (StandardExtensionElement) message.getExtension("x", "nwws-oi");

            if (data == null) {
                return;
            }
            String station = data.getAttributeValue(ATTR_STATION);
            String productId = data.getAttributeValue(ATTR_OWMS_PRODUCT_ID);
            String date = data.getAttributeValue(ATTR_DATE);
            String id = data.getAttributeValue(ATTR_ID);
            String awipsid = data.getAttributeValue(ATTR_AWPSID);
            date = date.replace("Z", "GMT-00:00");
            if (station != null && productId != null && date != null && id != null && id != null && awipsid != null) {
                Entry entry = new Entry(productId, station, ISO8601_FORMAT.parse(date), id, awipsid, message.getBody(),
                        data.getText());
                cache.add(entry);
            }
        } catch (ParseException e) {
            // Date parsing failed, ignore it
            cache.toString();
        }
    }

    public static void setLevel(Level targetLevel) {
        Logger root = Logger.getLogger("io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler");
        root.setLevel(targetLevel);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(targetLevel);
        }
        System.out.println("level set: " + targetLevel.getName());
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        setLevel(Level.INFO);
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretName = SecretVersionName.of("nwws-oi", "nwws-user", "latest");
            SecretVersionName secretPassword = SecretVersionName.of("nwws-oi", "nwws-password", "latest");
      
            // Access the secret version.
            String user = client.accessSecretVersion(secretName).getPayload().getData().toStringUtf8();
            String password = client.accessSecretVersion(secretPassword).getPayload().getData().toStringUtf8();
            new Main(user, password).run();
          }
      
        
    }
}
