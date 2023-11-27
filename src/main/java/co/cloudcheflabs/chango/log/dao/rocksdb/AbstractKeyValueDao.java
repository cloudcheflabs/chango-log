package co.cloudcheflabs.chango.log.dao.rocksdb;

import co.cloudcheflabs.chango.log.api.dao.KeyValueDao;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

public abstract class AbstractKeyValueDao<K, V> implements KeyValueDao<K, V>, InitializingBean {

    private static Logger LOG = LoggerFactory.getLogger(AbstractKeyValueDao.class);

    private Kryo kryo = new Kryo();

    @Autowired
    private Properties configuration;

    private String NAME = "log-file-db";

    private File dbDir;
    protected RocksDB rocksDB;

    public AbstractKeyValueDao(Class<V> clazz) {
        kryo.register(clazz);
    }

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
            }
            rocksDB = RocksDB.open(options, dbDir.getAbsolutePath());
        } catch(IOException | RocksDBException ex) {
            LOG.error("Error initializing RocksDB, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        LOG.info("RocksDB initialized and ready to use");
    }

    @Override
    public void save(K key, V value) {
        try {
            rocksDB.put(serialize(key), serialize(value));
        } catch (RocksDBException e) {
            LOG.error("Error saving entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }

    @Override
    public V find(K key, Class<V> clazz) {
        V result = null;
        try {
            if(rocksDB.keyExists(serialize(key))) {
                byte[] bytes = rocksDB.get(serialize(key));
                result = deserialize(bytes, clazz);
            } else {
                return null;
            }
        } catch (RocksDBException e) {
            LOG.error("Error retrieving the entry in RocksDB from key: {}, cause: {}, message: {}", key, e.getCause(), e.getMessage());
        }
        return result;
    }

    @Override
    public void delete(K key) {
        try {
            rocksDB.delete(serialize(key));
        } catch (RocksDBException e) {
            LOG.error("Error deleting entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }

    private <T> byte[]  serialize(T t) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Output output = new Output(os);
        kryo.writeObject(output, t);
        output.close();

        return os.toByteArray();
    }

    private <T> T deserialize(byte[] bytes, Class<T> clazz) {
        ByteArrayInputStream is = new ByteArrayInputStream(bytes);
        Input input = new Input(is);
        T t = kryo.readObject(input, clazz);
        input.close();

        return t;
    }
}
