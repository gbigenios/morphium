package de.caluga.morphium.replicaset;

import de.caluga.morphium.Morphium;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Used in a Thread or executor.
 * Checks for the replicaset status periodically.
 * Used in order to get number of currently active nodes and their state
 */
@SuppressWarnings("WeakerAccess")
public class RSMonitor {
    private static final Logger logger = LoggerFactory.getLogger(RSMonitor.class);
    private final ScheduledThreadPoolExecutor executorService;
    private final Morphium morphium;
    private ReplicaSetStatus currentStatus;
    private int nullcounter = 0;
    private final List<ReplicasetStatusListener> listeners = new Vector<>();

    public RSMonitor(Morphium morphium) {
        this.morphium = morphium;
        executorService = new ScheduledThreadPoolExecutor(1);
        executorService.setThreadFactory(new ThreadFactory() {
            private final AtomicInteger num = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread ret = new Thread(r, "rsMonitor " + num);
                num.set(num.get() + 1);
                ret.setDaemon(true);
                return ret;
            }
        });
    }

    public void start() {
        executorService.scheduleWithFixedDelay(this::execute, 1000, morphium.getConfig().getReplicaSetMonitoringTimeout(), TimeUnit.MILLISECONDS);
        execute();
    }


    public void addListener(ReplicasetStatusListener lst) {
        listeners.add(lst);
    }

    public void removeListener(ReplicasetStatusListener lst) {
        listeners.remove(lst);
    }

    public void terminate() {
        executorService.shutdownNow();
        while (!executorService.isShutdown()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                //ignored
            }
        }
    }

    public void execute() {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Getting RS-Status...");
            }
            currentStatus = getReplicaSetStatus(true);
            if (currentStatus == null) {
                nullcounter++;
                if (logger.isDebugEnabled()) {
                    logger.debug("RS status is null! Counter " + nullcounter);
                }
                for (ReplicasetStatusListener l : listeners) l.onGetStatusFailure(morphium, nullcounter);
            } else {
                nullcounter = 0;
            }
            if (nullcounter > 10) {
                logger.error("Getting ReplicasetStatus failed 10 times... will gracefully exit thread");
                executorService.shutdownNow();
                for (ReplicasetStatusListener l : listeners) l.onMonitorAbort(morphium, nullcounter);

            }
            if (currentStatus != null) {
                for (ReplicasetStatusListener l : listeners) {
                    l.gotNewStatus(morphium, currentStatus);
                }

                for (ReplicaSetNode n : currentStatus.getMembers()) {
                    if (morphium.getConfig().getHostSeed().contains(n.getName())) {
                        logger.debug("Found host in config " + n.getName());
                    } else {
                        morphium.getConfig().getHostSeed().add(n.getName());
                    }
                }
                List<String> hostsNotFound = new ArrayList<>();
                for (String host : morphium.getConfig().getHostSeed()) {
                    boolean found = false;
                    for (ReplicaSetNode n : currentStatus.getMembers()) {
                        if (n.getName().equals(host)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        hostsNotFound.add(host);
                    }
                }
                if (!hostsNotFound.isEmpty()) {
                    morphium.getConfig().getHostSeed().removeAll(hostsNotFound);
                    for (ReplicasetStatusListener l : listeners)
                        l.onHostDown(morphium, hostsNotFound, morphium.getConfig().getHostSeed());
                }
            }
        } catch (Exception ignored) {
            // ignored
        }
    }


    /**
     * get the current replicaset status - issues the replSetGetStatus command to mongo
     * if full==true, also the configuration is read. This method is called with full==false for every write in
     * case a Replicaset is configured to find out the current number of active nodes
     *
     * @param full - if true- return full status
     * @return status
     */
    @SuppressWarnings("unchecked")
    public de.caluga.morphium.replicaset.ReplicaSetStatus getReplicaSetStatus(boolean full) {
        if (morphium.isReplicaSet()) {
            try {
                Map<String, Object> res = morphium.getDriver().getReplsetStatus();
                de.caluga.morphium.replicaset.ReplicaSetStatus status = morphium.getMapper().deserialize(de.caluga.morphium.replicaset.ReplicaSetStatus.class, res);

                if (full) {
                    Map<String, Object> findMetaData = new HashMap<>();
                    List<Map<String, Object>> stats = morphium.getDriver().find("local", "system.replset", new HashMap<>(), null, null, 0, 10, 10, null, null, findMetaData);
                    if (stats == null || stats.isEmpty()) {
                        logger.debug("could not get replicaset status");
                    } else {
                        Map<String, Object> stat = stats.get(0);
                        //                    DBCursor rpl = morphium.getDriver().getDB("local").getCollection("system.replset").find();
                        //                    DBObject stat = rpl.next(); //should only be one, i think
                        //                    rpl.close();
                        ReplicaSetConf cfg = morphium.getMapper().deserialize(ReplicaSetConf.class, stat);
//                        List<Object> mem = cfg.getMemberList();
//                        List<ConfNode> cmembers = new ArrayList<>();
//
//                        for (Object o : mem) {
//                            //                        DBObject dbo = (DBObject) o;
//                            ConfNode cn = (ConfNode) o;// objectMapper.deserialize(ConfNode.class, dbo);
//                            cmembers.add(cn);
//                        }
//                        cfg.setMembers(cmembers);
                        status.setConfig(cfg);
                    }
                }
                //de-referencing list
                List lst = status.getMembers();
                List<ReplicaSetNode> members = new ArrayList<>();
                if (lst != null) {
                    for (Object l : lst) {
                        //                    DBObject o = (DBObject) l;
                        ReplicaSetNode n = (ReplicaSetNode) l;//objectMapper.deserialize(ReplicaSetNode.class, o);
                        members.add(n);
                    }
                }
                status.setMembers(members);


                //getting max limits
                //	"maxBsonObjectSize" : 16777216,
                //                "maxMessageSizeBytes" : 48000000,
                //                        "maxWriteBatchSize" : 1000,
                return status;
            } catch (Exception e) {
                logger.warn("Could not get Replicaset status: " + e.getMessage(), e);
                if (e.getMessage().contains(" 'not running with --replSet'")) {
                    logger.warn("Mongo not configured for replicaset! Disabling monitoring for now");
                    morphium.getConfig().setReplicasetMonitoring(false);
                    terminate();
                }
                logger.warn("Tried connection to: ");
                for (String adr : morphium.getConfig().getHostSeed()) {
                    logger.warn("   " + adr);
                }
            }
        }
        return null;
    }


    public ReplicaSetStatus getCurrentStatus() {
        return currentStatus;
    }
}
