package co.cloudcheflabs.chango.log.api.service;

import co.cloudcheflabs.chango.log.domain.LogFile;

public interface LogFileService {

    void save(String key, LogFile logFile);
    LogFile find(String key, Class<LogFile> clazz);
    void delete(String key);
}
