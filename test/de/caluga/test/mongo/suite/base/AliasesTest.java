package de.caluga.test.mongo.suite.base;

import de.caluga.morphium.query.Query;
import de.caluga.test.mongo.suite.data.ComplexObject;
import org.junit.Test;

import java.util.Date;

/**
 * User: Stephan Bösebeck
 * Date: 07.05.12
 * Time: 18:02
 * <p/>
 */
public class AliasesTest extends MorphiumTestBase {
    @Test
    public void aliasTest() {
        Query<ComplexObject> q = morphium.createQueryFor(ComplexObject.class).f("last_changed").eq(new Date());
        assert (q != null) : "Null Query?!?!?";
        assert (q.toQueryObject().toString().startsWith("{changed=")) : "Wrong query: " + q.toQueryObject().toString();
        log.info("All ok");
    }
}
