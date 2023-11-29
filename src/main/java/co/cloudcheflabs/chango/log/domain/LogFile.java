package co.cloudcheflabs.chango.log.domain;

import java.io.Serializable;

public class LogFile implements Serializable {

    private String fileName;
    private String filePath;

    private long lastModified;
    private long readLineCount;

    public LogFile() {}

    public LogFile(String fileName,
                   String filePath,
                   long lastModified,
                   long readLineCount) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.lastModified = lastModified;
        this.readLineCount = readLineCount;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getLastModified() {
        return lastModified;
    }

    public long getReadLineCount() {
        return readLineCount;
    }
}
