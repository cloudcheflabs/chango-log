package co.cloudcheflabs.chango.log.domain;

import java.io.Serializable;

public class LogPath implements Serializable {
    private String path;
    private String file;

    public void setPath(String path) {
        this.path = path;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getPath() {
        return path;
    }

    public String getFile() {
        return file;
    }
}
