package de.caluga.morphium.driver.singleconnect;

import de.caluga.morphium.driver.MorphiumDriver;
import de.caluga.morphium.driver.ReadPreference;
import de.caluga.morphium.driver.mongodb.Maximums;

import java.util.HashMap;
import java.util.Map;

/**
 * User: Stephan Bösebeck
 * Date: 03.12.15
 * Time: 22:36
 * <p>
 * TODO: Add documentation here
 */
public abstract class DriverBase implements MorphiumDriver {
    private volatile int rqid = 10000;
    private int maxWait = 1000;
    private boolean keepAlive = true;
    private int soTimeout = 1000;

    private Map<String, Map<String, char[]>> credentials;
    private int maxBsonObjectSize;
    private int maxMessageSize = 16 * 1024 * 1024;
    private int maxWriteBatchSize = 1000;
    private ReadPreference defaultRP;
    private boolean replicaSet = false;
    private String replicaSetName = null;
    private int retriesOnNetworkError = 5;
    private int sleepBetweenRetries = 100;
    private boolean defaultJ = false;
    private int defaultWriteTimeout = 1000;
    private int localThreshold = 0;
    private boolean defaultFsync = false;
    private int heartbeatConnectionTimeout = 1000;
    private String[] hostSeed;
    private int heartbeatSocketTimeout = 1000;
    private int heartbeatFrequency = 2000;
    private boolean useSSL = false;
    private int maxBlockingThreadsMultiplier = 5;
    private int defaultW = 1;
    private int connectionTimeout = 1000;
    private int maxConnectionIdleTime = 100000;
    private int maxConnectionLifetime = 600000;
    private int minConnectionsPerHost = 1;
    private int maxConnectionsPerHost = 100;

    @Override
    public void setCredentials(String db, String login, char[] pwd) {
        if (credentials == null) {
            credentials = new HashMap<>();
        }
        Map<String, char[]> cred = new HashMap<>();
        cred.put(login, pwd);
        credentials.put(db, cred);
    }

    @Override
    public boolean isReplicaset() {
        return replicaSet;
    }


    public String getReplicaSetName() {
        return replicaSetName;
    }

    public void setReplicaSetName(String replicaSetName) {
        this.replicaSetName = replicaSetName;
    }


    public Map<String, Map<String, char[]>> getCredentials() {
        return credentials;
    }

    @Override
    public void setRetriesOnNetworkError(int r) {
        if (r < 1) r = 1;
        retriesOnNetworkError = r;
    }

    @Override
    public int getRetriesOnNetworkError() {
        return retriesOnNetworkError;
    }

    @Override
    public void setSleepBetweenErrorRetries(int s) {
        if (s < 100) s = 100;
        sleepBetweenRetries = s;
    }

    @Override
    public int getSleepBetweenErrorRetries() {
        return sleepBetweenRetries;
    }


    @Override
    public void setDefaultReadPreference(ReadPreference rp) {
        defaultRP = rp;
    }

    public int getMaxBsonObjectSize() {
        return maxBsonObjectSize;
    }

