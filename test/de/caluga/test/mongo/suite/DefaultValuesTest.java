package de.caluga.test.mongo.suite;

import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.driver.MorphiumId;
import org.junit.Test;

public class DefaultValuesTest extends MorphiumTestBase {

    @Test
    public void defaultValuesTest() throws Exception {
        DefaultsTestEntitiy e = new DefaultsTestEntitiy();
        morphium.store(e);

        DefaultsTestEntitiy read = morphium.findById(DefaultsTestEntitiy.class, e.id);
        assert (read.bool == null);
        assert (read.v == e.v);
        assert (read.value.equals("value"));
        assert (read.value2.equals("value2"));

        morphium.set(read, "value", (Object) null);
        morphium.unset(read, "value2");

        read = morphium.findById(DefaultsTestEntitiy.class, e.id);
        assert (read.value == null);
        assert (read.value2.equals("value2"));
        assert (read.bool == null);
    }


    @Entity
    public static class DefaultsTestEntitiy {
        @Id
        public MorphiumId id;
        public String value = "value";
        public String value2 = "value2";
        public int v = 12;
        public Boolean bool = null;
    }
}
