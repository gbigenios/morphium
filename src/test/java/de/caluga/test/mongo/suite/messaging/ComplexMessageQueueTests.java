package de.caluga.test.mongo.suite.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import de.caluga.morphium.Utils;
import de.caluga.morphium.annotations.DefaultReadPreference;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.ReadPreferenceLevel;
import de.caluga.morphium.annotations.locking.Lockable;
import de.caluga.morphium.annotations.locking.LockedAt;
import de.caluga.morphium.annotations.locking.LockedBy;
import de.caluga.morphium.changestream.ChangeStreamEvent;
import de.caluga.morphium.changestream.ChangeStreamListener;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.MessageListener;
import de.caluga.morphium.messaging.MessageRejectedException;
import de.caluga.morphium.messaging.MessageRejectedException.RejectionHandler;
import de.caluga.morphium.messaging.Messaging;
import de.caluga.morphium.messaging.Msg;
import de.caluga.test.mongo.suite.base.MorphiumTestBase;
import de.caluga.test.mongo.suite.base.TestUtils;

public class ComplexMessageQueueTests extends MorphiumTestBase {

    @Test
    public void longRunningExclusivesWithIgnor() throws Exception {
        Messaging sender = new Messaging(morphium);
        sender.setSenderId("sender");
        sender.start();
        Messaging rec = new Messaging(morphium);
        rec.setSenderId("rec");
        rec.start();
        AtomicInteger messageCount = new AtomicInteger();
        AtomicInteger totalCount = new AtomicInteger();
        rec.addMessageListener((msg, m) -> {
            if (messageCount.get() == 0) {
                messageCount.incrementAndGet();
                var ex = new MessageRejectedException("nah.. not now", true);
                ex.setCustomRejectionHandler(new RejectionHandler() {
                    @Override
                    public void handleRejection(Messaging msg, Msg m) throws Exception {
                        m.setProcessedBy(new ArrayList<>());
                        morphium.save(m);
                    }
                });
                throw ex;
            }
            totalCount.incrementAndGet();
            messageCount.decrementAndGet();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
            return null;
        });

        for (int i = 0; i < 10; i++) {
            Msg m = new Msg("name", "msg" + i, "value");
            m.setTimingOut(false);
            m.setDeleteAfterProcessing(true);
            m.setDeleteAfterProcessingTime(0);
            sender.sendMessage(m);
        }

        TestUtils.waitForConditionToBecomeTrue(110000, "did not get all messages?", () -> totalCount.get() == 10,
            () -> log.info("Not there yet: " + totalCount.get()));
    }

    @Test
    public void releaseLockonMessage() throws Exception {
        Messaging sender = new Messaging(morphium);
        sender.setSenderId("sender");
        sender.start();
        Vector<Messaging> clients = new Vector<>();
        Vector<MorphiumId> processedMessages = new Vector<>();

        try {
            MessageListener<Msg> lst = new MessageListener<Msg>() {
                @Override
                public boolean markAsProcessedBeforeExec() {
                    return true;
                }
                @Override
                public Msg onMessage(Messaging msg, Msg m) {
                    if (processedMessages.contains(m.getMsgId())) {
                        log.error("Duplicate processing!");
                    }

                    processedMessages.add(m.getMsgId());
                    return null;
                }
            };

            for (int i = 0; i < 10; i++) {
                Messaging cl = new Messaging(morphium);
                cl.setSenderId("cl" + i);
                cl.addMessageListener(lst);
                cl.start();
                clients.add(cl);
            }

            // //releasing lock _after_ sending
            Msg m = new Msg("test", "msg", "value", 1000, true);
            m.setLockedBy("someone");
            m.setTimingOut(false);
            sender.sendMessage(m);
            Thread.sleep(1000);
            assertEquals(0, processedMessages.size());
            var q = morphium.createQueryFor(Msg.class).f("_id").eq(m.getMsgId());
            q.set("locked_by", null);
            Thread.sleep(1000);
            assertEquals(1, processedMessages.size(), "not processed");
        } finally {
            for (Messaging m : clients) {
                m.terminate();
            }

            sender.terminate();
        }
    }

