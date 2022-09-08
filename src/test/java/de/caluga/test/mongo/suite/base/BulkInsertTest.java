package de.caluga.test.mongo.suite.base;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.annotations.ReadPreferenceLevel;
import de.caluga.morphium.async.AsyncOperationCallback;
import de.caluga.morphium.async.AsyncOperationType;
import de.caluga.morphium.query.Query;
import de.caluga.test.mongo.suite.data.Person;
import de.caluga.test.mongo.suite.data.UncachedObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * User: Stephan Bösebeck
 * Date: 01.07.12
 * Time: 11:34
 * <p/>
 */
@SuppressWarnings("AssertWithSideEffects")
public class BulkInsertTest extends MultiDriverTestBase {
    private boolean asyncSuccess = true;
    private boolean asyncCall = false;

    @ParameterizedTest
    @MethodSource("getMorphiumInstances")
    public void maxWriteBatchTest(Morphium morphium) throws Exception {
        try (morphium) {
            //logSeparator("Using driver: " + morphium.getDriver().getClass().getName());
            morphium.clearCollection(UncachedObject.class);

            List<UncachedObject> lst = new ArrayList<>();
            for (int i = 0; i < 4212; i++) {
                UncachedObject u = new UncachedObject();
                u.setStrValue("V" + i);
                u.setCounter(i);
                lst.add(u);
            }
            morphium.storeList(lst);
            Thread.sleep(1000);
            long l = morphium.createQueryFor(UncachedObject.class).countAll();
            assert (l == 4212) : "Count wrong: " + l;

            for (UncachedObject u : lst) {
                u.setCounter(u.getCounter() + 1000);
            }
            for (int i = 0; i < 100; i++) {
                UncachedObject u = new UncachedObject();
                u.setStrValue("O" + i);
                u.setCounter(i + 1200);
                lst.add(u);
            }
            morphium.storeList(lst);


        }
    }

    @ParameterizedTest
    @MethodSource("getMorphiumInstances")
    public void bulkInsert(Morphium morphium) throws Exception {
        try (morphium) {
            //logSeparator("Using driver: " + morphium.getDriver().getClass().getName());
            morphium.clearCollection(UncachedObject.class);
            log.info("Start storing single");
            long start = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                UncachedObject uc = new UncachedObject();
                uc.setCounter(i + 1);
                uc.setStrValue("nix " + i);
                morphium.store(uc);
            }
            long dur = System.currentTimeMillis() - start;
            log.info("storing objects one by one took " + dur + " ms");
            morphium.clearCollection(UncachedObject.class);
            log.info("Start storing list");
            List<UncachedObject> lst = new ArrayList<>();
            start = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                UncachedObject uc = new UncachedObject();
                uc.setCounter(i + 1);
                uc.setStrValue("nix " + i);
                lst.add(uc);
            }
            log.info("List prepared...");
            morphium.storeList(lst);
            assertNotNull(lst.get(0).getMorphiumId());
            ;
            dur = System.currentTimeMillis() - start;
            if ((morphium.getWriteBufferCount() != 0)) {
                throw new AssertionError("WriteBufferCount not 0!? Buffered:" + morphium.getBufferedWriterBufferCount());
            }
            log.info("storing objects one by one took " + dur + " ms");
            Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class);
            q.setReadPreferenceLevel(ReadPreferenceLevel.PRIMARY);
            assert (q.countAll() == 100) : "Assert not all stored yet????";

        }
    }

    @ParameterizedTest
    @MethodSource("getMorphiumInstances")
    public void bulkInsertAsync(Morphium morphium) throws Exception {
        try (morphium) {
            //logSeparator("Using driver: " + morphium.getDriver().getClass().getName());
            morphium.clearCollection(UncachedObject.class);
            log.info("Start storing single");
            long start = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                UncachedObject uc = new UncachedObject();
                uc.setCounter(i + 1);
                uc.setStrValue("nix " + i);
                morphium.store(uc, new AsyncOperationCallback<UncachedObject>() {
                    @Override
                    public void onOperationSucceeded(AsyncOperationType type, Query<UncachedObject> q, long duration, List<UncachedObject> result, UncachedObject entity, Object... param) {
                        asyncCall = true;
                    }

                    @Override
                    public void onOperationError(AsyncOperationType type, Query<UncachedObject> q, long duration, String error, Throwable t, UncachedObject entity, Object... param) {
                        log.error("Got async error - should not be!!!", t);
                        asyncSuccess = false;
                    }
                });
            }
            waitForWrites(morphium);
            long dur = System.currentTimeMillis() - start;
            log.info("storing objects one by one async took " + dur + " ms");
            Thread.sleep(1000);
            assert (asyncSuccess);
            assert (asyncCall);

            morphium.clearCollection(UncachedObject.class);

            log.info("Start storing list");
            List<UncachedObject> lst = new ArrayList<>();
            start = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                UncachedObject uc = new UncachedObject();
                uc.setCounter(i + 1);
                uc.setStrValue("nix " + i);
                lst.add(uc);
            }
            morphium.storeList(lst);
            dur = System.currentTimeMillis() - start;
            assert (morphium.getWriteBufferCount() == 0) : "WriteBufferCount not 0!? Buffered:" + morphium.getBufferedWriterBufferCount();
            log.info("storing objects one by one took " + dur + " ms");
            Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class);
            q.setReadPreferenceLevel(ReadPreferenceLevel.PRIMARY);
            assert (q.countAll() == 1000) : "Assert not all stored yet????";

        }
    }

    @ParameterizedTest
    @MethodSource("getMorphiumInstances")
    public void bulkInsertNonId(Morphium morphium) throws Exception {
        try (morphium) {
            morphium.dropCollection(Person.class);
            List<Person> prs = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Person p = new Person();
                p.setBirthday(new Date());
                p.setName("" + i);
                prs.add(p);
            }
            morphium.storeList(prs);

            Thread.sleep(1000);
            assertNotNull(prs.get(0).getId());
            ;
            long cnt = morphium.createQueryFor(Person.class).countAll();
            assert (cnt == 100);
        }
    }
}
