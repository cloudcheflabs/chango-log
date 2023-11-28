package co.cloudcheflabs.chango.log.config;

import co.cloudcheflabs.chango.log.dao.rocksdb.RocksdbLogFileDao;
import co.cloudcheflabs.chango.log.domain.LogPath;
import co.cloudcheflabs.chango.log.service.LogFileServiceImpl;
import co.cloudcheflabs.chango.log.util.JsonUtils;
import co.cloudcheflabs.chango.log.util.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import static co.cloudcheflabs.chango.log.Chango.ENV_CHANGO_LOG_CONFIGURATION_PATH;

public class ConfigurationLoaderTestRunner {

    private static Logger LOG = LoggerFactory.getLogger(ConfigurationLoaderTestRunner.class);

    @Test
    public void loadConfiguration() throws Exception {
        String confPath = System.getProperty("confPath", "/Users/mykidong/project/chango-log/src/test/resources/configuration-test.yml");

        StringUtils.setEnv(ENV_CHANGO_LOG_CONFIGURATION_PATH, confPath);
        LOG.info("env {}: {}", ENV_CHANGO_LOG_CONFIGURATION_PATH,
                StringUtils.getEnv(ENV_CHANGO_LOG_CONFIGURATION_PATH));

        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(
                ConfigurationLoader.class,
                RocksdbLogFileDao.class,
                LogFileServiceImpl.class
        );

        Properties configuration = applicationContext.getBean("configuration", Properties.class);

        Iterator<Object> iter = configuration.keys().asIterator();
        Map<String, String> logsMap = new HashMap<>();
        while(iter.hasNext()) {
            String key = (String) iter.next();
            if(key.startsWith("logs")) {
                logsMap.put(key, configuration.getProperty(key));
            }
        }

        Map<String, LogPath> logPathMap = new HashMap<>();
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

        LOG.info(JsonUtils.toJson(logPathMap));
        Assert.assertTrue(logPathMap.keySet().size() == 3);
    }
}
