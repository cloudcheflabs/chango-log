package co.cloudcheflabs.chango.log.component;

import co.cloudcheflabs.chango.client.component.ChangoClient;
import co.cloudcheflabs.chango.log.api.service.LogFileService;
import co.cloudcheflabs.chango.log.domain.LogPath;
import co.cloudcheflabs.chango.log.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

@Component
public class LogReader implements InitializingBean {
    private static Logger LOG = LoggerFactory.getLogger(LogReader.class);

    @Autowired
    private Properties configuration;

    @Autowired
    private LogFileService logFileService;

    private Map<String, LogPath> logPathMap;

    private ChangoClient changoClient;

    private String token;
    private String dataApiUrl;
    private String schema;
    private String table;
    private int batchSize;
    private long interval;

    @Override
    public void afterPropertiesSet() throws Exception {

        // load log paths configuraiton.

        Iterator<Object> iter = configuration.keys().asIterator();
        Map<String, String> logsMap = new HashMap<>();
        while(iter.hasNext()) {
            String key = (String) iter.next();
            if(key.startsWith("logs")) {
                logsMap.put(key, configuration.getProperty(key));
            }
        }

        if(logsMap.isEmpty()) {
            throw new RuntimeException("Log paths not available!");
        }

        logPathMap = new HashMap<>();
        for(String key : logsMap.keySet()) {
            String[] keyTokens = key.split("\\.");
            String keyPrefix = keyTokens[0];

            LogPath logPath = null;
            if(logPathMap.containsKey(keyPrefix)) {
                logPath = logPathMap.get(keyPrefix);
            } else {
                logPath = new LogPath();
            }

            if(key.contains("path")) {
                logPath.setPath(logsMap.get(key));
            } else if(key.contains("file")) {
                logPath.setFile(logsMap.get(key));
            }

            logPathMap.put(keyPrefix, logPath);
        }

        LOG.info("Log paths loaded: {}", JsonUtils.toJson(logPathMap));

        // construct chango client.

        token = configuration.getProperty("chango.token");
        dataApiUrl = configuration.getProperty("chango.dataApiUrl");
        schema = configuration.getProperty("chango.schema");
        table = configuration.getProperty("chango.table");
        batchSize = Integer.valueOf(configuration.getProperty("chango.batchSize"));
        interval = Long.valueOf(configuration.getProperty("chango.interval"));

        constructChangoClient();
        LOG.info("Chango client constructed.");

        // TODO: read logs and send them to data api.
    }

    private void constructChangoClient() {
        changoClient = new ChangoClient(
                token,
                dataApiUrl,
                schema,
                table,
                batchSize,
                interval
        );
    }
}
