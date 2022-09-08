package de.caluga.morphium.driver.commands;

import de.caluga.morphium.driver.Doc;
import de.caluga.morphium.driver.MorphiumDriver;
import de.caluga.morphium.driver.MorphiumDriverException;
import de.caluga.morphium.driver.wire.MongoConnection;

import java.util.Map;

public class CountMongoCommand extends MongoCommand<CountMongoCommand> implements SingleResultCommand {
    private Map<String, Object> query;
    private Integer limit;
    private Integer skip;
    private Object hint;
    private Map<String, Object> readConcern;
    private Map<String, Object> collation;

    public CountMongoCommand(MongoConnection d) {
        super(d);
    }


    public Map<String, Object> getQuery() {
        return query;
    }

    public CountMongoCommand setQuery(Map<String, Object> query) {
        this.query = query;
        return this;
    }

    public Integer getLimit() {
        return limit;
    }

    public CountMongoCommand setLimit(Integer limit) {
        this.limit = limit;
        return this;
    }

    public Integer getSkip() {
        return skip;
    }

    public CountMongoCommand setSkip(Integer skip) {
        this.skip = skip;
        return this;
    }

    public Object getHint() {
        return hint;
    }

    public CountMongoCommand setHint(Object hint) {
        this.hint = hint;
        return this;
    }

    public Map<String, Object> getReadConcern() {
        return readConcern;
    }

    public CountMongoCommand setReadConcern(Map<String, Object> readConcern) {
        this.readConcern = readConcern;
        return this;
    }

    public Map<String, Object> getCollation() {
        return collation;
    }

    public CountMongoCommand setCollation(Map<String, Object> collation) {
        this.collation = collation;
        return this;
    }


    @Override
    public String getCommandName() {
        return "count";
    }

    @Override
    public Map<String, Object> execute() throws MorphiumDriverException {
        if (getConnection().getDriver().isTransactionInProgress()) {
            //log.warn("Cannot count while in transaction, will use IDlist!");
            //TODO: use Aggregation
            FindCommand fs = new FindCommand(getConnection());
            fs.setMetaData(getMetaData());
            fs.setDb(getDb());
            fs.setColl(getColl());
            fs.setFilter(getQuery());
            fs.setProjection(Doc.of("_id", 1)); //forcing ID-list
            fs.setCollation(getCollation());
            return Doc.of("n", fs.execute().size());
        }
        int id = executeAsync();
        return getConnection().readSingleAnswer(id);
    }

    public int getCount() throws MorphiumDriverException {
        return (int) execute().get("n");
    }


    @Override
    public int executeAsync() throws MorphiumDriverException {
        if (getConnection().getDriver().isTransactionInProgress()) {
            throw new MorphiumDriverException("Count during transaction is not allowed");
        }
        return super.executeAsync();
    }
}
