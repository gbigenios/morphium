package de.caluga.test.mongo.suite.base;

import de.caluga.test.mongo.suite.data.CachedObject;
import de.caluga.test.mongo.suite.data.ComplexObject;
import de.caluga.test.mongo.suite.data.TestEntityNameProvider;
import org.junit.Test;

/**
 * User: Stephan Bösebeck
 * Date: 10.05.12
 * Time: 13:05
 * <p/>
 */
public class CollectionMappingTest extends MorphiumTestBase {
    @Test
    public void collectionMappingTest() {
        String n = morphium.getMapper().getCollectionName(CachedObject.class);
        assert (n.equals("cached_object_" + TestEntityNameProvider.number.get())) : "Collection wrong";
        n = morphium.getMapper().getCollectionName(ComplexObject.class);
        assert (n.equals("ComplexObject")) : "Collection wrong";

    }
}
