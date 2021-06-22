package de.caluga.test.mongo.suite.base;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.SafetyLevel;
import de.caluga.morphium.annotations.WriteSafety;
import de.caluga.morphium.driver.WriteConcern;
import de.caluga.morphium.replicaset.ReplicaSetNode;
import de.caluga.morphium.replicaset.ReplicaSetStatus;
import de.caluga.morphium.replicaset.ReplicasetStatusListener;
import de.caluga.test.mongo.suite.data.UncachedObject;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: Stephan Bösebeck
 * Date: 24.08.12
 * Time: 11:39
 * <p/>
 */
public class ReplicaSetStatusTest extends MorphiumTestBase {
    private static final Logger log = LoggerFactory.getLogger(ReplicaSetStatusTest.class);


    @Test
    public void testReplicaNode() throws Exception {
        if (!morphium.isReplicaSet()) {
            log.warn("Cannot test replicaset on non-replicaset installation");
            return;
        }
        int cnt = 0;
        ReplicaSetStatus currentRSState = null;
        while (currentRSState == null) {
            cnt++;
            currentRSState = morphium.getCurrentRSState();
            assert (cnt < 7);
            Thread.sleep(1000);
        }

        log.info("got status: " + currentRSState.getActiveNodes());

        log.info("Config: " + currentRSState.getConfig());
        log.info("    " + currentRSState.getConfig().getMemberList());
        log.info("    " + currentRSState.getConfig().getMembers().get(0).getHost());
        log.info("    " + currentRSState.getConfig().getId());
        log.info("    " + currentRSState.getConfig().getVersion());
        log.info("ElectionCandidate: " + currentRSState.getElectionCandidateMetrics());
        log.info("date: " + currentRSState.getDate());
        log.info("Set: " + currentRSState.getSet());
        log.info("SyncSource: " + currentRSState.getSyncSourceHost());
        log.info("Heartbeat Interval: " + currentRSState.getHeartbeatIntervalMillis());
        log.info("Majority Vote count: " + currentRSState.getMajorityVoteCount());
        log.info("last stable recovery timestamp: " + currentRSState.getLastStableRecoveryTimestamp());
        log.info("my state: " + currentRSState.getMyState());
        log.info("sync source id: " + currentRSState.getSyncSourceId());
        log.info("term: " + currentRSState.getTerm());
        log.info("voting members count: " + currentRSState.getVotingMembersCount());
        log.info("writable voting members count: " + currentRSState.getWritableVotingMembersCount());
        log.info("writable majority count: " + currentRSState.getWriteMajorityCount());
        log.info("optimes: " + currentRSState.getOptimes());

        for (ReplicaSetNode node : currentRSState.getMembers()) {

            log.info("--------->  node: " + node.getName());
            log.info("            info: " + node.getInfoMessage());
            log.info("            lastHeartbeat: " + node.getLastHeartbeatMessage());
            log.info("            stateStr: " + node.getStateStr() + "(" + node.getState() + ")");
            log.info("            health: " + node.getHealth());
            log.info("            optimedate: " + node.getOptimeDate());
            log.info("            pingms: " + node.getPingMs());
            log.info("            configversion: " + node.getConfigVersion());
            log.info("            uptime: " + node.getUptime());
            log.info("            id: " + node.getId());
            log.info("            election date: " + node.getElectionDate());
            log.info("            sync source host: " + node.getSyncSourceHost());
            log.info("            sync source id: " + node.getSyncSourceId());
            log.info("            configterm: " + node.getConfigTerm());
            String s = node.toString();
        }


    }

    @Test
    public void testReplicaSetMonitoring() throws Exception {
        if (!morphium.isReplicaSet()) {
            log.warn("Cannot test replicaset on non-replicaset installation");
            return;
        }
        int cnt = 0;
        while (morphium.getCurrentRSState() == null) {
            cnt++;
            assert (cnt < 7);
            Thread.sleep(1000);
        }

        log.info("got status: " + morphium.getCurrentRSState().getActiveNodes());
    }

    @Test
    public void testWriteConcern() {
        if (!morphium.isReplicaSet()) {
            log.warn("Cannot test replicaset on non-replicaset installation");
            return;
        }
        WriteConcern w = morphium.getWriteConcernForClass(SecureObject.class);
        int c = morphium.getCurrentRSState().getActiveNodes();
        assert (w.getW() == c) : "W=" + w.getW() + " but should be: " + c;
        assert (w.getWtimeout() == 10000);
        //        assert (w.raiseNetworkErrors());
    }


    @Test
    public void rsStatusListenerTest() throws InterruptedException {
        if (!morphium.isReplicaSet()) {
            log.warn("Cannot test replicaset on non-replicaset installation");
            return;
        }
        final AtomicInteger cnt = new AtomicInteger(0);
        morphium.addReplicasetStatusListener(new ReplicasetStatusListener() {
            @Override
            public void gotNewStatus(Morphium morphium, ReplicaSetStatus status) {
                cnt.incrementAndGet();
                log.info(status.toString());
            }

            @Override
            public void onGetStatusFailure(Morphium morphium, int numErrors) {

            }

            @Override
            public void onMonitorAbort(Morphium morphium, int numErrors) {

            }

            @Override
            public void onHostDown(Morphium morphium, List<String> hostsDown, List<String> currentHostSeed) {

            }
        });
        log.info("Waiting for RSMonitor to inform us: " + morphium.getConfig().getReplicaSetMonitoringTimeout() * 2);
        Thread.sleep(morphium.getConfig().getReplicaSetMonitoringTimeout() * 2);
        assert (cnt.get() > 0);
    }

    @Entity
    @WriteSafety(level = SafetyLevel.WAIT_FOR_ALL_SLAVES, waitForJournalCommit = false, timeout = 10000)
    public class SecureObject extends UncachedObject {

    }
}
