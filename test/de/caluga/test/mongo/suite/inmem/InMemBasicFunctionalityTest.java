/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.caluga.test.mongo.suite.inmem;

import de.caluga.morphium.AnnotationAndReflectionHelper;
import de.caluga.morphium.StatisticKeys;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.driver.inmem.InMemoryDriver;
import de.caluga.morphium.query.MorphiumIterator;
import de.caluga.morphium.query.Query;
import de.caluga.test.mongo.suite.data.CachedObject;
import de.caluga.test.mongo.suite.data.ComplexObject;
import de.caluga.test.mongo.suite.data.EmbeddedObject;
import de.caluga.test.mongo.suite.data.UncachedObject;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author stephan
 */
@SuppressWarnings({"AssertWithSideEffects", "MismatchedQueryAndUpdateOfCollection"})
public class InMemBasicFunctionalityTest extends MorphiumInMemTestBase {
    public static final int NO_OBJECTS = 100;
    private static final Logger log = LoggerFactory.getLogger(InMemBasicFunctionalityTest.class);
    private int runningThreads;

    public InMemBasicFunctionalityTest() {

    }

    @Test
    public void whereTest() {
        createUncachedObjects(NO_OBJECTS);
        Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class);
        q.where("this.counter<10").f(UncachedObject.Fields.counter).gt(5);
        log.info(q.toQueryObject().toString());

        List<UncachedObject> lst = q.asList();
        for (UncachedObject o : lst) {
            assert (o.getCounter() < 10 && o.getCounter() > 5) : "Counter is wrong: " + o.getCounter();
        }

        assert (morphium.getStatistics().get("X-Entries for: idCache|de.caluga.test.mongo.suite.data.UncachedObject") == null) : "Cached Uncached Object?!?!?!";


    }

    @Test
    public void subObjectQueryTest() {
        Query<ComplexObject> q = morphium.createQueryFor(ComplexObject.class);

        q = q.f("embed.testValueLong").eq(null).f("entityEmbeded.binaryData").eq(null);
        String queryString = q.toQueryObject().toString();
        log.info(queryString);
        assert (queryString.contains("embed.test_value_long") && queryString.contains("entityEmbeded.binary_data"));
        q = q.f("embed.test_value_long").eq(null).f("entity_embeded.binary_data").eq(null);
        queryString = q.toQueryObject().toString();
        log.info(queryString);
        assert (queryString.contains("embed.test_value_long") && queryString.contains("entityEmbeded.binary_data"));
    }

    public void subObjectQueryTestUnknownField() {
        Query<ComplexObject> q = morphium.createQueryFor(ComplexObject.class);

        q = q.f("embed.testValueLong").eq(null).f("entityEmbeded.binaryData.non_existent").eq(null);
        String queryString = q.toQueryObject().toString();
        log.info(queryString);
    }

    @Test
    public void getDatabaseListTest() {
        UncachedObject o = new UncachedObject("value", 10);
        morphium.store(o);
        List<String> dbs = morphium.listDatabases();
        assert (dbs != null);
        assert (dbs.size() != 0);
        for (String s : dbs) {
            log.info("Got DB: " + s);
        }
    }

    @Test
    public void exprQueryTest() {
//        createUncachedObjects(100);
//        Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class).expr(Expr.eq(Expr.field(UncachedObject.Fields.counter, UncachedObject.class, morphium), Expr.intExpr(42)));
//        List<UncachedObject> lst = q.asList();
//        assert (lst.size() == 1);
    }

    @Test
    public void listCollections() {
        UncachedObject u = new UncachedObject("test", 1);
        morphium.store(u);

        List<String> cols = morphium.listCollections();
        assert (cols != null);
    }

    @Test
    public void sortTest() throws Exception {
        for (int i = 1; i <= NO_OBJECTS; i++) {
            UncachedObject o = new UncachedObject();
            o.setCounter(i);
            o.setStrValue("Uncached " + (i % 2));
            morphium.store(o);
            log.info("Stored c: " + o.getCounter() + " v: " + o.getStrValue());
        }
        Thread.sleep(600);
        Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class);
        q = q.f(UncachedObject.Fields.counter).gt(0).sort("strValue", "-counter");
        List<UncachedObject> lst = q.asList();
