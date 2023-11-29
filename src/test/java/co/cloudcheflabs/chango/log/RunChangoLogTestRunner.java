package co.cloudcheflabs.chango.log;

import co.cloudcheflabs.chango.log.config.ConfigurationLoader;
import co.cloudcheflabs.chango.log.dao.rocksdb.RocksdbLogFileDao;
import co.cloudcheflabs.chango.log.service.LogFileServiceImpl;
import co.cloudcheflabs.chango.log.util.FileUtils;
import co.cloudcheflabs.chango.log.util.StringUtils;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static co.cloudcheflabs.chango.log.Chango.ENV_CHANGO_LOG_CONFIGURATION_PATH;

public class RunChangoLogTestRunner {

    @Test
    public void runChangoLog() throws Exception {

        String confPath = System.getProperty("confPath", "/Users/mykidong/project/chango-log/src/test/resources/configuration-test.yml");

        // remove rocksdb directory.
        //removeRocksDBDirectory(confPath);

        List<String> args = Arrays.asList(confPath);

        Chango.main(args.toArray(new String[0]));
    }

    private void removeRocksDBDirectory(String confPath) {
        StringUtils.setEnv(ENV_CHANGO_LOG_CONFIGURATION_PATH, confPath);

        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(
                ConfigurationLoader.class
        );

        // delete rockdb directory.
        Properties configuration = applicationContext.getBean("configuration", Properties.class);
        String rocksdbDirectory = configuration.getProperty("rocksdb.directory");
        FileUtils.deleteDirectory(rocksdbDirectory);
    }
}