    public void setMaxBsonObjectSize(int maxBsonObjectSize) {
        this.maxBsonObjectSize = maxBsonObjectSize;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public int getMaxWriteBatchSize() {
        return maxWriteBatchSize;
    }

    public void setMaxWriteBatchSize(int maxWriteBatchSize) {
        this.maxWriteBatchSize = maxWriteBatchSize;
    }


    public boolean isReplicaSet() {
        return replicaSet;
    }

    public void setReplicaSet(boolean replicaSet) {
        this.replicaSet = replicaSet;
    }


    public int getNextId() {
        return ++rqid;
    }


    @Override
    public void setDefaultJ(boolean j) {
        defaultJ = j;
    }

    @Override
    public void setDefaultWriteTimeout(int wt) {
        defaultWriteTimeout = wt;
    }

    @Override
    public void setLocalThreshold(int thr) {
        localThreshold = thr;
    }

    @Override
    public void setDefaultFsync(boolean j) {
        defaultFsync = j;
    }


    @Override
    public int getHeartbeatConnectTimeout() {
        return heartbeatConnectionTimeout;
    }

    @Override
    public void setHeartbeatConnectTimeout(int heartbeatConnectTimeout) {
        heartbeatConnectionTimeout = heartbeatConnectTimeout;
    }

    @Override
    public int getMaxWaitTime() {
        return this.maxWait;
    }

    @Override
    public void setMaxWaitTime(int maxWaitTime) {
        this.maxWait = maxWaitTime;
    }


    @Override
    public boolean isSocketKeepAlive() {
        return keepAlive;
    }

    @Override
    public void setSocketKeepAlive(boolean socketKeepAlive) {
        keepAlive = socketKeepAlive;
    }

    @Override
    public String[] getCredentials(String db) {
        return new String[0];
    }

    @Override
    public boolean isDefaultFsync() {
        return false;
    }

    @Override
    public String[] getHostSeed() {
        return hostSeed;
    }

    @Override
    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    @Override
    public int getMinConnectionsPerHost() {
        return minConnectionsPerHost;
    }

    @Override
    public int getMaxConnectionLifetime() {
        return maxConnectionLifetime;
    }

    @Override
    public int getMaxConnectionIdleTime() {
        return maxConnectionIdleTime;
    }

    @Override
    public int getSocketTimeout() {
        return soTimeout;
    }

    @Override
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @Override
    public int getDefaultW() {
        return defaultW;
    }

    @Override
    public int getMaxBlockintThreadMultiplier() {
        return maxBlockingThreadsMultiplier;
    }

    @Override
    public int getHeartbeatFrequency() {
        return heartbeatFrequency;
    }

    @Override
    public void setHeartbeatSocketTimeout(int heartbeatSocketTimeout) {
        this.heartbeatSocketTimeout = heartbeatSocketTimeout;
    }


    @Override
    public Maximums getMaximums() {
        Maximums max = new Maximums();
        max.setMaxBsonSize(maxBsonObjectSize);
        max.setMaxMessageSize(maxMessageSize);
        max.setMaxWriteBatchSize(maxWriteBatchSize);
        return max;
    }

    @Override
    public void setUseSSL(boolean useSSL) {

    }

    public ReadPreference getDefaultReadPreference() {
        return defaultRP;
    }

    @Override
    public void setHeartbeatFrequency(int heartbeatFrequency) {

    }

    @Override
    public void setWriteTimeout(int writeTimeout) {

    }

    @Override
    public void setDefaultBatchSize(int defaultBatchSize) {

    }

    @Override
    public void setCredentials(Map<String, String[]> credentials) {

    }

    @Override
    public int getHeartbeatSocketTimeout() {
        return heartbeatSocketTimeout;
    }

    @Override
    public boolean isUseSSL() {
        return useSSL;
    }

    @Override
    public boolean isDefaultJ() {
        return defaultJ;
    }

    @Override
    public int getWriteTimeout() {
        return defaultWriteTimeout;
    }

    @Override
    public int getLocalThreshold() {
        return localThreshold;
    }

    @Override
    public void setHostSeed(String... host) {
        hostSeed = host;
    }

    @Override
    public void setMaxConnectionsPerHost(int mx) {
        maxConnectionsPerHost = mx;
    }

    @Override
    public void setMinConnectionsPerHost(int mx) {
        minConnectionsPerHost = mx;
    }

    @Override
    public void setMaxConnectionLifetime(int timeout) {
        maxConnectionLifetime = timeout;
    }

    @Override
    public void setMaxConnectionIdleTime(int time) {
        maxConnectionIdleTime = time;
    }

    @Override
    public void setSocketTimeout(int timeout) {
        soTimeout = timeout;
    }

    @Override
    public void setConnectionTimeout(int timeout) {
        connectionTimeout = timeout;
    }

    @Override
    public void setDefaultW(int w) {
        defaultW = w;
    }

    @Override
    public void setMaxBlockingThreadMultiplier(int m) {
        maxBlockingThreadsMultiplier = m;
    }

    @Override
    public void heartBeatFrequency(int t) {
        heartbeatFrequency = t;
    }

    @Override
    public void heartBeatSocketTimeout(int t) {
        heartbeatSocketTimeout = t;
    }

    @Override
    public void useSsl(boolean ssl) {
        useSSL = ssl;
    }


}
