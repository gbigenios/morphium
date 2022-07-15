package de.caluga.morphium.driver.commands;

import de.caluga.morphium.IndexDescription;
import de.caluga.morphium.driver.Doc;
import de.caluga.morphium.driver.MorphiumDriver;
import de.caluga.morphium.driver.MorphiumDriverException;
import de.caluga.morphium.driver.wire.MongoConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListIndexesCommand extends MongoCommand<ListIndexesCommand> {

    public ListIndexesCommand(MongoConnection d) {
        super(d);
    }

    @Override
    public String getCommandName() {
        return "listIndexes";
    }

    public List<IndexDescription> execute() throws MorphiumDriverException {
        var msg = getConnection().sendCommand(asMap());
        var crs = getConnection().getAnswerFor(msg, getDefaultBatchSize());

        List<IndexDescription> lst = new ArrayList<>();
        while (crs.hasNext()) {
            Map<String, Object> next = crs.next();
            if (next.get("ok") != null && next.get("ok").equals(Double.valueOf(0))) continue;
            var idx = IndexDescription.fromMap(next);
            if (idx.getKey().containsKey("_ftsx") && idx.getKey().get("_fts").equals("text")) {
                //text index
                Map<String, Object> weights = idx.getWeights();
                var m = Doc.of();
                for (var k : weights.keySet()) {
                    m.put(k, "text");
                }
                idx.setKey(m);

            }
            lst.add(idx);
        }
        return lst;
    }
}
