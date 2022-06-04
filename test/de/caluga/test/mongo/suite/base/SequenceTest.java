package de.caluga.test.mongo.suite.base;

import de.caluga.morphium.*;
import de.caluga.morphium.driver.MorphiumId;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: Stephan Bösebeck
 * Date: 25.07.12
 * Time: 08:09
 * <p/>
 */
public class SequenceTest extends MorphiumTestBase {
    @Test
    public void singleSequenceTest() {
        morphium.dropCollection(Sequence.class);
        SequenceGenerator sg = new SequenceGenerator(morphium, "tstseq", 1, 1);
        long v = sg.getNextValue();
        assert (v == 1) : "Value wrong: " + v;
        v = sg.getNextValue();
        assert (v == 2);
    }

    @Test
    public void multiSequenceTest() {
        morphium.dropCollection(Sequence.class);
        SequenceGenerator sg1 = new SequenceGenerator(morphium, "tstseq1", 1, 1);
        SequenceGenerator sg2 = new SequenceGenerator(morphium, "tstseq2", 1, 1);
        long v = sg1.getNextValue();
        assert (v == 1) : "Value wrong: " + v;
        v = sg2.getNextValue();
        v = sg2.getNextValue();
        assert (v == 2) : "Value wrong: " + v;

        v = sg1.getNextValue();
        assert (v == 2);
        v = sg2.getNextValue();
        v = sg2.getNextValue();
        assert (v == 4);
    }

    @Test
    public void errorLockedSequenceTest() {
        morphium.dropCollection(Sequence.class);
        SequenceGenerator sg = new SequenceGenerator(morphium, "test", 1, 1);
        sg.getNextValue(); //initializing

        Sequence s = morphium.createQueryFor(Sequence.class).f(Sequence.Fields.name).eq("test").get();
        s.setLockedBy("noone");
        morphium.store(s);
        waitForWrites();
        //now sequence is blocked by someone else... waiting 30s
        long v = sg.getNextValue();
        log.info("Got next Value: " + v);
        assert (v == 2);


    }

    @Test
    public void migrationTest() throws Exception {

        Map<String, Object> seq = UtilsMap.of("_id", new MorphiumId());
        seq.put("name", "testSeq");
        seq.put("locked_at", 0);
        seq.put("current_value", 100);
//        morphium.getDriver().store(morphium.getConfig().getDatabase(), "sequence", Arrays.asList(seq), null);
//        Thread.sleep(100);

        SequenceGenerator gen = new SequenceGenerator(morphium, "testSeq");
        log.info("Current value: " + gen.getCurrentValue());
        assert (gen.getCurrentValue() == 100);
        assert (gen.getNextValue() > 100);

    }

    @Test
    public void massiveMultiSequenceTest() {
        morphium.dropCollection(Sequence.class);
        Vector<SequenceGenerator> gens = new Vector<>();
        //creating lots of sequences
        for (int i = 0; i < 10; i++) {
            SequenceGenerator sg1 = new SequenceGenerator(morphium, "tstseq_" + i, i % 3 + 1, i);
            gens.add(sg1);
        }

        log.info("getting values...");
        for (int i = 0; i < 200; i++) {
            log.info("" + i + "/200");
            int r = (int) (Math.random() * gens.size());
            SequenceGenerator g = gens.get(r);
            long v = g.getCurrentValue();
            long v2 = g.getNextValue();
            assert (v2 == v + g.getInc()) : "incremented wrong?";
        }
        log.info("done");
    }

    @Test
    public void massiveParallelSingleSequenceTest() throws Exception {
        morphium.dropCollection(Sequence.class);
        final SequenceGenerator sg1 = new SequenceGenerator(morphium, "tstseq", 1, 0);
        Vector<Thread> thr = new Vector<>();
        final Vector<Long> data = new Vector<>();
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(() -> {
                for (int i1 = 0; i1 < 25; i1++) {
                    long nv = sg1.getNextValue();
                    assert (!data.contains(nv)) : "Value already stored? Value: " + nv;
                    data.add(nv);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                    }
                }
            });
            t.start();
            thr.add(t);
        }
        log.info("Waiting for threads to finish");
        for (Thread t : thr) {
            t.join();
        }
        long last = -1;
        Collections.sort(data);
        for (Long l : data) {
            assert (last == l - 1);
            last = l;
        }
        log.info("done");
    }

    @Test
    public void massiveParallelMultiSequenceTest() throws Exception {
        morphium.dropCollection(Sequence.class);
        Vector<SequenceGenerator> gens = new Vector<>();
        //creating lots of sequences
        for (int i = 0; i < 10; i++) {
            SequenceGenerator sg1 = new SequenceGenerator(morphium, "tstseq_" + i, i % 3 + 1, i);
            gens.add(sg1);
        }

        Vector<Thread> thr = new Vector<>();
        for (final SequenceGenerator g : gens) {
            Thread t = new Thread(() -> {
                double max = Math.random() * 10;
                for (int i = 0; i < max; i++) {
                    long cv = g.getCurrentValue();
                    long nv = g.getNextValue();
                    assert (nv == cv + g.getInc());
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                    }
                }
            });
            thr.add(t);
            log.info("started thread for seqence " + g.getName());
            t.start();
        }

        //joining threads
        log.info("Waiting for threads to finish");
        for (Thread t : thr) {
            t.join();
        }
        log.info("done!");
    }

    @Test
    public void massiveParallelMulticonnectSingleSequenceTest() throws Exception {
        morphium.dropCollection(Sequence.class);
        Thread.sleep(100); //wait for the drop to be persisted


        //creating lots of sequences, with separate MongoDBConnections
        //reading from the same sequence
        //in different Threads
        final Vector<Long> values = new Vector<>();
        List<Thread> threads = new ArrayList<>();
        final AtomicInteger errors = new AtomicInteger(0);
        for (int i = 0; i < 50; i++) {
            Morphium m = new Morphium(MorphiumConfig.fromProperties(morphium.getConfig().asProperties()));

            Thread t = new Thread(() -> {
                SequenceGenerator sg1 = new SequenceGenerator(m, "testsequence", 1, 0);
                for (int j = 0; j < 50; j++) {
                    try {
                        long l = sg1.getNextValue();
                        if (l % 100 == 0)
                            log.info("Got nextValue: " + l);
                        if (values.contains(l)) {
                            log.error("Duplicate value " + l);
                            errors.incrementAndGet();
                        } else {
                            values.add(l);
                        }
                    } catch (Exception e) {
                        log.error("Got Exception... pausing");
                        errors.incrementAndGet();
                        try {
                            Thread.sleep((long) (250 * Math.random()));
                        } catch (InterruptedException interruptedException) {
                        }

                    }

                }
                m.close();
            });
            threads.add(t);
            t.start();

        }

        while (threads.size() > 0) {
            //log.info("Threads active: "+threads.size());
            threads.remove(0).join();
        }

        assert (errors.get() == 0);
        assert (values.size() == 2500);
        //checking that no value was skipped
        for (int i = 0; i < values.size(); i++) {
            assert (values.get(i) == i);
        }

    }
}
