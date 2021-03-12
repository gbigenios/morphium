/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.caluga.test.mongo.suite.data;

import de.caluga.morphium.annotations.*;
import de.caluga.morphium.annotations.caching.Cache;
import de.caluga.morphium.annotations.caching.WriteBuffer;
import de.caluga.morphium.driver.MorphiumId;


/**
 * @author stephan
 */
@Entity(nameProvider = TestEntityNameProvider.class)
@Cache(maxEntries = 20000, strategy = Cache.ClearStrategy.LRU, syncCache = Cache.SyncCacheStrategy.CLEAR_TYPE_CACHE, timeout = 150000)
@WriteBuffer(timeout = 500, size = 100, strategy = WriteBuffer.STRATEGY.JUST_WARN)
@WriteSafety(level = SafetyLevel.WAIT_FOR_ALL_SLAVES, timeout = 3000, waitForJournalCommit = false)
public class CachedObject {

    @Index
    private String value;
    @Index
    private int counter;

    @Id
    private MorphiumId id;

    public CachedObject() {
    }

    public CachedObject(String value, int counter) {
        this.value = value;
        this.counter = counter;
    }

    public int getCounter() {
        return counter;
    }

    public MorphiumId getId() {
        return id;
    }

    public void setId(MorphiumId id) {
        this.id = id;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }


    public String toString() {
        return "Counter: " + counter + " Value: " + value + " MongoId: " + id;
    }


    public enum Fields {id, value, counter}
}
