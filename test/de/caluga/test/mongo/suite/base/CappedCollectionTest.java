package de.caluga.test.mongo.suite.base;

import de.caluga.test.mongo.suite.data.CappedCol;
import de.caluga.test.mongo.suite.data.TestEntityNameProvider;
import de.caluga.test.mongo.suite.data.UncachedObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by stephan on 08.08.14.
 */
@SuppressWarnings("AssertWithSideEffects")
public class CappedCollectionTest extends MorphiumTestBase {

    @Test
    public void testCreationOfCappedCollection() throws Exception {
        morphium.dropCollection(CappedCol.class);
        Thread.sleep(1000);
        CappedCol cc = new CappedCol();
        cc.setStrValue("A value");
        cc.setCounter(-1);
        morphium.store(cc);


        assert (morphium.getDriver().isCapped(morphium.getConfig().getDatabase(), "capped_col_" + TestEntityNameProvider.number.get()));
        //storing more than max entries
        for (int i = 0; i < 1000; i++) {
            cc = new CappedCol();
            cc.setStrValue("Value " + i);
            cc.setCounter(i);
            morphium.store(cc);
        }
        Thread.sleep(1000);
        assert (morphium.createQueryFor(CappedCol.class).countAll() <= 10);
        for (CappedCol cp : morphium.createQueryFor(CappedCol.class).sort("counter").asIterable(10)) {
            log.info("Capped: " + cp.getCounter() + " - " + cp.getStrValue());
        }

    }


    @Test
    public void testListCreationOfCappedCollection() throws Exception {
        morphium.dropCollection(CappedCol.class);

        List<CappedCol> lst = new ArrayList<>();

        //storing more than max entries
        for (int i = 0; i < 100; i++) {
            CappedCol cc = new CappedCol();
            cc.setStrValue("Value " + i);
            cc.setCounter(i);
            lst.add(cc);
        }

        morphium.storeList(lst);
        assert (morphium.getDriver().isCapped(morphium.getConfig().getDatabase(), "capped_col_" + TestEntityNameProvider.number.get()));
        assert (morphium.createQueryFor(CappedCol.class).countAll() <= 10);
        for (CappedCol cp : morphium.createQueryFor(CappedCol.class).sort("counter").asIterable(10)) {
            log.info("Capped: " + cp.getCounter() + " - " + cp.getStrValue());
        }

    }


    @Test
    public void convertToCappedTest() throws Exception {
        morphium.dropCollection(UncachedObject.class);
        createUncachedObjects(1000);

        morphium.convertToCapped(UncachedObject.class, 100, null);

        assert (morphium.getDriver().isCapped(morphium.getConfig().getDatabase(), "uncached_object_" + TestEntityNameProvider.number.get()));
        assert (morphium.createQueryFor(UncachedObject.class).countAll() <= 100);
    }


}
