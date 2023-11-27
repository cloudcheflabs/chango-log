package co.cloudcheflabs.chango.log.dao.rocksdb;

import co.cloudcheflabs.chango.log.domain.LogFile;
import org.springframework.stereotype.Repository;

@Repository
public class RocksdbLogFileDao extends AbstractKeyValueDao<String, LogFile> {

    public RocksdbLogFileDao() {
        super(LogFile.class);
    }
}
