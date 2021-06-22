package de.caluga.test.mongo.suite.base;

import de.caluga.test.mongo.suite.data.UncachedObject;
import org.junit.Test;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: stephan
 * Date: 29.04.14
 * Time: 13:24
 * To change this template use File | Settings | File Templates.
 */
public class DistinctTest extends MorphiumTestBase {

    @Test
    public void distinctTest() {
        createUncachedObjects(100);

        List lst = morphium.createQueryFor(UncachedObject.class).distinct("counter");
        assert (lst.size() == 100);
        lst = morphium.createQueryFor(UncachedObject.class).distinct("str_value");
        assert (lst.size() == 1);
    }
//
//    @Test
//    public void distinctTestInMemory() throws Exception {
//        morphiumInMemeory.dropCollection(UncachedObject.class);
//        createUncachedObjectsInMemory(100);
//
//        List lst = morphiumInMemeory.createQueryFor(UncachedObject.class).distinct("counter");
//        assert (lst.size() == 100);
//        lst = morphiumInMemeory.createQueryFor(UncachedObject.class).distinct("value");
//        assert (lst.size() == 1);
//    }
}
