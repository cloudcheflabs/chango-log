package co.cloudcheflabs.chango.log.dao.rocksdb;

import co.cloudcheflabs.chango.log.domain.LogFile;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Repository
public class RocksdbLogFileDao extends AbstractKeyValueDao<String, LogFile> {

    private static Logger LOG = LoggerFactory.getLogger(RocksdbLogFileDao.class);

    private String NAME = "log-file-db";

    private File dbDir;

    @Override
    public void afterPropertiesSet() throws Exception {
        String rocksDbDir = configuration.getProperty("rocksdb.directory");

        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);
        dbDir = new File(rocksDbDir, NAME);
        try {
            if(!dbDir.exists()) {
                Files.createDirectories(dbDir.getParentFile().toPath());
                Files.createDirectories(dbDir.getAbsoluteFile().toPath());
                LOG.info("RocksDB directory {} created.", rocksDbDir);
            }
            rocksDB = RocksDB.open(options, dbDir.getAbsolutePath());
        } catch(IOException | RocksDBException ex) {
            LOG.error("Error initializing RocksDB, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        LOG.info("RocksDB initialized and ready to use");
    }

    public RocksdbLogFileDao() {
        super(LogFile.class);
    }
}