    @Test
    public void rejectPauseReplayTest() throws Exception {
        Messaging sender = new Messaging(morphium);
        sender.setSenderId("sender");
        sender.start();
        Vector<Messaging> clients = new Vector<>();
        Map<String, Integer> maxParallel = Map.of("msg1", 1, "msg3", 15, "msg4", 12, "msg5", 3);
        AtomicInteger counts = new AtomicInteger(0);
        AtomicInteger locks = new AtomicInteger(0);
        AtomicInteger crons = new AtomicInteger(0);
        AtomicLong lastCron = new AtomicLong(0);
        AtomicInteger errors = new AtomicInteger(0);
        List<MorphiumId> processedIds = new Vector<>();
        List<String> lockedBy = new Vector<>();
        AtomicInteger busyClients = new AtomicInteger();
        int cronInterval = 10000;
        GlobalLock gl = new GlobalLock();
        gl.scope = "global";
        morphium.insert(gl);

        try {
            var msgL = new MessageListener<Msg>() {
                @Override
                public boolean markAsProcessedBeforeExec() {
                    return true;
                }
                @Override
                public Msg onMessage(Messaging msg, Msg m) {
                    try {
                        // log.info(msg.getSenderId() + ": OnMessage " + m.getName());
                        if (maxParallel.containsKey(m.getName()) && maxParallel.get(m.getName()) != null) {
                            var cnt = morphium.createQueryFor(Msg.class).f("name").eq(m.getName()).f("locked_by")
                                .ne("Planner").countAll();

                            if (cnt > maxParallel.get(m.getName()) + 1) { // has to be bigger, as the entry is already
                                // there
                                // for _this_ execution!
                                // log.error(String.format("MaxParallel for %s exceeded in ONMESSAGE! -
                                // pausing!", m.getName()));
                                msg.pauseProcessingOfMessagesNamed(m.getName());
                                var ex = new MessageRejectedException("MaxParallel exceeded", true);
                                ex.setCustomRejectionHandler((messaging, message) -> {
                                    message.setLockedBy("Planner");
                                    message.setLocked(0);
                                    message.setProcessedBy(new ArrayList<>());
                                    // morphium.updateUsingFields(message,"locked_by","processed_by");
                                    morphium.store(message);
                                });
                                throw ex;
                            }
                        }

                        busyClients.incrementAndGet();

                        // no maxParallel - can do our magic
                        if (processedIds.contains(m.getMsgId())) {
                            log.error("Duplicate Message processing for ID " + m.getMsgId());
                            errors.incrementAndGet();
                        }

                        processedIds.add(m.getMsgId());
                        // simulate to process message
                        counts.incrementAndGet();
                        Thread.sleep(((int)(Math.random() * 500.0) + 1500));
                        // reset message count
                        busyClients.decrementAndGet();
                        // log.info("Deleted: " + Utils.toJsonString(ret));
                    } catch (InterruptedException e) {
                        log.info("Onmessage aborting");
                        // e.printStackTrace();
                    } finally {
                        // log.info(msg.getSenderId() + " onmessage finished");
                    }

                    // log.info(msg.getSenderId() + ": onMessage finished - " + m.getName());
                    return null;
                }
            };
            final int noOfClients = 25;
            GlobalLock l = morphium.createQueryFor(GlobalLock.class).f("_id").eq("global").get();

            for (int i = 0; i < noOfClients; i++) {
                log.info("Creating client " + i);
                Messaging rec1 = new Messaging(morphium, 100, true, true, 1);
                rec1.setSenderId("rec" + i);
                rec1.addListenerForMessageNamed("msg1", msgL);
                rec1.addListenerForMessageNamed("msg2", msgL);
                rec1.addListenerForMessageNamed("msg3", msgL);
                rec1.addListenerForMessageNamed("msg4", msgL);
                rec1.addListenerForMessageNamed("msg5", msgL);
                rec1.start();
                // ____
                // / ___|_ __ ___ _ __
                // | | | '__/ _ \| '_ \
                // | |___| | | (_) | | | |
                // \____|_| \___/|_| |_|
                new Thread(() -> {
                    while (!rec1.isRunning()) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                        }
                    }

                    while (rec1.isRunning()) {
                        if (System.currentTimeMillis() - lastCron.get() > cronInterval) {
                            // getting lock
                            // log.info("Should trigger Cron...");
                            rec1.triggerCheck();

                            try {
                                GlobalLock lock = morphium.lockEntity(l, rec1.getSenderId() + "_cron", 5000);

                                if (lock != null && (lock.lockedBy == null
                                        || !lock.lockedBy.equals(rec1.getSenderId() + "_cron"))) {
                                    log.error("HELL YEAH - lock failed on CRON!");
                                    errors.incrementAndGet();
                                    return;
                                }

                                if (lock != null && (rec1.getSenderId() + "_cron").equals(lock.lockedBy)) {
                                    locks.incrementAndGet();

                                    if (locks.get() > 1) {
                                        log.error(rec1.getSenderId() + ": Too many locks!!!!! " + locks.get());
                                        errors.incrementAndGet();
                                    }

                                    // log.info("Cron interval triggered...");
                                    Msg m = new Msg("msg2", "cron", "value");
                                    m.setDeleteAfterProcessing(true);
                                    m.setDeleteAfterProcessingTime(0);
                                    m.setLockedBy("Planner");
                                    m.setTimingOut(false);
                                    m.setLocked(System.currentTimeMillis());
                                    sender.sendMessage(m); // need to have a different sender ID than myself - or I
                                    // won't be able to run this task
                                    crons.incrementAndGet();
                                    lastCron.set(System.currentTimeMillis());
                                    locks.decrementAndGet();
                                    morphium.releaseLock(lock);
                                }
                            } catch (Exception e) {
                                log.error("Something wrong in cron thread", e);
                                errors.incrementAndGet();
                            }
                        }

                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                        }
                    }
                }).start();
                ////////////////////////////////////////////////////////////////////////////////
                ///////////////////////////////
                // ____ _
                // | _ \| | __ _ _ __ _ __ ___ _ __
                // | |_) | |/ _` | '_ \| '_ \ / _ \ '__|
                // | __/ | | (_| | | | | | | | __/ |
                // |_| |_|\__,_|_| |_|_| |_|\___|_|
                Runnable plan = () -> {
                    var running = morphium.createQueryFor(Msg.class).f("locked_by").nin(Arrays.asList("Planner","ALL"))
                    .countAll();

                    if (noOfClients <= running) {
                        return;
                    }

                    long start = System.currentTimeMillis();

                    try {
                        var lock = morphium.lockEntity(l, rec1.getSenderId(), 5000);

                        if (lock != null && (lock.lockedBy == null || !lock.lockedBy.equals(rec1.getSenderId()))) {
                            log.error("HELL IS LOOSE - lock does not work!");
                            errors.incrementAndGet();
                            return;
                        }

                        if (lock != null && rec1.getSenderId().equals(lock.lockedBy)) {
                            if (locks.get() > 1) {
                                log.error(rec1.getSenderId() + ": Too many locks!!!!! " + locks.get());
                                errors.incrementAndGet();
                            }

                            // plan ahead
                            try {
                                // log.info(rec1.getSenderId() + " Planner: Got lock!");
                                locks.incrementAndGet();
                                var agg = morphium.createAggregator(Msg.class, Map.class)
                                    .match(morphium.createQueryFor(Msg.class).f("locked_by").nin(Arrays.asList("Planner","ALL")))
                                    .group("$name").sum("count", 1).end().aggregateMap();

                                for (var e : agg) {
                                    if (maxParallel.containsKey(e.get("_id"))) {
                                        if (rec1.getPausedMessageNames().contains(e.get("_id"))
                                            && ((Integer) e.get("count")) < maxParallel.get(e.get("_id"))) {
                                            rec1.unpauseProcessingOfMessagesNamed(e.get("_id").toString());
                                        } else if (maxParallel.get(e.get("_id")) >= (Integer) e.get("count")) {
                                            rec1.pauseProcessingOfMessagesNamed(e.get("_id").toString());
                                        }
                                    }
                                }

                                // for (String n : rec1.getPausedMessageNames()) {
                                // // check for pauses
                                // if (maxParallel.containsKey(n)) {
                                // if (morphium.createQueryFor(Msg.class).f("name").eq(n).f("locked_by")
                                // .ne("Planner").countAll() < maxParallel.get(n)) {
                                // rec1.unpauseProcessingOfMessagesNamed(n);
                                // }
                                // } else {
                                // // log.info(rec1.getSenderId() + " -> paused " + n);
                                // // why?
                                // rec1.unpauseProcessingOfMessagesNamed(n);
                                // }
                                // }
                                // log.info(String.format("Planner: NoOfClients %d - running %d", noOfClients,
                                // running));
                                var msgQuery = morphium.createQueryFor(Msg.class).f("locked_by").eq("Planner")
                                    .f("name").nin(rec1.getPausedMessageNames())
                                    .sort(Msg.Fields.priority, Msg.Fields.timestamp)
                                    .addProjection("_id").addProjection("name")
                                    .limit((int)(noOfClients - running));
                                var lst = msgQuery.asList();
                                log.info(String.format("%s: Planner: Got %s candidates", rec1.getSenderId(),
                                        lst.size()));

                                if (lst.size() != 0) {
                                    // log.info(String.format("Planner: Processing %d messages", lst.size()));
                                    // for (String pn : rec1.getPausedMessageNames()) {
                                    // log.info(" " + pn);
                                    // }
                                    int added = 0;

                                    do {
                                        for (var m : lst) {
                                            if (m.getLockedBy().equals("Planner")) {
                                                if (maxParallel.containsKey(m.getName())) {
                                                    var cnt = morphium.createQueryFor(Msg.class).f("name")
                                                        .eq(m.getName()).f("locked_by").nin(Arrays.asList("Planner","ALL")).countAll();

                                                    if (cnt >= (Integer) maxParallel.get(m.getName())) {
                                                        rec1.pauseProcessingOfMessagesNamed(m.getName());
                                                        continue;
                                                    }
                                                }

                                                added++;
                                                m.setLockedBy(null);

                                                try {
                                                    morphium.updateUsingFields(m, "locked_by");
                                                } catch (Exception e) {
                                                    log.error("Planner: update failed!", e);
                                                    m = morphium.reread(m);
                                                    log.error("Planner: "
                                                        + Utils.toJsonString(morphium.getMapper().serialize(m)));
                                                    errors.incrementAndGet();
                                                }
                                            }
                                        }

                                        if (added < (noOfClients - running)) {
                                            msgQuery = morphium.createQueryFor(Msg.class).f("locked_by").eq("Planner")
                                                .f("name").nin(rec1.getPausedMessageNames())
                                                .sort(Msg.Fields.priority, Msg.Fields.timestamp)
                                                .addProjection("_id").addProjection("name")
                                                .limit((int)(noOfClients - running - added));
                                            lst = msgQuery.asList();

                                            if (lst.isEmpty()) {
                                                break;
                                            }
                                        }
                                    } while (added < (noOfClients - running));

                                    // log.info(String.format("Planner added %d entries (Running: %d - available
                                    // %d)!", added, running, noOfClients));
                                }
                            } finally {
                                // log.info("Planner: release lock");
                                lockedBy.remove(rec1.getSenderId());
                                locks.decrementAndGet();
                                morphium.releaseLock(lock);
                                log.info(
                                    String.format("Whole planning took %d ms", System.currentTimeMillis() - start));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                };
                new Thread() {
                    public void run() {
                        morphium.watch(Msg.class, true, new ChangeStreamListener() {
                            @Override
                            public boolean incomingData(ChangeStreamEvent evt) {
                                if (evt.getOperationType().equals("insert")) {
                                    // log.info("new Message!");
                                    Msg obj = morphium.getMapper().deserialize(Msg.class, evt.getFullDocument());

                                    if (obj.getLockedBy() != null && obj.getLockedBy().equals("Planner")) {
                                        if (rec1.getPausedMessageNames().contains(obj.getName())) {
                                            return rec1.isRunning();
                                        }

                                        plan.run();
                                    }
                                } else if (evt.getOperationType().equals("delete")) {
                                    // log.info(rec1.getSenderId()+": incoming delete");
                                    for (String n : rec1.getPausedMessageNames()) {
                                        if (maxParallel.containsKey(n)) {
                                            if (morphium.createQueryFor(Msg.class).f("name").eq(n).f("locked_by")
                                                .nin(Arrays.asList("Planner","ALL")).countAll() < maxParallel.get(n)) {
                                                long dur = rec1.unpauseProcessingOfMessagesNamed(n);
                                                // log.info(n + " was paused for " + dur + "ms");
                                                if (dur>5000){
                                                    log.warn(n+" was paused too long: "+dur);
                                                }
                                            }
                                        } else {
                                            rec1.unpauseProcessingOfMessagesNamed(n);
                                        }
                                    }

                                    rec1.triggerCheck();
                                    plan.run(); // plan ahead - there is a free slot!
                                }

                                return rec1.isRunning();
                            }
                        });
                    }
                }
                .start();
                clients.add(rec1);
            }

            // sending some messages
            int amount = 585;
            new Thread(() -> {
                for (int i = 0; i < amount; i++) {
                    var mod = (i % 5) + 1;

                    if (mod == 1) {
                        // too much of them
                        if (Math.random() > 0.25) {
                            mod = 2; // no limit
                        }
                    }

                    if (mod == 5) {
                        if (Math.random() > 0.5) {
                            mod = 2;
                        }
                    }

                    Msg m = new Msg("msg" + mod, "msg", "value");
                    m.setDeleteAfterProcessing(true);
                    m.setDeleteAfterProcessingTime(0);
                    m.setLockedBy("Planner");
                    m.setTimingOut(false);
                    m.setLocked(System.currentTimeMillis());
                    assertTrue(m.isExclusive());
                    sender.sendMessage(m);
                    // log.info("Inserted " + m.getMsgId());
                }
            }).start();
            long start = System.currentTimeMillis();
            int maxRunning = 0;
            int maxPlanned = 0;

            while (!(counts.get() >= (amount + crons.get()))) {
                if (System.currentTimeMillis() - start > 1000) {
                    Msg m=new Msg("morphium.status_info", "status", "value", 4000);
                    var answers=sender.sendAndAwaitAnswers(m, 25, 4000,false);
                    log.info("Got answers "+answers.size());
                    start = System.currentTimeMillis();
                    log.info(
                        "---------------------------------------------------------------------------------------------");
                    log.info("---------------->>>>>>> Waiting for " + counts.get() + " to reach "
                        + (amount + crons.get()));
                    log.info("  Active clients:" + busyClients.get() + " Max: " + maxRunning);
                    log.info("  Message Queue: " + morphium.createQueryFor(Msg.class).countAll());
                    log.info("  max scheduled: " + maxPlanned);
                    log.info("  processing   : "
                        + morphium.createQueryFor(Msg.class).f("locked_by").nin(Arrays.asList("Planner","ALL")).countAll());
                    var agg = morphium.createAggregator(Msg.class, Map.class)
                        .match(morphium.createQueryFor(Msg.class).f("locked_by").nin(Arrays.asList("Planner","ALL"))).group("$name")
                        .sum("count", 1).end().aggregateMap();

                    for (var e : agg) {
                        if (maxParallel.get(e.get("_id")) != null) {
                            Integer maxp = (Integer) maxParallel.get(e.get("_id"));
                            Integer current = (Integer) e.get("count");

                            if (current > maxp) {
                                log.error(" ERROR --->     " + e.get("_id") + " = " + e.get("count") + "  max: "
                                    + maxParallel.get(e.get("_id")));
                                errors.incrementAndGet();
                            } else {
                                log.info("     " + e.get("_id") + " = " + e.get("count") + "  max: " + maxp);
                            }
                        }
                    }

                    log.info(String.format("Messages planned: %d - processed: %d - cronjobs: %d", amount, counts.get(),
                            crons.get()));

                    if (errors.get() > 0) {
                        log.error("Errors occured: " + errors.get());
                        break;
                    }

                    log.info("------------------------------");
                    start=System.currentTimeMillis();
                }

                if (busyClients.get() > maxRunning) {
                    maxRunning = busyClients.get();
                }

                var scheduled = (int) morphium.createQueryFor(Msg.class).f("locked_by").ne("Planner").countAll();

                if (scheduled > maxPlanned) {
                    maxPlanned = scheduled;
                }

                Thread.sleep(10);
            }
        } finally {
            for (Messaging m : clients) {
                m.terminate();
            }

            sender.terminate();
        }
    }

    @Lockable
    @Entity
    @DefaultReadPreference(ReadPreferenceLevel.PRIMARY)
    public static class GlobalLock {
        @Id
        public String scope;
        @LockedBy
        public String lockedBy;
        @LockedAt
        public long lockedAt;
    }

}
