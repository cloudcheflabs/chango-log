package co.cloudcheflabs.chango.log.util;

import co.cloudcheflabs.chango.log.config.ConfigurationLoaderTestRunner;
import org.joda.time.DateTime;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StringUtilTestRunner {

    private static Logger LOG = LoggerFactory.getLogger(StringUtilTestRunner.class);

    @Test
    public void listFiles() throws Exception {
        Set<File> files = listFiles("/Users/mykidong");
        LOG.info(JsonUtils.toJson(files));

        String filePattern = "rest-catalog-*.yaml";
        String[] patternToken = filePattern.split("\\*");
        filePattern = "\\b" + patternToken[0] + "\\b" + ".*" + "\\b" + patternToken[1] + "\\b";
        LOG.info(filePattern);

        for(File f : files) {
            String fileName = f.getName();
            LOG.info("file name: {}", fileName);

            Pattern pattern = Pattern.compile(filePattern);
            Matcher matcher = pattern.matcher(fileName);
            if(matcher.matches()) {
                LOG.info("matched: {}", fileName);
            }
        }
    }
    private Set<File> listFiles(String directory) {
        File dir = new File(directory);
        if(!dir.exists()) {
            throw new RuntimeException("Log directory [" + directory + "] not exists!");
        }

        Set<File> files = new HashSet<>();
        for(File f : dir.listFiles()) {
            if(f.isFile()) {
                files.add(f);
            }
        }

        return files;
    }

    @Test
    public void tsWithTz() throws Exception {
        DateTime dt = DateTime.now();
        LOG.info(dt.toString());

        long ts = dt.getMillis();
        String tsWithTz = new DateTime(ts).toString();
        LOG.info(tsWithTz);
    }
}
