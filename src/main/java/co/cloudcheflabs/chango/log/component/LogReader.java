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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
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

    private long logInterval;
    private int logThreads;

    private LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private AtomicReference<Throwable> ex = new AtomicReference<>();

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

        token = configuration.getProperty("chango.token");
        dataApiUrl = configuration.getProperty("chango.dataApiUrl");
        schema = configuration.getProperty("chango.schema");
        table = configuration.getProperty("chango.table");
        batchSize = Integer.valueOf(configuration.getProperty("chango.batchSize"));
        interval = Long.valueOf(configuration.getProperty("chango.interval"));

        logInterval = Long.valueOf(configuration.getProperty("task.log.interval"));
        logThreads = Integer.valueOf(configuration.getProperty("task.log.threads"));

        // read logs and send them to data api.
        readLogs();
    }

    private void readLogs() {

        ExecutorService executor = Executors.newFixedThreadPool(logThreads);

        Timer timer = new Timer("Reading Log Timer");
        timer.schedule(
                new ReadLogTask(queue),
                5000,
                logInterval
        );
        LOG.info("Timer for reading log is running.");

        LOG.info("Ready to read logs...");
        Thread readLogThread = new Thread(new ReadLogRunnable(
                queue,
                executor,
                logPathMap,
                logFileService,
                token,
                dataApiUrl,
                schema,
                table,
                batchSize,
                interval,
                logThreads
        ));

        readLogThread.setUncaughtExceptionHandler((Thread t, Throwable e) -> {
            LOG.error(e.getMessage());
            ex.set(e);
        });
        readLogThread.start();
    }

    public static void pause(long pause) {
        try {
            Thread.sleep(pause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class ReadLogRunnable implements Runnable {

        private LinkedBlockingQueue<String> queue;
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

        private int threads;
        private Map<String, Future<String>> futureMap = new ConcurrentHashMap<>();

        public ReadLogRunnable(
                LinkedBlockingQueue<String> queue,
                ExecutorService executor,
                Map<String, LogPath> logPathMap,
                LogFileService logFileService,
                String token,
                String dataApiUrl,
                String schema,
                String table,
                int batchSize,
                long interval,
                int threads
        ) {
            this.queue = queue;
            this.executor = executor;
            this.logPathMap = logPathMap;
            this.logFileService = logFileService;
            this.token = token;
            this.dataApiUrl = dataApiUrl;
            this.schema = schema;
            this.table = table;
            this.batchSize = batchSize;
            this.interval = interval;
            this.threads = threads;

            constructChangoClient();

            // check if task of reading logs is finished.
            checkIfTaskFinished();
        }

        private void checkIfTaskFinished() {
            Thread t = new Thread(() -> {
                while (true) {
                    for(String key : futureMap.keySet()) {
                        Future<String> task = futureMap.get(key);
                        // if task is done, remove task from map.
                        if(task.isDone()) {
                            futureMap.remove(key);
                            //LOG.info("Reading log file {} finished.", key);
                        }
                    }

                    pause(1000);
                }
            });
            t.start();
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
            while (true) {
                String event = null;
                if(!queue.isEmpty()) {
                    event = queue.remove();
                }
                if(event != null) {
                    // read logs.
                    try {
                        doReadLogs();
                    } catch (Exception e) {
                        LOG.error(e.getMessage());
                        throw new RuntimeException(e);
                    }
                } else {
                    pause(1000);
                }
            }
        }

        private void doReadLogs() {
            int currentReadingLogTasks = logPathMap.keySet().size();
            if(currentReadingLogTasks >= threads) {
                LOG.info("Skip reading logs because current count of reading log tasks [{}] is greater than or equals to configured thread count [{}].",
                        currentReadingLogTasks, threads);
                return;
            }

            for(LogPath logPath : logPathMap.values()) {
                String path = logPath.getPath();
                String filePattern = logPath.getFile();

                Set<File> files = listFiles(path);
                Set<File> filteredFiles = new HashSet<>();
                if(filePattern == null) {
                    filteredFiles.addAll(files);
                } else {
                    // filter files with file pattern.
                    if(filePattern.indexOf("*") != -1) {
                        String[] patternToken = filePattern.split("\\*");
                        filePattern = "\\b" + patternToken[0] + "\\b" + ".*" + "\\b" + patternToken[1] + "\\b";
                    } else {
                        filePattern = "\\b" + filePattern + "\\b";
                    }

                    for(File f : files) {
                        String fileName = f.getName();
                        Pattern pattern = Pattern.compile(filePattern);
                        Matcher matcher = pattern.matcher(fileName);
                        if(matcher.matches()) {
                            filteredFiles.add(f);
                        }
                    }
                }

                //LOG.info("Selected log files: {}", JsonUtils.toJson(filteredFiles));

                for(File f : filteredFiles) {

                    // check if log file is being read.
                    String filePath = f.getAbsolutePath();
                    if(futureMap.containsKey(filePath)) {
                        if(!futureMap.get(filePath).isDone()) {
                            LOG.info("Skip reading log file {} which is being read now.", filePath);
                            continue;
                        }
                    }

                    Future<String> future = executor.submit(() -> {
                        // read log file.
                        String fileName = f.getName();
                        long lastModified = f.lastModified();

                        LogFile logFile = logFileService.find(filePath, LogFile.class);
                        if(logFile != null) {
                            if(lastModified == logFile.getLastModified()) {
                                //LOG.info("Log file {} not modified.", filePath);
                                return "Log file " + filePath + " not modified.";
                            } else if (lastModified > logFile.getLastModified()) {
                                long lastReadLineCount = logFile.getReadLineCount();
                                long newReadLineCount = sendLogsAt(f, lastReadLineCount);

                                // new entity for log file.
                                LogFile newLogFile = new LogFile(
                                        fileName,
                                        filePath,
                                        lastModified,
                                        newReadLineCount
                                );

                                // delete previous entity and save new entity.
                                logFileService.delete(filePath);
                                logFileService.save(filePath, newLogFile);
                            }
                        } else {
                            long newReadLineCount = sendLogsAt(f, 0);

                            // new entity for log file.
                            LogFile newLogFile = new LogFile(
                                    fileName,
                                    filePath,
                                    lastModified,
                                    newReadLineCount
                            );

                            // save new entity.
                            logFileService.save(filePath, newLogFile);
                        }

                        return "Reading log file " + filePath + " done.";
                    });
                    futureMap.put(filePath, future);
                    //LOG.info("Tasks of reading log file {} added.", filePath);
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
                        LOG.info("log: {}", log);

//                        // send logs.
//
//                        Map<String, Object> map = new HashMap<>();
//                        map.put("year", year);
//                        map.put("month", month);
//                        map.put("day", day);
//                        map.put("ts", ts);
//                        map.put("readableTs", readableTs);
//                        map.put("message", log);
//                        map.put("lineNumber", lineCount);
//                        map.put("fileName", fileName);
//                        map.put("filePath", filePath);
//                        map.put("hostName", hostName);
//                        map.put("hostAddress", hostAddress);
//
//                        String json = JsonUtils.toJson(map);
//
//                        try {
//                            // send json.
//                            changoClient.add(json);
//                        } catch (Exception e) {
//                            LOG.error(e.getMessage());
//
//                            // reconstruct chango client.
//                            constructChangoClient();
//                            LOG.info("Chango client reconstructed.");
//                            Thread.sleep(1000);
//                        }
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

    private static class ReadLogTask extends TimerTask {

        private LinkedBlockingQueue<String> queue;

        public ReadLogTask(LinkedBlockingQueue<String> queue) {
            this.queue = queue;
        }


        @Override
        public void run() {
            String event = "Read logs at " + DateTime.now().toString();
            this.queue.add(event);
        }
    }
}
