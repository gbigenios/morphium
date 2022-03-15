package de.caluga.morphium.driver;/**
 * Created by stephan on 15.10.15.
 */

import com.mongodb.event.ClusterListener;
import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;
import de.caluga.morphium.Collation;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.driver.bulk.BulkRequestContext;
import de.caluga.morphium.driver.mongodb.Maximums;

import javax.net.ssl.SSLContext;
import java.util.List;
import java.util.Map;

/**
 * Morphium driver interface
 * <p>
 * All drivers need to implement this interface. you can add your own drivers to morphium. These are actually not
 * limited to be mongodb drivers. There is also an InMemory implementation.
 **/
@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "RedundantThrows", "UnusedReturnValue"})
public interface MorphiumDriver {
    String VERSION_NAME = "morphium version";

    String getAtlasUrl();

    void setAtlasUrl(String atlasUrl);

    List<String> listDatabases() throws MorphiumDriverException;

    void setCredentials(String db, String login, char[] pwd);

    @SuppressWarnings("unused")
    boolean isReplicaset();

    @SuppressWarnings("unused")
    String[] getCredentials(String db);

    @SuppressWarnings("unused")
    boolean isDefaultFsync();

    @SuppressWarnings("unused")
    void setDefaultFsync(boolean j);

    String[] getHostSeed();

    void setHostSeed(String... host);


    int getMaxConnections();

    void setMaxConnections(int maxConnections);


    int getMinConnections();

    void setMinConnections(int minConnections);

    @SuppressWarnings("unused")
    int getMaxConnectionLifetime();

    void setMaxConnectionLifetime(int timeout);

    @SuppressWarnings("unused")
    int getMaxConnectionIdleTime();

    void setMaxConnectionIdleTime(int time);


    @SuppressWarnings("unused")
    int getConnectionTimeout();

    void setConnectionTimeout(int timeout);

    @SuppressWarnings("unused")
    int getDefaultW();

    @SuppressWarnings("unused")
    void setDefaultW(int w);

    @SuppressWarnings("unused")
    int getHeartbeatFrequency();

    void setHeartbeatFrequency(int heartbeatFrequency);

    @SuppressWarnings("unused")
    void setDefaultBatchSize(int defaultBatchSize);

    @SuppressWarnings("unused")
    void setCredentials(Map<String, String[]> credentials);


    boolean isRetryReads();

    void setRetryReads(boolean retryReads);

    boolean isRetryWrites();

    void setRetryWrites(boolean retryWrites);

    String getUuidRepresentation();

    void setUuidRepresentation(String uuidRepresentation);

    @SuppressWarnings("unused")
    boolean isUseSSL();

    @SuppressWarnings("unused")
    void setUseSSL(boolean useSSL);

    @SuppressWarnings("unused")
    boolean isDefaultJ();

    @SuppressWarnings("unused")
    void setDefaultJ(boolean j);

    int getLocalThreshold();

    void setLocalThreshold(int thr);

    int getReadTimeout();

    void setReadTimeout(int readTimeout);

    @SuppressWarnings("unused")
    void heartBeatFrequency(int t);

    @SuppressWarnings("unused")
    void useSsl(boolean ssl);

    void connect() throws MorphiumDriverException;

    void setDefaultReadPreference(ReadPreference rp);

    void connect(String replicasetName) throws MorphiumDriverException;

    @SuppressWarnings("unused")
    Maximums getMaximums();

    boolean isConnected();

    @SuppressWarnings("unused")
    int getDefaultWriteTimeout();

    @SuppressWarnings("unused")
    void setDefaultWriteTimeout(int wt);

    @SuppressWarnings("unused")
    int getRetriesOnNetworkError();

    @SuppressWarnings("unused")
    void setRetriesOnNetworkError(int r);

    @SuppressWarnings("unused")
    int getSleepBetweenErrorRetries();

    @SuppressWarnings("unused")
    void setSleepBetweenErrorRetries(int s);

    void close() throws MorphiumDriverException;

    Map<String, Object> getReplsetStatus() throws MorphiumDriverException;

    @SuppressWarnings("unused")
    Map<String, Object> getDBStats(String db) throws MorphiumDriverException;

    Map<String, Object> getCollStats(String db, String coll) throws MorphiumDriverException;

    @SuppressWarnings("unused")
    Map<String, Object> getOps(long threshold) throws MorphiumDriverException;

    Map<String, Object> runCommand(String db, Map<String, Object> cmd) throws MorphiumDriverException;

    MorphiumCursor initAggregationIteration(String db, String collection, List<Map<String, Object>> aggregationPipeline, ReadPreference readPreference, Collation collation, int batchSize, Map<String, Object> findMetaData) throws MorphiumDriverException;

    MorphiumCursor initIteration(String db, String collection, Map<String, Object> query, Map<String, Integer> sort, Map<String, Object> projection, int skip, int limit, int batchSize, ReadPreference readPreference, Collation coll, Map<String, Object> findMetaData) throws MorphiumDriverException;

    void watch(String db, int maxWait, boolean fullDocumentOnUpdate, List<Map<String, Object>> pipeline, DriverTailableIterationCallback cb) throws MorphiumDriverException;

    void watch(String db, String collection, int maxWait, boolean fullDocumentOnUpdate, List<Map<String, Object>> pipeline, DriverTailableIterationCallback cb) throws MorphiumDriverException;

