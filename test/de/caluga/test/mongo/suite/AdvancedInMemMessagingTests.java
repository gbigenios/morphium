package de.caluga.test.mongo.suite;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.MessageListener;
import de.caluga.morphium.messaging.Messaging;
import de.caluga.morphium.messaging.Msg;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdvancedInMemMessagingTests extends InMemTest {
    private Map<MorphiumId, Integer> counts = new ConcurrentHashMap<>();

    @Test
    public void testExclusiveMessages() throws Exception {
        counts.clear();
        Messaging m1 = new Messaging(morphium, 10, false, false, 10);
        m1.start();

        Messaging m2 = new Messaging(morphium, 10, false, false, 10);
//        m2.setUseChangeStream(false);
        m2.start();

        Messaging m3 = new Messaging(morphium, 10, false, false, 10);
//        m3.setUseChangeStream(false);
        m3.start();

        Messaging m4 = new Messaging(morphium, 10, false, false, 10);
//        m4.setUseChangeStream(false);
        m4.start();

        MessageListener<Msg> msgMessageListener = new MessageListener<Msg>() {
            @Override
            public Msg onMessage(Messaging msg, Msg m) throws InterruptedException {
                log.info("Received " + m.getMsgId() + " created " + (System.currentTimeMillis() - m.getTimestamp()) + "ms ago");
                counts.putIfAbsent(m.getMsgId(), 0);
                counts.put(m.getMsgId(), counts.get(m.getMsgId()) + 1);
                Thread.sleep(100);
                return null;
            }
        };

        m2.addListenerForMessageNamed("test", msgMessageListener);
        m3.addListenerForMessageNamed("test", msgMessageListener);
        m4.addListenerForMessageNamed("test", msgMessageListener);

        for (int i = 0; i < 500; i++) {
            Msg m = new Msg("test", "test msg", "value");
            m.setMsgId(new MorphiumId());
            m.setExclusive(true);
            m1.storeMessage(m);

        }

        while (counts.size() < 500) {
            log.info("-----> Messages processed so far: " + counts.size());
            for (MorphiumId id : counts.keySet()) {
                assert (counts.get(id) <= 1) : "Count for id " + id.toString() + " is " + counts.get(id);
            }
            Thread.sleep(1000);
        }

        m1.terminate();
        m2.terminate();
        m3.terminate();
        m4.terminate();

    }
}
