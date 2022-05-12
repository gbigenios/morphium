package de.caluga.test.mongo.suite.messaging;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.messaging.Messaging;
import de.caluga.morphium.messaging.Msg;
import de.caluga.test.mongo.suite.base.MorphiumTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SeveralSystemsTests extends MorphiumTestBase {
    private final List<Morphium> morphiums = new Vector<>();
    private final List<Messaging> messagings = new Vector<>();

    private void createIndependentMessagings(int num, boolean processMultiple, int pause, boolean changeStream) {
        for (int i = 0; i < num; i++) {
            Morphium m = new Morphium(MorphiumConfig.fromProperties(morphium.getConfig().asProperties()));
            morphiums.add(m);
            Messaging msg = new Messaging(m, pause, processMultiple);
            msg.setUseChangeStream(changeStream);
            msg.setSenderId("Msg" + i);
            messagings.add(msg);
            msg.start();
        }
    }


    private void terminateAll() {
        List<Thread> threads = new ArrayList<>();
        for (Messaging m : messagings) {
            Thread t = new Thread() {
                private Messaging m;

                public Thread init(Messaging m) {
                    this.m = m;
                    return this;
                }

                public void run() {
                    log.info("Terminating " + m.getSenderId());
                    m.terminate();
                }
            }.init(m);
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
            }
        }
        messagings.clear();
        threads.clear();
        for (Morphium m : morphiums) {
            Thread t = new Thread() {
                private Morphium m;

                public Thread init(Morphium m) {
                    this.m = m;
                    return this;
                }

                public void run() {
                    m.close();
                }
            }.init(m);
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
            }
        }


    }


    @Test
    public void simpleBroadcastChangeStream() throws Exception {
        morphium.dropCollection(Msg.class, "msg", null);
        Thread.sleep(100);
        final AtomicInteger cnt = new AtomicInteger();
        Messaging m1 = new Messaging(morphium, 10, false);
        m1.start();

        try {
            createIndependentMessagings(10, true, 10, true);
            for (Messaging m : messagings) {
                m.addMessageListener((ms, msg) -> {
                    log.info("incoming message to " + ms.getSenderId());
                    cnt.incrementAndGet();
                    return null;
                });
            }

            //broadcast
            for (int i = 0; i < 10; i++) {
                m1.sendMessage(new Msg("test", "msg", "value"));
                long s = System.currentTimeMillis();
                while (cnt.get() < 10) {
                    Thread.sleep(100);
                    assert (System.currentTimeMillis() - s < 35000);
                }
                assert (cnt.get() == 10) : "Count wrong: " + cnt.get();
                cnt.set(0);
            }
        } finally {
            m1.terminate();
            terminateAll();
        }
    }

    @Test
    public void simpleBroadcastNoChangeStream() throws Exception {
        morphium.dropCollection(Msg.class, "msg", null);
        Thread.sleep(100);
        final AtomicInteger cnt = new AtomicInteger();
        Messaging m1 = new Messaging(morphium, 10, false);
        m1.start();

        try {
            createIndependentMessagings(10, true, 10, false);
            for (Messaging m : messagings) {
                m.addMessageListener((ms, msg) -> {
                    log.info("incoming message to " + ms.getSenderId());
                    cnt.incrementAndGet();
                    return null;
                });
            }

            //broadcast
            for (int i = 0; i < 10; i++) {
                m1.sendMessage(new Msg("test", "msg", "value"));
                Thread.sleep(400);
                assert (cnt.get() == 10) : "Count wrong: " + cnt.get();
                cnt.set(0);
            }
        } finally {
            m1.terminate();
            terminateAll();
        }
    }

    @Test
    public void simpleExclusiveChangeSteram() throws Exception {
        morphium.dropCollection(Msg.class, "msg", null);
        Thread.sleep(100);
        final AtomicInteger cnt = new AtomicInteger();
        Messaging m1 = new Messaging(morphium, 10, false);
        m1.start();

        try {
            createIndependentMessagings(10, true, 10, true);
            for (Messaging m : messagings) {
                m.addMessageListener((ms, msg) -> {
                    log.info("incoming message to " + ms.getSenderId());
                    cnt.incrementAndGet();
                    return null;
                });
            }

            //broadcase
            for (int i = 0; i < 20; i++) {
                m1.sendMessage(new Msg("test", "msg", "value", 30000, true));
                while (cnt.get() < 1) {
                    Thread.sleep(100);
                }
                assert (cnt.get() == 1);
                cnt.set(0);
            }
        } finally {
            m1.terminate();
            terminateAll();
        }
    }


    @Test
    public void simpleExclusiveNoChangeStream() throws Exception {
        morphium.dropCollection(Msg.class, "msg", null);
        Thread.sleep(100);
        final AtomicInteger cnt = new AtomicInteger();
        Messaging m1 = new Messaging(morphium, 10, false);
        m1.start();

        try {
            createIndependentMessagings(10, true, 10, false);
            for (Messaging m : messagings) {
                m.addMessageListener((ms, msg) -> {
                    log.info("incoming message to " + ms.getSenderId());
                    cnt.incrementAndGet();
                    return null;
                });
            }

            //broadcase
            for (int i = 0; i < 20; i++) {
                m1.sendMessage(new Msg("test", "msg", "value", 30000, true));
                while (cnt.get() < 1) {
                    Thread.sleep(100);
                }
                assert (cnt.get() == 1);
                cnt.set(0);
            }
        } finally {
            m1.terminate();
            terminateAll();
        }
    }


    //test "singleton" execution with several systems using exclusive messages and pausing!
    @Test
    public void simpleExclusivePausingChangeStream() throws Exception {
        morphium.dropCollection(Msg.class, "msg", null);
        Thread.sleep(100);
        final AtomicInteger cnt = new AtomicInteger();
        final Map<String, AtomicInteger> countById = new ConcurrentHashMap<>();
        Messaging m1 = new Messaging(morphium, 10, false);
        m1.start();

        try {
            createIndependentMessagings(10, false, 10, true);
            for (Messaging m : messagings) {
                m.addMessageListener((ms, msg) -> {
                    ms.pauseProcessingOfMessagesNamed(msg.getName());
                    log.info("incoming message to " + ms.getSenderId());
                    Thread.sleep(250);
                    cnt.incrementAndGet();
                    countById.putIfAbsent(ms.getSenderId(), new AtomicInteger());
                    countById.get(ms.getSenderId()).incrementAndGet();
                    ms.unpauseProcessingOfMessagesNamed(msg.getName());
                    return null;
                });
            }


            for (int j = 0; j < 5; j++) {
                countById.clear();
                cnt.set(0);
                for (int i = 0; i < 10; i++) {
                    m1.sendMessage(new Msg("test", "msg", "value", 30000, true));
                }

                while (cnt.get() < 10) {
                    Thread.sleep(100);
                }
                assert (cnt.get() == 10);
                for (Map.Entry<String, AtomicInteger> e : countById.entrySet()) {
                    assert (e.getValue().get() < 8) : "Count wrong for " + e.getKey() + ": " + e.getValue();
                }
            }

        } finally {
            m1.terminate();
            terminateAll();
        }
    }

    @Test
    public void simpleExclusivePausingNoChangeStream() throws Exception {
        morphium.dropCollection(Msg.class, "msg", null);
        Thread.sleep(100);
        final AtomicInteger cnt = new AtomicInteger();
        final Map<String, AtomicInteger> countById = new ConcurrentHashMap<>();
        Messaging m1 = new Messaging(morphium, 10, false);
        m1.start();

        try {
            createIndependentMessagings(10, false, 10, false);
            for (Messaging m : messagings) {
                m.addMessageListener((ms, msg) -> {
                    ms.pauseProcessingOfMessagesNamed(msg.getName());
                    log.info("incoming message to " + ms.getSenderId());
                    Thread.sleep(250);
                    cnt.incrementAndGet();
                    countById.putIfAbsent(ms.getSenderId(), new AtomicInteger());
                    countById.get(ms.getSenderId()).incrementAndGet();
                    ms.unpauseProcessingOfMessagesNamed(msg.getName());
                    return null;
                });
            }


            for (int j = 0; j < 5; j++) {
                countById.clear();
                cnt.set(0);
                for (int i = 0; i < 10; i++) {
                    m1.sendMessage(new Msg("test", "msg", "value", 30000, true));
                }

                while (cnt.get() < 10) {
                    Thread.sleep(100);
                }
                assert (cnt.get() == 10);
                for (Map.Entry<String, AtomicInteger> e : countById.entrySet()) {
                    assert (e.getValue().get() < 8) : "Count wrong for " + e.getKey() + ": " + e.getValue();
                }
            }

        } finally {
            m1.terminate();
            terminateAll();
        }
    }


    @Test
    public void parallelExclusiveMessages() throws Exception {
        morphium.dropCollection(Msg.class, "msg", null);
        Thread.sleep(100);
        final AtomicInteger cnt = new AtomicInteger();
        final Map<String, AtomicInteger> countById = new ConcurrentHashMap<>();
        Messaging m1 = new Messaging(morphium, 10, false);
        m1.start();

        try {
            createIndependentMessagings(10, true, 10, true);
            for (Messaging m : messagings) {
                m.addListenerForMessageNamed("test", (ms, msg) -> {
                    //log.info("incoming message to "+ms.getSenderId());
                    Thread.sleep(250);
                    cnt.incrementAndGet();
                    countById.putIfAbsent(ms.getSenderId(), new AtomicInteger());
                    countById.get(ms.getSenderId()).incrementAndGet();
                    return null;
                });
            }


            for (int j = 0; j < 5; j++) {
                countById.clear();
                cnt.set(0);
                for (int i = 0; i < 30; i++) {
                    m1.sendMessage(new Msg("test", "msg", "value", 30000, true));
                }

                while (cnt.get() != 30) {
                    Thread.sleep(100);
                }
                assert (cnt.get() == 30);
                for (Map.Entry<String, AtomicInteger> e : countById.entrySet()) {
                    log.info(e.getKey() + " - processed: " + e.getValue());
                    assert (e.getValue().get() != 0);
                    if (e.getValue().get() > 4 || e.getValue().get() < 2) {
                        log.warn("---> not evenly distributed...");
                    }
                }
            }

        } finally {
            m1.terminate();
            terminateAll();
        }
    }


    @Test
    public void parallelExclusiveMessagesNoChangestream() throws Exception {
        morphium.dropCollection(Msg.class, "msg", null);
        Thread.sleep(100);
        final AtomicInteger cnt = new AtomicInteger();
        final Map<String, AtomicInteger> countById = new ConcurrentHashMap<>();
        Messaging m1 = new Messaging(morphium, 10, false);
        m1.start();

        try {
            createIndependentMessagings(10, true, 10, false);
            for (Messaging m : messagings) {
                m.addListenerForMessageNamed("test", (ms, msg) -> {
                    //log.info("incoming message to "+ms.getSenderId());
                    Thread.sleep(250);
                    cnt.incrementAndGet();
                    countById.putIfAbsent(ms.getSenderId(), new AtomicInteger());
                    countById.get(ms.getSenderId()).incrementAndGet();
                    return null;
                });
            }


            for (int j = 0; j < 5; j++) {
                countById.clear();
                cnt.set(0);
                for (int i = 0; i < 30; i++) {
                    m1.sendMessage(new Msg("test", "msg", "value", 60000, true));
                }

                while (cnt.get() != 30) {
                    Thread.sleep(100);
                }
                assert (cnt.get() == 30);
                for (Map.Entry<String, AtomicInteger> e : countById.entrySet()) {
                    log.info(e.getKey() + " - processed: " + e.getValue());
                    assert (e.getValue().get() != 0);
                }
            }

        } finally {
            m1.terminate();
            terminateAll();
        }
    }


    @Test
    public void simpleBroadcastAnswer() throws Exception {
        morphium.dropCollection(Msg.class, "msg", null);
        Thread.sleep(100);
        final AtomicInteger cnt = new AtomicInteger();
        Messaging m1 = new Messaging(morphium, 10, false);
        m1.start();

        try {
            createIndependentMessagings(10, true, 10, true);
            for (Messaging m : messagings) {
                m.addMessageListener((ms, msg) -> {
                    log.info("incoming message to " + ms.getSenderId());
                    cnt.incrementAndGet();
                    return msg.createAnswerMsg();
                });
            }

            //broadcast
            for (int i = 0; i < 10; i++) {
                List<Msg> answers = m1.sendAndAwaitAnswers(new Msg("test", "msg", "value"), 10, 1000);
                assert (cnt.get() == 10);
                assert (answers.size() == 10);
                cnt.set(0);
            }
        } finally {
            m1.terminate();
            terminateAll();
        }
    }

    @Test
    public void simpleBroadcastAnswernoChangestream() throws Exception {
        morphium.dropCollection(Msg.class, "msg", null);
        Thread.sleep(100);
        final AtomicInteger cnt = new AtomicInteger();
        Messaging m1 = new Messaging(morphium, 10, false);
        m1.start();

        try {
            createIndependentMessagings(10, true, 10, false);
            for (Messaging m : messagings) {
                m.addMessageListener((ms, msg) -> {
                    log.info("incoming message to " + ms.getSenderId());
                    cnt.incrementAndGet();
                    return msg.createAnswerMsg();
                });
            }

            //broadcast
            for (int i = 0; i < 10; i++) {
                List<Msg> answers = m1.sendAndAwaitAnswers(new Msg("test", "msg", "value"), 10, 1000);
                assert (cnt.get() == 10);
                assert (answers.size() == 10);
                cnt.set(0);
            }
        } finally {
            m1.terminate();
            terminateAll();
        }
    }
}
