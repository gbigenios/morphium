package de.caluga.test.mongo.suite;

import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.SafetyLevel;
import de.caluga.morphium.annotations.WriteSafety;
import de.caluga.morphium.driver.WriteConcern;
import de.caluga.test.mongo.suite.data.UncachedObject;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: Stephan Bösebeck
 * Date: 24.08.12
 * Time: 11:39
 * <p/>
 */
public class ReplicaSetStatusTest extends MorphiumTestBase {
    private static Logger log = LoggerFactory.getLogger(ReplicaSetStatusTest.class);

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
        assert (w.isJ());
        assert (!w.isFsync());
        assert (w.getWtimeout() == 10000);
        //        assert (w.raiseNetworkErrors());
    }

    @Entity
    @WriteSafety(level = SafetyLevel.WAIT_FOR_ALL_SLAVES, waitForJournalCommit = false, timeout = 10000)
    public class SecureObject extends UncachedObject {

    }
}
