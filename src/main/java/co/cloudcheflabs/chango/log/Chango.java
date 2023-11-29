package co.cloudcheflabs.chango.log;

import co.cloudcheflabs.chango.log.component.LogReader;
import co.cloudcheflabs.chango.log.config.ConfigurationLoader;
import co.cloudcheflabs.chango.log.dao.rocksdb.RocksdbLogFileDao;
import co.cloudcheflabs.chango.log.service.LogFileServiceImpl;
import co.cloudcheflabs.chango.log.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Chango {

    private static Logger LOG = LoggerFactory.getLogger(Chango.class);

    public static final String ENV_CHANGO_LOG_CONFIGURATION_PATH = "CHANGO_LOG_CONFIGURATION_PATH";

    public static void main(String[] args) {
        if(args.length < 1) {
            throw new RuntimeException("Configuration path not available!");
        }

        String confPath = args[0];

        StringUtils.setEnv(ENV_CHANGO_LOG_CONFIGURATION_PATH, confPath);
        LOG.info("env {}: {}", ENV_CHANGO_LOG_CONFIGURATION_PATH,
                StringUtils.getEnv(ENV_CHANGO_LOG_CONFIGURATION_PATH));

        ConfigurableApplicationContext applicationContext = new AnnotationConfigApplicationContext(
                ConfigurationLoader.class,
                RocksdbLogFileDao.class,
                LogFileServiceImpl.class,
                LogReader.class
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            applicationContext.close();
        }));

        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
        }
    }
}
