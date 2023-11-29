package co.cloudcheflabs.chango.log.service;

import co.cloudcheflabs.chango.log.api.dao.KeyValueDao;
import co.cloudcheflabs.chango.log.api.service.LogFileService;
import co.cloudcheflabs.chango.log.domain.LogFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class LogFileServiceImpl implements LogFileService {

    @Autowired
    @Qualifier("rocksdbLogFileDao")
    private KeyValueDao<String, LogFile> logFileDao;

    @Override
    public void save(String key, LogFile logFile) {
        logFileDao.save(key, logFile);
    }

    @Override
    public LogFile find(String key, Class<LogFile> clazz) {
        return logFileDao.find(key, clazz);
    }

    @Override
    public void delete(String key) {
        logFileDao.delete(key);
    }
}
