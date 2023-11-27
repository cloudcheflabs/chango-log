package co.cloudcheflabs.chango.log.service;

import co.cloudcheflabs.chango.log.api.service.LogFileService;
import co.cloudcheflabs.chango.log.config.ConfigurationLoader;
import co.cloudcheflabs.chango.log.dao.rocksdb.AbstractKeyValueDao;
import co.cloudcheflabs.chango.log.dao.rocksdb.RocksdbLogFileDao;
import co.cloudcheflabs.chango.log.domain.LogFile;
import co.cloudcheflabs.chango.log.util.FileUtils;
import co.cloudcheflabs.chango.log.util.StringUtils;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.File;
import java.util.Properties;

import static co.cloudcheflabs.chango.log.Chango.ENV_CHANGO_LOG_CONFIGURATION_PATH;

public class LogFileServiceTestRunner {

    private static Logger LOG = LoggerFactory.getLogger(LogFileServiceTestRunner.class);

    @Test
    public void save() throws Exception {
        String confPath = System.getProperty("confPath", "/Users/mykidong/project/chango-log/src/main/resources/configuration.yml");

        StringUtils.setEnv(ENV_CHANGO_LOG_CONFIGURATION_PATH, confPath);
        LOG.info("env {}: {}", ENV_CHANGO_LOG_CONFIGURATION_PATH,
                StringUtils.getEnv(ENV_CHANGO_LOG_CONFIGURATION_PATH));

        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(
                ConfigurationLoader.class,
                RocksdbLogFileDao.class,
                LogFileServiceImpl.class
        );

        LogFileService logFileService = applicationContext.getBean(LogFileService.class);

        String fileName = "test.log";
        String filePath = "/log-dir/" + fileName;
        long lastModified = DateTime.now().getMillis();
        LogFile logFile = new LogFile(
                fileName,
                filePath,
                lastModified,
                10
        );
        logFileService.save(filePath, logFile);

        LogFile ret = logFileService.find(filePath, LogFile.class);
        Assert.assertTrue(ret.getFileName().equals(fileName));
        Assert.assertTrue(ret.getReadLineCount() == 10);

        logFileService.delete(filePath);

        fileName = "test.log";
        filePath = "/log-dir/" + fileName;
        logFile = new LogFile(
                fileName,
                filePath,
                lastModified,
                20
        );
        logFileService.save(filePath, logFile);

        ret = logFileService.find(filePath, LogFile.class);
        Assert.assertTrue(ret.getFileName().equals(fileName));
        Assert.assertTrue(ret.getFilePath().equals(filePath));
        Assert.assertTrue(ret.getReadLineCount() == 20);

        logFileService.delete(filePath);

        ret = logFileService.find(filePath, LogFile.class);
        Assert.assertTrue(ret == null);

        // delete rockdb directory.
        Properties configuration = applicationContext.getBean("configuration", Properties.class);
        String rocksdbDirectory = configuration.getProperty("rocksdb.directory");
        FileUtils.deleteDirectory(rocksdbDirectory);
    }
}
