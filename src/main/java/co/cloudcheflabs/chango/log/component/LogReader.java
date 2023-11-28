package co.cloudcheflabs.chango.log.component;

import co.cloudcheflabs.chango.client.component.ChangoClient;
import co.cloudcheflabs.chango.log.api.service.LogFileService;
import co.cloudcheflabs.chango.log.domain.LogFile;
import co.cloudcheflabs.chango.log.domain.LogPath;
import co.cloudcheflabs.chango.log.util.JsonUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogReader implements InitializingBean {
    private static Logger LOG = LoggerFactory.getLogger(LogReader.class);

    @Autowired
    private Properties configuration;

    @Autowired
    private LogFileService logFileService;

    private Map<String, LogPath> logPathMap;

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

        LOG.info("Chango client constructed.");

        // read logs and send them to data api.
        readLogs();
    }

    private void readLogs() {

        ExecutorService executor = Executors.newFixedThreadPool(3);

        Timer timer = new Timer("Chango Private Metrics Timer");
        long intervalInMillis = 1000 * 10;
        timer.schedule(
                new ReadLogTask(
                        executor,
                        logPathMap,
                        logFileService,
                        token,
                        dataApiUrl,
                        schema,
                        table,
                        batchSize,
                        interval
                ),
                5000,
                intervalInMillis
        );


    }

    private static class ReadLogTask extends TimerTask {

        private ExecutorService executor;
        private ChangoClient changoClient;
        private Map<String, LogPath> logPathMap;
        private LogFileService logFileService;
        private String token;
        private String dataApiUrl;
        private String schema;
        private String table;
        private int batchSize;
        private long interval;

        public ReadLogTask(
                ExecutorService executor,
                Map<String, LogPath> logPathMap,
                LogFileService logFileService,
                String token,
                String dataApiUrl,
                String schema,
                String table,
                int batchSize,
                long interval
        ) {
            this.executor = executor;
            this.logPathMap = logPathMap;
            this.logFileService = logFileService;
            this.token = token;
            this.dataApiUrl = dataApiUrl;
            this.schema = schema;
            this.table = table;
            this.batchSize = batchSize;
            this.interval = interval;

            constructChangoClient();
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

        @Override
        public void run() {
            for(LogPath logPath : logPathMap.values()) {
                String path = logPath.getPath();
                String filePattern = logPath.getFile();

                Set<File> files = listFiles(path);
                Set<File> filteredFiles = new HashSet<>();
                if(filePattern == null) {
                    filteredFiles.addAll(files);
                } else {
                    String[] patternToken = filePattern.split("\\*");
                    filePattern = "\\b" + patternToken[0] + "\\b" + ".*" + "\\b" + patternToken[1] + "\\b";

                    for(File f : files) {
                        String fileName = f.getName();
                        Pattern pattern = Pattern.compile(filePattern);
                        Matcher matcher = pattern.matcher(fileName);
                        if(matcher.matches()) {
                            filteredFiles.add(f);
                        }
                    }
                }

                LOG.info("Selected log files: {}", JsonUtils.toJson(filteredFiles));

                for(File f : filteredFiles) {
                    Future<String> future = executor.submit(() -> {
                        // read log file.
                        String fileName = f.getName();
                        String absolutePath = f.getAbsolutePath();
                        long lastModified = f.lastModified();

                        LogFile logFile = logFileService.find(absolutePath, LogFile.class);
                        if(logFile != null) {
                            if(lastModified == logFile.getLastModified()) {
                                return "Log file " + f.getAbsolutePath() + " not modified.";
                            } else if (lastModified > logFile.getLastModified()) {
                                long lastReadLineCount = logFile.getReadLineCount();
                                long newReadLineCount = sendLogsAt(f, lastReadLineCount);

                                // new entity for log file.
                                LogFile newLogFile = new LogFile(
                                        fileName,
                                        absolutePath,
                                        lastModified,
                                        newReadLineCount
                                );

                                // delete previous entity and save new entity.
                                logFileService.delete(absolutePath);
                                logFileService.save(absolutePath, newLogFile);
                            }
                        } else {
                            long newReadLineCount = sendLogsAt(f, 0);

                            // new entity for log file.
                            LogFile newLogFile = new LogFile(
                                    fileName,
                                    absolutePath,
                                    lastModified,
                                    newReadLineCount
                            );

                            // save new entity.
                            logFileService.save(absolutePath, newLogFile);
                        }

                        return "Reading log file " + f.getAbsolutePath() + " done.";
                    });
                }
            }
        }

        private long sendLogsAt(File f, long lastReadLineCount) {
            long lineCount = 0;
            try{
                FileInputStream fileInputStream = new FileInputStream(f);
                BufferedReader br = new BufferedReader(new InputStreamReader(fileInputStream));
                String strLine;
                while ((strLine = br.readLine()) != null)   {
                    lineCount++;

                    if(lineCount > lastReadLineCount) {

                        DateTime dt = DateTime.now();

                        String year = String.valueOf(dt.getYear());
                        String month = padZero(dt.getMonthOfYear());
                        String day = padZero(dt.getDayOfMonth());
                        long ts = dt.getMillis(); // in milliseconds.
                        String readableTs = new DateTime(ts).toString();

                        String fileName = f.getName();
                        String filePath = f.getAbsolutePath();

                        InetAddress local = InetAddress.getLocalHost();
                        String hostName = local.getHostName();
                        String hostAddress = local.getHostAddress();

                        String log = strLine;

                        // send logs.

                        Map<String, Object> map = new HashMap<>();
                        map.put("year", year);
                        map.put("month", month);
                        map.put("day", day);
                        map.put("ts", ts);
                        map.put("readableTs", readableTs);
                        map.put("message", log);
                        map.put("lineNumber", lineCount);
                        map.put("fileName", fileName);
                        map.put("filePath", filePath);
                        map.put("hostName", hostName);
                        map.put("hostAddress", hostAddress);

                        String json = JsonUtils.toJson(map);

                        try {
                            // send json.
                            changoClient.add(json);
                        } catch (Exception e) {
                            LOG.error(e.getMessage());

                            // reconstruct chango client.
                            constructChangoClient();
                            LOG.info("Chango client reconstructed.");
                            Thread.sleep(1000);
                        }
                    }
                }
                fileInputStream.close();
            } catch (Exception e) {
                LOG.error("Error: " + e.getMessage());
            }

            return lineCount;
        }

        private String padZero(int value) {
            String strValue = String.valueOf(value);
            if(strValue.length() == 1) {
                strValue = "0" + strValue;
            }
            return strValue;
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
    }
}