    void tailableIteration(String db, String collection, Map<String, Object> query, Map<String, Integer> sort, Map<String, Object> projection, int skip, int limit, int batchSize, ReadPreference readPreference, int timeout, DriverTailableIterationCallback cb) throws MorphiumDriverException;

    MorphiumCursor nextIteration(MorphiumCursor crs) throws MorphiumDriverException;

    void closeIteration(MorphiumCursor crs) throws MorphiumDriverException;

    List<Map<String, Object>> find(String db, String collection, Map<String, Object> query, Map<String, Integer> sort, Map<String, Object> projection, int skip, int limit, int batchSize, ReadPreference readPreference, Collation collation, final Map<String, Object> findMetaData) throws MorphiumDriverException;

    long count(String db, String collection, Map<String, Object> query, Collation collation, ReadPreference rp) throws MorphiumDriverException;

    long estimatedDocumentCount(String db, String collection, ReadPreference rp);


    /**
     * just insert - no special handling
     *
     * @param db
     * @param collection
     * @param wc
     * @throws MorphiumDriverException
     */
    void insert(String db, String collection, List<Map<String, Object>> objs, WriteConcern wc) throws MorphiumDriverException;

    /**
     * store - if id == null, create it...
     *
     * @param db
     * @param collection
     * @param objs
     * @param wc
     * @throws MorphiumDriverException
     */
    Map<String, Integer> store(String db, String collection, List<Map<String, Object>> objs, WriteConcern wc) throws MorphiumDriverException;


    Map<String, Object> update(String db, String collection, Map<String, Object> query, Map<String, Object> op, boolean multiple, boolean upsert, Collation collation, WriteConcern wc) throws MorphiumDriverException;

    Map<String, Object> delete(String db, String collection, Map<String, Object> query, boolean multiple, Collation collation, WriteConcern wc) throws MorphiumDriverException;

    void drop(String db, String collection, WriteConcern wc) throws MorphiumDriverException;

    @SuppressWarnings("unused")
    void drop(String db, WriteConcern wc) throws MorphiumDriverException;

    @SuppressWarnings("unused")
    boolean exists(String db) throws MorphiumDriverException;

    List<Object> distinct(String db, String collection, String field, final Map<String, Object> filter, Collation collation, ReadPreference rp) throws MorphiumDriverException;

    boolean exists(String db, String collection) throws MorphiumDriverException;

    List<Map<String, Object>> getIndexes(String db, String collection) throws MorphiumDriverException;

    @SuppressWarnings("unused")
    List<String> getCollectionNames(String db) throws MorphiumDriverException;

    Map<String, Object> findAndOneAndDelete(String db, String col, Map<String, Object> query, Map<String, Integer> sort, Collation collation) throws MorphiumDriverException;

    Map<String, Object> findAndOneAndUpdate(String db, String col, Map<String, Object> query, Map<String, Object> update, Map<String, Integer> sort, Collation collation) throws MorphiumDriverException;

    Map<String, Object> findAndOneAndReplace(String db, String col, Map<String, Object> query, Map<String, Object> replacement, Map<String, Integer> sort, Collation collation) throws MorphiumDriverException;


    @SuppressWarnings("RedundantThrows")
    List<Map<String, Object>> aggregate(String db, String collection, List<Map<String, Object>> pipeline, boolean explain, boolean allowDiskUse, Collation collation, ReadPreference readPreference) throws MorphiumDriverException;

    @SuppressWarnings("unused")
    int getMaxWaitTime();

    void setMaxWaitTime(int maxWaitTime);

    int getServerSelectionTimeout();

    void setServerSelectionTimeout(int serverSelectionTimeout);

    boolean isCapped(String db, String coll) throws MorphiumDriverException;

    BulkRequestContext createBulkContext(Morphium m, String db, String collection, boolean ordered, WriteConcern wc);

    void createIndex(String db, String collection, Map<String, Object> index, Map<String, Object> options) throws MorphiumDriverException;


    List<Map<String, Object>> mapReduce(String db, String collection, String mapping, String reducing) throws MorphiumDriverException;

    List<Map<String, Object>> mapReduce(String db, String collection, String mapping, String reducing, Map<String, Object> query) throws MorphiumDriverException;

    List<Map<String, Object>> mapReduce(String db, String collection, String mapping, String reducing, Map<String, Object> query, Map<String, Object> sorting, Collation collation) throws MorphiumDriverException;

    void addCommandListener(CommandListener cmd);

    void removeCommandListener(CommandListener cmd);

    void addClusterListener(ClusterListener cl);

    void removeClusterListener(ClusterListener cl);

    void addConnectionPoolListener(ConnectionPoolListener cpl);

    void removeConnectionPoolListener(ConnectionPoolListener cpl);

    /**
     * list collections whose name match the pattern
     *
     * @param pattern regular expression for the collection, might be null
     * @return
     */
    List<String> listCollections(String db, String pattern) throws MorphiumDriverException;


    void startTransaction();

    void commitTransaction();

    MorphiumTransactionContext getTransactionContext();

    void setTransactionContext(MorphiumTransactionContext ctx);

    void abortTransaction();

    SSLContext getSslContext();

    void setSslContext(SSLContext sslContext);

    boolean isSslInvalidHostNameAllowed();

    void setSslInvalidHostNameAllowed(boolean sslInvalidHostNameAllowed);
}