//        lst.sort(((o1, o2) -> {
//            if (o1.getCounter()==o2.getCounter()){
//                return o1.getValue().compareTo(o2.getValue());
//            }
//            return o1.getCounter()<o2.getCounter()?1:-1;
//
//        }));
        int last = 9999;
        String lastV = "";
        for (UncachedObject uc : lst) {
            log.info("Counter " + uc.getCounter() + " Value: " + uc.getStrValue());
            if (!lastV.equals(uc.getStrValue())) {
                assert (lastV.compareTo(uc.getStrValue()) <= 0 || lastV.equals(""));
                lastV = uc.getStrValue();
                last = 99999;
            }
            assert (last > uc.getCounter());
            last = uc.getCounter();
        }

        q = q.q().f(UncachedObject.Fields.counter).gt(0).sort("counter", "-str_value");
        List<UncachedObject> lst2 = q.asList();
        last = 0;

        for (UncachedObject uc : lst2) {
            log.info("Counter " + uc.getCounter());
            assert (last <= uc.getCounter());
            last = uc.getCounter();
        }
        log.info("Sorted");

        q = morphium.createQueryFor(UncachedObject.class);
        q = q.f(UncachedObject.Fields.counter).gt(0).limit(5).sort("-counter");
        int st = q.asList().size();
        q = morphium.createQueryFor(UncachedObject.class);
        q = q.f(UncachedObject.Fields.counter).gt(0).sort("-counter").limit(5);
        assert (st == q.asList().size()) : "List length differ?";

    }

    @Test
    public void arrayOfPrimitivesTest() {
        UncachedObject o = new UncachedObject();
        int[] binaryData = new int[100];
        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = i;
        }
        o.setIntData(binaryData);

        long[] longData = new long[100];
        for (int i = 0; i < longData.length; i++) {
            longData[i] = i;
        }
        o.setLongData(longData);

        float[] floatData = new float[100];
        for (int i = 0; i < floatData.length; i++) {
            floatData[i] = (float) (i / 100.0);
        }
        o.setFloatData(floatData);

        double[] doubleData = new double[100];
        for (int i = 0; i < doubleData.length; i++) {
            doubleData[i] = (float) (i / 100.0);
        }
        o.setDoubleData(doubleData);

        boolean[] bd = new boolean[100];
        for (int i = 0; i < bd.length; i++) {
            bd[i] = i % 2 == 0;
        }
        o.setBoolData(bd);

        morphium.store(o);


        morphium.reread(o);

        for (int i = 0; i < o.getIntData().length; i++) {
            assert (o.getIntData()[i] == binaryData[i]);
        }

        for (int i = 0; i < o.getLongData().length; i++) {
            assert (o.getLongData()[i] == longData[i]);
        }
        for (int i = 0; i < o.getFloatData().length; i++) {
            assert (o.getFloatData()[i] == floatData[i]);
        }

        for (int i = 0; i < o.getDoubleData().length; i++) {
            assert (o.getDoubleData()[i] == doubleData[i]);
        }

        for (int i = 0; i < o.getBoolData().length; i++) {
            assert (o.getBoolData()[i] == bd[i]);
        }


    }

    @Test
    public void updateBinaryDataTest() {
        UncachedObject o = new UncachedObject();
        byte[] binaryData = new byte[100];
        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = (byte) i;
        }
        o.setBinaryData(binaryData);
        morphium.store(o);


        waitForWriteBufferToFlush(3000);
        waitForWrites();
        morphium.reread(o);
        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = (byte) (i + 2);
        }
        o.setBinaryData(binaryData);
        morphium.store(o);
        waitForWriteBufferToFlush(3000);
        waitForWrites();

        for (int i = 0; i < o.getBinaryData().length; i++) {
            assert (o.getBinaryData()[i] == binaryData[i]);
        }
    }

    @Test
    public void binaryDataTest() {
        UncachedObject o = new UncachedObject();
        byte[] binaryData = new byte[100];
        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = (byte) i;
        }
        o.setBinaryData(binaryData);
        morphium.store(o);

        waitForWriteBufferToFlush(3000);
        waitForWrites();
        morphium.reread(o);
        for (int i = 0; i < o.getBinaryData().length; i++) {
            assert (o.getBinaryData()[i] == binaryData[i]);
        }
    }


