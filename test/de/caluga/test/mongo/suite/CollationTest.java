package de.caluga.test.mongo.suite;

import de.caluga.morphium.Collation;
import de.caluga.morphium.aggregation.Aggregator;
import de.caluga.morphium.aggregation.Expr;
import de.caluga.morphium.query.Query;
import de.caluga.test.mongo.suite.data.UncachedObject;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class CollationTest extends MorphiumTestBase {


    @Test
    public void queryTest() throws Exception {
        morphium.dropCollection(UncachedObject.class);
        Thread.sleep(100);
        morphium.store(new UncachedObject("A", 1));
        morphium.store(new UncachedObject("B", 1));
        morphium.store(new UncachedObject("C", 1));
        morphium.store(new UncachedObject("a", 1));
        morphium.store(new UncachedObject("b", 1));
        morphium.store(new UncachedObject("c", 1));

        List<UncachedObject> lst = morphium.createQueryFor(UncachedObject.class).setCollation(new Collation().locale("de")).sort("value").asList();
        String result = "";
        for (UncachedObject u : lst) {
            log.info("value: " + u.getValue());
            result += u.getValue();
        }
        assert (result.equals("aAbBcC")) : "Wrong ordering: " + result;

    }

    @Test
    public void countTest() throws Exception {
        morphium.dropCollection(UncachedObject.class);
        Thread.sleep(100);
        morphium.store(new UncachedObject("A", 1));
        morphium.store(new UncachedObject("B", 1));
        morphium.store(new UncachedObject("C", 1));
        morphium.store(new UncachedObject("a", 1));
        morphium.store(new UncachedObject("b", 1));
        morphium.store(new UncachedObject("c", 1));
        Thread.sleep(100);
        long count = morphium.createQueryFor(UncachedObject.class).setCollation(new Collation().locale("de").strength(Collation.Strength.PRIMARY)).f("value").eq("a").countAll();
        assert (count == 2);

    }

    @Test
    public void updateTest() throws Exception {
        morphium.dropCollection(UncachedObject.class);
        Thread.sleep(100);
        morphium.store(new UncachedObject("A", 1));
        morphium.store(new UncachedObject("B", 1));
        morphium.store(new UncachedObject("C", 1));
        morphium.store(new UncachedObject("a", 1));
        morphium.store(new UncachedObject("b", 1));
        morphium.store(new UncachedObject("c", 1));
        Thread.sleep(100);
        Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class).setCollation(new Collation().locale("de").strength(Collation.Strength.PRIMARY)).f("value").eq("a");
        morphium.inc(q, UncachedObject.Fields.counter, 1, false, true);

        Thread.sleep(100);
        for (UncachedObject u : q.asIterable()) {
            assert (u.getCounter() == 2);
        }

    }

    @Test
    public void delTest() throws Exception {
        morphium.dropCollection(UncachedObject.class);
        Thread.sleep(100);
        morphium.store(new UncachedObject("A", 1));
        morphium.store(new UncachedObject("B", 1));
        morphium.store(new UncachedObject("C", 1));
        morphium.store(new UncachedObject("a", 1));
        morphium.store(new UncachedObject("b", 1));
        morphium.store(new UncachedObject("c", 1));
        Thread.sleep(100);
        Query<UncachedObject> q = morphium.createQueryFor(UncachedObject.class).setCollation(new Collation().locale("de").strength(Collation.Strength.PRIMARY)).f("value").eq("a");
        morphium.delete(q);

        Thread.sleep(100);
        assert (morphium.createQueryFor(UncachedObject.class).countAll() == 4);

    }


    @Test
    public void aggregateTest() throws Exception {
        morphium.dropCollection(UncachedObject.class);
        Thread.sleep(100);
        morphium.store(new UncachedObject("A", 1));
        morphium.store(new UncachedObject("B", 1));
        morphium.store(new UncachedObject("C", 1));
        morphium.store(new UncachedObject("a", 1));
        morphium.store(new UncachedObject("b", 1));
        morphium.store(new UncachedObject("c", 1));
        Thread.sleep(1000);
        Aggregator<UncachedObject, Map> agg = morphium.createAggregator(UncachedObject.class, Map.class);
        agg.collation(new Collation().locale("de").strength(Collation.Strength.PRIMARY));
        agg.match(Expr.eq(Expr.field("value"), Expr.string("a")));
        List<Map> lst = agg.aggregate();
        assert (lst.size() == 2) : "Count wrong " + lst.size();
    }


}
