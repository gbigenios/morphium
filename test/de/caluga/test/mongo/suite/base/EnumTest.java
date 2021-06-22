package de.caluga.test.mongo.suite.base;

import de.caluga.morphium.query.MorphiumIterator;
import de.caluga.test.mongo.suite.data.TestEnum;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: Stephan Bösebeck
 * Date: 04.05.12
 * Time: 12:44
 * <p/>
 */
@SuppressWarnings("unchecked")
public class EnumTest extends MorphiumTestBase {

    @Test
    public void enumTest() {
        morphium.clearCollection(EnumEntity.class);
        EnumEntity ent = new EnumEntity();
        ent.setTst(TestEnum.TEST1);
        ent.setValue("ein Test");
        morphium.store(ent);

        ent = morphium.createQueryFor(EnumEntity.class).f("value").eq("ein Test").get();
        assert (ent.getTst() != null) : "Enum is null!";
        assert (ent.getTst().equals(TestEnum.TEST1)) : "Enum error!";

        ent = morphium.createQueryFor(EnumEntity.class).f("tst").eq(TestEnum.TEST1).get();
        assert (ent.getTst() != null) : "Enum is null!";
        assert (ent.getTst().equals(TestEnum.TEST1)) : "Enum error!";

    }

    @Test
    public void enumListTest() {
        morphium.clearCollection(EnumEntity.class);
        EnumEntity ent = new EnumEntity();
        ent.setTst(TestEnum.TEST1);
        ent.setValue("ein Test");
        List<TestEnum> lst = new ArrayList();
        lst.add(TestEnum.NOCH_EIN_TEST);
        lst.add(TestEnum.TEST2);
        lst.add(TestEnum.NOCH_EIN_TEST);
        ent.setTstLst(lst);
        morphium.store(ent);

        EnumEntity ent2 = morphium.createQueryFor(EnumEntity.class).f("value").eq("ein Test").get();
        assert (ent2.getTst() != null) : "Enum is null!";
        assert (ent2.getTst().equals(TestEnum.TEST1)) : "Enum error!";

        ent2 = morphium.createQueryFor(EnumEntity.class).f("tst").eq(TestEnum.TEST1).get();
        assert (ent2.getTst() != null) : "Enum is null!";
        assert (ent2.getTst().equals(TestEnum.TEST1)) : "Enum error!";

        assert (ent2.getTstLst().size() == 3) : "Size of testlist wrong: " + ent2.getTstLst().size();
        for (int i = 0; i < ent2.getTstLst().size(); i++) {
            assert (ent2.getTstLst().get(i).equals(ent.getTstLst().get(i))) : "Enums differ?!?!? " + ent.getTstLst().get(i).name() + "!=" + ent2.getTstLst().get(i).name();
        }

    }

    @Test
    public void enumIteratorTest() {

        morphium.clearCollection(EnumEntity.class);
        for (int i = 0; i < 100; i++) {
            EnumEntity ent = new EnumEntity();
            ent.setTst(TestEnum.TEST1);
            ent.setValue("ein Test " + i);
            List<TestEnum> lst = new ArrayList();
            lst.add(TestEnum.NOCH_EIN_TEST);
            lst.add(TestEnum.TEST2);
            lst.add(TestEnum.NOCH_EIN_TEST);
            ent.setTstLst(lst);
            morphium.store(ent);
        }

        MorphiumIterator<EnumEntity> it = morphium.createQueryFor(EnumEntity.class).f("tst").in(Arrays.asList(TestEnum.TEST1)).asIterable();

        for (EnumEntity e : it) {
            log.info("Got enum: " + e.getTst());
        }
    }
}