// unfortunately not working in memory yet
//    @Test
//    public void whereTest() {
//        for (int i = 1; i <= NO_OBJECTS; i++) {
//            UncachedObject o = new UncachedObject();
//            o.setCounter(i);
//            o.setValue("Uncached " + i);
//            morphium.store(o);
//        }
//        Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class);
//        q.where("this.counter<10").f(UncachedObject.Fields.counter).gt(5);
//        log.info(q.toQueryObject().toString());
//
//        List<UncachedObject> lst = q.asList();
//        for (UncachedObject o : lst) {
//            assert (o.getCounter() < 10 && o.getCounter() > 5) : "Counter is wrong: " + o.getCounter();
//        }
//
//        assert (morphium.getStatistics().get("X-Entries for: de.caluga.test.mongo.suite.data.UncachedObject") == null) : "Cached Uncached Object?!?!?!";
//
//
//    }

    @Test
    public void existsTest() throws Exception {
        for (int i = 1; i <= 10; i++) {
            UncachedObject o = new UncachedObject();
            o.setCounter(i);
            o.setStrValue("Uncached " + i);
            morphium.store(o);
        }
        Thread.sleep(1000);
        Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class);
        q = q.f(UncachedObject.Fields.counter).exists().f(UncachedObject.Fields.strValue).eq("Uncached 1");
        long c = q.countAll();
        assert (c == 1) : "Count wrong: " + c;

        UncachedObject o = q.get();
        assert (o.getCounter() == 1);
    }

    @Test
    public void notExistsTest() {
        for (int i = 1; i <= 10; i++) {
            UncachedObject o = new UncachedObject();
            o.setCounter(i);
            o.setStrValue("Uncached " + i);
            morphium.store(o);
        }
        Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class);
        q = q.f(UncachedObject.Fields.counter).notExists().f(UncachedObject.Fields.strValue).eq("Uncached 1");
        long c = q.countAll();
        assert (c == 0);
    }


    @Test
    public void idTest() throws Exception {
        log.info("Storing Uncached objects...");

        long start = System.currentTimeMillis();

        UncachedObject last = null;
        for (int i = 1; i <= NO_OBJECTS; i++) {
            UncachedObject o = new UncachedObject();
            o.setCounter(i);
            o.setStrValue("Uncached " + i);
            morphium.store(o);
            last = o;
        }

        assert (last.getMorphiumId() != null) : "ID null?!?!?";
        Thread.sleep(1000);
        UncachedObject uc = morphium.findById(UncachedObject.class, last.getMorphiumId());
        assert (uc != null) : "Not found?!?";
        assert (uc.getCounter() == last.getCounter()) : "Different Object? " + uc.getCounter() + " != " + last.getCounter();

    }

    //    @Test
    //    public void currentOpTest() throws Exception{
    //        new Thread() {
    //            public void run() {
    //                createUncachedObjects(1000);
    //            }
    //        }.start();
    //        List<Map<String, Object>> lst = morphium.getDriver().find("local", "$cmd.sys.inprog", new HashMap<String, Object>(), null, null, 0, 1000, 1000, null, null);
    //        log.info("got: "+lst.size());
    //    }

    @Test
    public void orTest() {
        log.info("Storing Uncached objects...");

        long start = System.currentTimeMillis();

        for (int i = 1; i <= NO_OBJECTS; i++) {
            UncachedObject o = new UncachedObject();
            o.setCounter(i);
            o.setStrValue("Uncached " + i);
            morphium.store(o);
        }

        Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class);
        q.or(q.q().f(UncachedObject.Fields.counter).lt(10), q.q().f(UncachedObject.Fields.strValue).eq("Uncached 50"));
        log.info("Query string: " + q.toQueryObject().toString());
        List<UncachedObject> lst = q.asList();
        for (UncachedObject o : lst) {
            assert (o.getCounter() < 10 || o.getStrValue().equals("Uncached 50")) : "Value did not match: " + o;
            log.info(o.toString());
        }
        log.info("1st test passed");
        for (int i = 1; i < 120; i++) {
            //Storing some additional test content:
            UncachedObject uc = new UncachedObject();
            uc.setCounter(i);
            uc.setStrValue("Complex Query Test " + i);
            morphium.store(uc);
        }

        assert (morphium.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.UncachedObject") == null) : "Cached Uncached Object?!?!?!";

    }


    @Test
    public void uncachedSingeTest() throws Exception {
        log.info("Storing Uncached objects...");

        long start = System.currentTimeMillis();

        for (int i = 1; i <= NO_OBJECTS; i++) {
            UncachedObject o = new UncachedObject();
            o.setCounter(i);
            o.setStrValue("Uncached " + i);
            morphium.store(o);
        }
        long dur = System.currentTimeMillis() - start;
        log.info("Storing single took " + dur + " ms");
        //        assert (dur < NO_OBJECTS * 5) : "Storing took way too long";
        Thread.sleep(1500);
        log.info("Searching for objects");

        checkUncached();
        assert (morphium.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.UncachedObject") == null) : "Cached Uncached Object?!?!?!";

    }

    @Test
    public void testAnnotationCache() {
        Entity e = morphium.getARHelper().getAnnotationFromHierarchy(EmbeddedObject.class, Entity.class);
        assert (e == null);
        Embedded em = morphium.getARHelper().getAnnotationFromHierarchy(EmbeddedObject.class, Embedded.class);
        assert (em != null);
    }

    @Test
    public void uncachedListTest() throws Exception {
        morphium.clearCollection(UncachedObject.class);
        log.info("Preparing a list...");

        long start = System.currentTimeMillis();
        List<UncachedObject> lst = new ArrayList<>();
        for (int i = 1; i <= NO_OBJECTS; i++) {
            UncachedObject o = new UncachedObject();
            o.setCounter(i);
            o.setStrValue("Uncached " + i);
            lst.add(o);
        }
        morphium.storeList(lst);
        long dur = System.currentTimeMillis() - start;
        log.info("Storing a list  took " + dur + " ms");
        Thread.sleep(1000);
        checkUncached();
        assert (morphium.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.UncachedObject") == null) : "Cached Uncached Object?!?!?!";

    }

    private void checkUncached() {
        long start;
        long dur;
        start = System.currentTimeMillis();
        for (int i = 1; i <= NO_OBJECTS; i++) {
            Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class);
            q.f(UncachedObject.Fields.counter).eq(i);
            List<UncachedObject> l = q.asList();
            assert (l != null && !l.isEmpty()) : "Nothing found!?!?!?!? Value: " + i;
            UncachedObject fnd = l.get(0);
            assert (fnd != null) : "Error finding element with id " + i;
            assert (fnd.getCounter() == i) : "Counter not equal: " + fnd.getCounter() + " vs. " + i;
            assert (fnd.getStrValue().equals("Uncached " + i)) : "value not equal: " + fnd.getCounter() + "(" + fnd.getStrValue() + ") vs. " + i;
        }
        dur = System.currentTimeMillis() - start;
        log.info("Searching  took " + dur + " ms");
    }

    private void randomCheck() {
        log.info("Random access to cached objects");
        long start;
        long dur;
        start = System.currentTimeMillis();
        for (int idx = 1; idx <= NO_OBJECTS * 3.5; idx++) {
            int i = (int) (Math.random() * (double) NO_OBJECTS);
            if (i == 0) {
                i = 1;
            }
            Query<CachedObject> q = morphium.createQueryFor(CachedObject.class);
            q.f(UncachedObject.Fields.counter).eq(i);
            List<CachedObject> l = q.asList();
            assert (l != null && !l.isEmpty()) : "Nothing found!?!?!?!? " + i;
            CachedObject fnd = l.get(0);
            assert (fnd != null) : "Error finding element with id " + i;
            assert (fnd.getCounter() == i) : "Counter not equal: " + fnd.getCounter() + " vs. " + i;
            assert (fnd.getValue().equals("Cached " + i)) : "value not equal: " + fnd.getCounter() + " vs. " + i;
        }
        dur = System.currentTimeMillis() - start;
        log.info("Searching  took " + dur + " ms");
        log.info("Cache Hits Percentage: " + morphium.getStatistics().get(StatisticKeys.CHITSPERC.name()) + "%");
    }


    @Test
    public void cachedWritingTest() throws Exception {
        log.info("Starting background writing test - single objects");
        long start = System.currentTimeMillis();
        for (int i = 1; i <= NO_OBJECTS; i++) {
            CachedObject o = new CachedObject();
            o.setCounter(i);
            o.setValue("Cached " + i);
            morphium.store(o);
        }

        long dur = System.currentTimeMillis() - start;
        log.info("Storing (in Cache) single took " + dur + " ms");
        waitForWriteBufferToFlush(3000);
        waitForWrites();
        dur = System.currentTimeMillis() - start;
        log.info("Storing took " + dur + " ms overall");
        Thread.sleep(500);
        randomCheck();
        Map<String, Double> statistics = morphium.getStatistics();
        Double uc = statistics.get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject");
        assert (uc > 0) : "No Cached Object cached?!?!?!";

    }


    @Test
    public void checkListWriting() {
        List<CachedObject> lst = new ArrayList<>();
        try {
            morphium.save(lst);
            morphium.storeBuffered(lst);
        } catch (Exception e) {
            log.info("Got exception, good!");
            return;
        }
        //noinspection ConstantConditions
        assert (false) : "Exception missing!";
    }

    @Test
    public void checkToStringUniqueness() {
        Query<CachedObject> q = morphium.createQueryFor(CachedObject.class);
        q = q.f(CachedObject.Fields.value).eq("Test").f(CachedObject.Fields.counter).gt(5);
        String t = q.toString();
        log.info("Tostring: " + q);
        q = morphium.createQueryFor(CachedObject.class);
        q = q.f(CachedObject.Fields.counter).gt(5).f(CachedObject.Fields.value).eq("Test");
        String s = q.toString();
        if (!s.equals(t)) {
            log.warn("Warning: order is important s=" + s + " and t=" + t);
        }

        q = morphium.createQueryFor(CachedObject.class);
        q = q.f(CachedObject.Fields.counter).gt(5).sort("counter", "-value");
        t = q.toString();
        q = morphium.createQueryFor(CachedObject.class);
        q = q.f(CachedObject.Fields.counter).gt(5);
        s = q.toString();
        assert (!t.equals(s)) : "Values should not be equal: s=" + s + " t=" + t;
    }

    @Test
    public void mixedListWritingTest() {
        List<Object> tst = new ArrayList<>();
        int cached = 0;
        int uncached = 0;
        for (int i = 0; i < NO_OBJECTS; i++) {
            if (Math.random() < 0.5) {
                cached++;
                CachedObject c = new CachedObject();
                c.setValue("List Test!");
                c.setCounter(11111);
                tst.add(c);
            } else {
                uncached++;
                UncachedObject uc = new UncachedObject();
                uc.setStrValue("List Test uc");
                uc.setCounter(22222);
                tst.add(uc);
            }
        }
        log.info("Writing " + cached + " Cached and " + uncached + " uncached objects!");

        morphium.storeList(tst);
        waitForWriteBufferToFlush(3000);
        waitForWrites();
        //Still waiting - storing lists is not shown in number of write buffer entries
        //        try {
        //            Thread.sleep(2000);
        //        } catch (InterruptedException e) {
        //            throw new RuntimeException(e);
        //        }
        Query<UncachedObject> qu = morphium.createQueryFor(UncachedObject.class);
        assert (qu.countAll() == uncached) : "Difference in object count for cached objects. Wrote " + uncached + " found: " + qu.countAll();
        Query<CachedObject> q = morphium.createQueryFor(CachedObject.class);
        assert (q.countAll() == cached) : "Difference in object count for cached objects. Wrote " + cached + " found: " + q.countAll();

    }


    @Test
    public void arHelperTest() {
        AnnotationAndReflectionHelper annotationHelper = morphium.getARHelper();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 500000; i++)
            annotationHelper.isAnnotationPresentInHierarchy(UncachedObject.class, Entity.class);
        long dur = System.currentTimeMillis() - start;
        log.info("present duration: " + dur);

        start = System.currentTimeMillis();
        for (int i = 0; i < 500000; i++)
            annotationHelper.isAnnotationPresentInHierarchy(UncachedObject.class, Entity.class);
        dur = System.currentTimeMillis() - start;
        log.info("present duration: " + dur);

        start = System.currentTimeMillis();
        for (int i = 0; i < 500000; i++)
            annotationHelper.getFields(UncachedObject.class, Id.class);
        dur = System.currentTimeMillis() - start;
        log.info("fields an duration: " + dur);
        start = System.currentTimeMillis();
        for (int i = 0; i < 500000; i++)
            annotationHelper.getFields(UncachedObject.class, Id.class);
        dur = System.currentTimeMillis() - start;
        log.info("fields an duration: " + dur);

        start = System.currentTimeMillis();
        for (int i = 0; i < 500000; i++)
            annotationHelper.getFields(UncachedObject.class);
        dur = System.currentTimeMillis() - start;
        log.info("fields duration: " + dur);

        start = System.currentTimeMillis();
        for (int i = 0; i < 500000; i++)
            annotationHelper.getFields(UncachedObject.class);
        dur = System.currentTimeMillis() - start;
        log.info("fields duration: " + dur);

        start = System.currentTimeMillis();
        for (int i = 0; i < 500000; i++)
            annotationHelper.getAnnotationFromHierarchy(UncachedObject.class, Entity.class);
        dur = System.currentTimeMillis() - start;
        log.info("Hierarchy duration: " + dur);

        start = System.currentTimeMillis();
        for (int i = 0; i < 500000; i++)
            annotationHelper.getAnnotationFromHierarchy(UncachedObject.class, Entity.class);
        dur = System.currentTimeMillis() - start;
        log.info("Hierarchy duration: " + dur);


        start = System.currentTimeMillis();
        for (int i = 0; i < 50000; i++) {
            List<String> lst = annotationHelper.getFields(UncachedObject.class);
            for (String f : lst) {
                Field fld = annotationHelper.getField(UncachedObject.class, f);
                fld.isAnnotationPresent(Id.class);
            }
        }
        dur = System.currentTimeMillis() - start;
        log.info("fields / getField duration: " + dur);

        start = System.currentTimeMillis();
        for (int i = 0; i < 50000; i++) {
            List<String> lst = annotationHelper.getFields(UncachedObject.class);
            for (String f : lst) {
                Field fld = annotationHelper.getField(UncachedObject.class, f);
                fld.isAnnotationPresent(Id.class);
            }
        }
        dur = System.currentTimeMillis() - start;
        log.info("fields / getField duration: " + dur);


    }

    @Test
    public void listOfIdsTest() {
        morphium.dropCollection(ListOfIdsContainer.class);
        List<MorphiumId> lst = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            lst.add(new MorphiumId());
        }

        Map<String, MorphiumId> map = new HashMap<>();
        for (int i = 0; i < 10; i++) map.put("" + i, new MorphiumId());
        ListOfIdsContainer c = new ListOfIdsContainer();
        c.value = "a value";
        c.others = lst;
        c.idMap = map;
        c.simpleId = new MorphiumId();


        morphium.store(c);

        Query<ListOfIdsContainer> q = morphium.createQueryFor(ListOfIdsContainer.class);
        ListOfIdsContainer cnt = q.get();

        assert (c.id.equals(cnt.id));
        assert (c.value.equals(cnt.value));

        for (int i = 0; i < 10; i++) {
            assert (c.others.get(i).equals(cnt.others.get(i)));
            assert (c.idMap.get("" + i).equals(cnt.idMap.get("" + i)));
        }

    }

    @Test
    public void dataIntegrityTest() throws Exception {
        morphium.dropCollection(UncachedObject.class);

        UncachedObject uc = new UncachedObject("String", 42);
        morphium.insert(uc);

        Map<String, List<Map<String, Object>>> database = ((InMemoryDriver) morphium.getDriver()).getDatabase(morphium.getConfig().getDatabase());
        List<Map<String, Object>> collection = database.get("uncached_object");
        assertThat(collection.size()).isEqualTo(1);
        assertThat(collection.get(0).get("_id")).isInstanceOf(ObjectId.class);
        UncachedObject uc2 = morphium.findById(UncachedObject.class, uc.getMorphiumId());
        assertThat(collection.get(0).get("_id")).isInstanceOf(ObjectId.class);
        assertThat(uc2).isEqualTo(uc);
    }

    @Test
    public void insertTest() throws Exception {
        morphium.dropCollection(UncachedObject.class);
        UncachedObject uc = new UncachedObject();
        uc.setCounter(1);
        uc.setStrValue("A value");
        log.info("Storing new value - no problem");
        morphium.insert(uc);
        assert (uc.getMorphiumId() != null);
        Thread.sleep(200);
        assert (morphium.findById(UncachedObject.class, uc.getMorphiumId()) != null);

        log.info("Inserting again - exception expected");
        boolean ex = false;
        try {
            morphium.insert(uc);
        } catch (Exception e) {
            log.info("Got exception as expected " + e.getMessage());
            ex = true;
        }
        assertThat(ex).isTrue();
        uc = new UncachedObject();
        uc.setStrValue("2");
        uc.setMorphiumId(new MorphiumId());
        uc.setCounter(3);
        morphium.insert(uc);
        Thread.sleep(200);
        assert (morphium.findById(UncachedObject.class, uc.getMorphiumId()) != null);

    }


    @Test
    public void insertListTest() throws Exception {

        morphium.dropCollection(UncachedObject.class);
        List<UncachedObject> lst = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            UncachedObject uc = new UncachedObject();
            uc.setCounter(i);
            uc.setStrValue("" + i);
            lst.add(uc);
        }
        morphium.insert(lst);
        Thread.sleep(500);
        long c = morphium.createQueryFor(UncachedObject.class).countAll();
        System.err.println("Found " + c);
        assert (c == 100);
        List<UncachedObject> lst2 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UncachedObject uc = new UncachedObject();
            uc.setCounter(i);
            uc.setStrValue("" + i);
            lst2.add(uc);
        }
        lst2.add(lst.get(0));
        boolean ex = false;
        try {
            morphium.insert(lst);
        } catch (Throwable e) {
            log.info("Exception expected!");
            ex = true;
        }
        assert (ex);

    }


    private void createTestUc() {
        Query<UncachedObject> qu = morphium.createQueryFor(UncachedObject.class);
        qu.setCollectionName("test_uc");
        if (qu.countAll() != 25000) {
            morphium.dropCollection(UncachedObject.class, "test_uc", null);
            log.info("Creating uncached objects");

            List<UncachedObject> lst = new ArrayList<>();
            for (int i = 0; i < 25000; i++) {
                UncachedObject o = new UncachedObject();
                o.setCounter(i + 1);
                o.setStrValue("V" + i);
                lst.add(o);
            }
            morphium.storeList(lst, "test_uc");
            log.info("creation finished");
        } else {
            log.info("Testdata already filled...");
        }
    }


    @Test
    public void parallelIteratorAccessTest() throws Exception {
        createTestUc();
        runningThreads = 0;


        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                int myNum = runningThreads++;
                log.info("Starting thread..." + myNum);
                Query<UncachedObject> qu = morphium.createQueryFor(UncachedObject.class).sort("counter");
                qu.setCollectionName("test_uc");
                //                    MorphiumIterator<UncachedObject> it = qu.asIterable(5000, 15);
                MorphiumIterator<UncachedObject>[] toTest = new MorphiumIterator[]{qu.asIterable(), qu.asIterable(1000)};
                long count = qu.countAll();
                for (MorphiumIterator<UncachedObject> it : toTest) {
                    for (UncachedObject uc : it) {
                        assert (it.getCursor() == uc.getCounter());
                        if (it.getCursor() % 2500 == 0) {
                            log.info("Thread " + myNum + " read " + it.getCursor() + "/" + count);
                            Thread.yield();
                        }
                    }
                }
                runningThreads--;
                log.info("Thread finished");
            }).start();
            Thread.sleep(250);
        }
        Thread.sleep(1000);
        while (runningThreads > 0) {
            Thread.sleep(100);
        }
    }


    @Test
    public void basicIteratorTest() throws Exception {
        createUncachedObjects(1000);
        Query<UncachedObject> qu = morphium.createQueryFor(UncachedObject.class);
        long start = System.currentTimeMillis();
        MorphiumIterator<UncachedObject> it = qu.asIterable(2);
        assert (it.hasNext());
        UncachedObject u = it.next();
        assert (u.getCounter() == 1);
        log.info("Got one: " + u.getCounter() + "  / " + u.getStrValue());
        log.info("Current Buffersize: " + it.available());
        assert (it.available() == 2);

        u = it.next();
        assert (u.getCounter() == 2);
        u = it.next();
        assert (u.getCounter() == 3);
        assert (qu.countAll() == 1000);
        assert (it.getCursor() == 3);

        u = it.next();
        assert (u.getCounter() == 4);
        u = it.next();
        assert (u.getCounter() == 5);

        while (it.hasNext()) {
            u = it.next();
            log.info("Object: " + u.getCounter());
        }

        assert (u.getCounter() == 1000);
        log.info("Took " + (System.currentTimeMillis() - start) + " ms");

        for (UncachedObject uc : qu.asIterable(100)) {
            if (uc.getCounter() % 100 == 0) {
                log.info("Got msg " + uc.getCounter());
            }
        }
        morphium.dropCollection(UncachedObject.class);
        u = new UncachedObject();
        u.setStrValue("Hello");
        u.setCounter(1900);
        morphium.store(u);
        Thread.sleep(1500);
        for (UncachedObject uc : morphium.createQueryFor(UncachedObject.class).asIterable(100)) {
            log.info("Got another " + uc.getCounter());
        }

    }

    @Test
    public void nullValueTest() {
        UncachedObject uc = new UncachedObject(null, 10);
        morphium.store(uc);
        UncachedObject uc2 = new UncachedObject("null", 22);
        morphium.store(uc2);

        morphium.reread(uc);
        assertThat(uc.getStrValue()).isNull();
        UncachedObject o = morphium.createQueryFor(UncachedObject.class).f(UncachedObject.Fields.strValue).eq(null).get();
        assertThat(o).isNotNull();
        assertThat(o.getStrValue()).isNull();
        assertThat(o.getCounter()).isEqualTo(10);

        List<UncachedObject> list = morphium.createQueryFor(UncachedObject.class).f(UncachedObject.Fields.strValue).in(Arrays.asList("null", null)).asList();
        assertThat(list.size()).isEqualTo(2);
        assert (list.get(0).getStrValue() == null || list.get(1).getStrValue() == null);
        assert (list.get(0).getCounter() == 10 || list.get(1).getCounter() == 10);

        list = morphium.createQueryFor(UncachedObject.class).f(UncachedObject.Fields.strValue).nin(Arrays.asList("null")).asList();
        assert (list.size() == 1);
        assert (list.get(0).getStrValue() == null);

        list = morphium.createQueryFor(UncachedObject.class).f(UncachedObject.Fields.strValue).nin(Arrays.asList((String) null)).asList();
        assert (list.size() == 1);
        assert (list.get(0).getStrValue().equals("null"));
    }


    @Entity
    public static class ListOfIdsContainer {
        @Id
        public MorphiumId id;
        public List<MorphiumId> others;
        public Map<String, MorphiumId> idMap;
        public MorphiumId simpleId;
        public String value;
    }

}
