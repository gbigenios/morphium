package de.caluga.morphium.writer;

import com.mongodb.BulkWriteError;
import com.mongodb.BulkWriteException;
import de.caluga.morphium.*;
import de.caluga.morphium.annotations.caching.WriteBuffer;
import de.caluga.morphium.async.AsyncOperationCallback;
import de.caluga.morphium.async.AsyncOperationType;
import de.caluga.morphium.driver.bulk.BulkRequestContext;
import de.caluga.morphium.driver.bulk.DeleteBulkRequest;
import de.caluga.morphium.driver.bulk.InsertBulkRequest;
import de.caluga.morphium.driver.bulk.UpdateBulkRequest;
import de.caluga.morphium.query.Query;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * User: Stephan Bösebeck
 * Date: 11.03.13
 * Time: 11:41
 * <p/>
 * Buffered Writer buffers all write requests (store, update, remove...) to mongo for a certain time. After that time the requests are
 * issued en block to mongo. Attention: this is not using BULK-Requests yet!
 */
@SuppressWarnings({"EmptyCatchBlock", "SynchronizeOnNonFinalField"})
public class BufferedMorphiumWriterImpl implements MorphiumWriter, ShutdownListener {

    private Morphium morphium;
    private MorphiumWriter directWriter;
    //needs to be securely stored
    private Map<Class<?>, List<WriteBufferEntry>> opLog = new ConcurrentHashMap<>(); //synced
    private Map<Class<?>, Long> lastRun = new ConcurrentHashMap<>();
    private Thread housekeeping;
    private boolean running = true;
    private static Logger logger = new Logger(BufferedMorphiumWriterImpl.class);
    private boolean orderedExecution = false;

    public BufferedMorphiumWriterImpl() {

    }

    public boolean isOrderedExecution() {
        return orderedExecution;
    }

    public void setOrderedExecution(boolean orderedExecution) {
        this.orderedExecution = orderedExecution;
    }

    private List<WriteBufferEntry> flushToQueue(List<WriteBufferEntry> localQueue) {
        //either buffer size reached, or time is up => queue writes
        List<WriteBufferEntry> didNotWrite = new ArrayList<>();
        Map<String, BulkRequestContext> bulkByCollectionName = new HashMap<>();


//        BulkRequestContext ctx = morphium.getDriver().createBulkContext(morphium.getConfig().getDatabase(), "", false, null);
//        BulkRequestContext octx = morphium.getDriver().createBulkContext(morphium.getConfig().getDatabase(), "", true, null);

        for (WriteBufferEntry entry : localQueue) {
            try {
                if (bulkByCollectionName.get(entry.getCollectionName()) == null) {
                    WriteBuffer w = morphium.getARHelper().getAnnotationFromHierarchy(entry.getEntityType(), WriteBuffer.class);
                    bulkByCollectionName.put(entry.getCollectionName(), morphium.getDriver().createBulkContext(morphium.getConfig().getDatabase(), entry.getCollectionName(), w.ordered(), morphium.getWriteConcernForClass(entry.getEntityType())));
                }
                entry.getToRun().exec(bulkByCollectionName.get(entry.getCollectionName()));
                entry.getCb().onOperationSucceeded(entry.getType(), null, 0, null, null);
            } catch (RejectedExecutionException e) {
                logger.info("too much load - add write to next run");
                didNotWrite.add(entry);
            } catch (Exception e) {
                logger.error("could not write", e);
            }
        }
        try {
            for (BulkRequestContext ctx : bulkByCollectionName.values()) {
                ctx.execute();
            }
        } catch (BulkWriteException bwe) {
            logger.error("Error executing unordered bulk",bwe);
            for (BulkWriteError err:bwe.getWriteErrors()){
                logger.error("Write error: "+err.getMessage()+"\n"+err.getDetails().toString());
            }
        } catch (Exception e) {
            logger.error("Error during exeecution of unordered bulk",e);
        }

        return didNotWrite;
    }


    public void addToWriteQueue(Class<?> type, String collectionName, BufferedBulkOp r, AsyncOperationCallback c, AsyncOperationType t) {
        if (collectionName == null) collectionName = morphium.getMapper().getCollectionName(type);
        WriteBufferEntry wb = new WriteBufferEntry(type, collectionName, r, System.currentTimeMillis(), c, t);
        WriteBuffer w = morphium.getARHelper().getAnnotationFromHierarchy(type, WriteBuffer.class);
        int size = 0;
        int timeout = morphium.getConfig().getWriteBufferTime();
        WriteBuffer.STRATEGY strategy = WriteBuffer.STRATEGY.JUST_WARN;
        boolean ordered = false;
        if (w != null) {
            ordered = w.ordered();
            size = w.size();
            strategy = w.strategy();
        }
        synchronized (opLog) {
            if (opLog.get(type) == null) {
                opLog.put(type, new Vector<WriteBufferEntry>());
            }

            if (size > 0 && opLog.get(type).size() > size) {
                logger.warn("WARNING: Write buffer for type " + type.getName() + " maximum exceeded: " + opLog.get(type).size() + " entries now, max is " + size);
                BulkRequestContext ctx = morphium.getDriver().createBulkContext(morphium.getConfig().getDatabase(), collectionName, ordered, morphium.getWriteConcernForClass(type));
                switch (strategy) {
                    case JUST_WARN:
                        opLog.get(type).add(wb);
                        break;
                    case IGNORE_NEW:
                        logger.warn("ignoring new incoming...");
                        return;
                    case WRITE_NEW:
                        logger.warn("directly writing data... due to strategy setting");
                        r.exec(ctx);
                        break;
                    case WRITE_OLD:

                        Collections.sort(opLog.get(type), new Comparator<WriteBufferEntry>() {
                            @Override
                            public int compare(WriteBufferEntry o1, WriteBufferEntry o2) {
                                return Long.valueOf(o1.getTimestamp()).compareTo(o2.getTimestamp());
                            }
                        });

                        opLog.get(type).get(0).getToRun().exec(ctx);
                        opLog.get(type).remove(0);
                        opLog.get(type).add(wb);
                        break;
                    case DEL_OLD:

                        Collections.sort(opLog.get(type), new Comparator<WriteBufferEntry>() {
                            @Override
                            public int compare(WriteBufferEntry o1, WriteBufferEntry o2) {
                                return Long.valueOf(o1.getTimestamp()).compareTo(o2.getTimestamp());
                            }
                        });
                        if (logger.isDebugEnabled()) {
                            logger.debug("Deleting oldest entry");
                        }
                        opLog.get(type).remove(0);
                        opLog.get(type).add(wb);
                        return;
                }
                ctx.execute();

            } else {
                opLog.get(type).add(wb);
            }
        }
    }

    @Override
    public <T> void store(final T o, final String collection, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(o.getClass(), collection, new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
                //do nothing
                boolean isNew = morphium.getARHelper().getId(o) == null;
                if (!isNew && !morphium.getARHelper().getIdField(o).getType().equals(ObjectId.class)) {
                    //need to check if type is not ObjectId
                    isNew = (morphium.createQueryFor(o.getClass()).f("_id").eq(morphium.getId(o)).countAll() == 0);
                }
                morphium.firePreStoreEvent(o, isNew);
                if (isNew) {
                    ArrayList<Map<String, Object>> objToInsert = new ArrayList<>();
                    objToInsert.add(morphium.getMapper().marshall(o));
                    InsertBulkRequest ins = new InsertBulkRequest(objToInsert);
                    ctx.addRequest(ins);
                } else {

                    UpdateBulkRequest up = new UpdateBulkRequest();
                    up.setMultiple(false);
                    up.setUpsert(false);
                    up.setQuery((morphium.createQueryFor(o.getClass()).f(morphium.getARHelper().getIdFieldName(o)).eq(morphium.getARHelper().getId(o))).toQueryObject());
                    Map<String, Object> cmd = new HashMap<String, Object>();
                    up.setCmd(morphium.getMap("$set", cmd));
                    for (String f : morphium.getARHelper().getFields(o.getClass())) {
                        try {
                            cmd.put(morphium.getARHelper().getFieldName(o.getClass(), f), morphium.getMapper().marshallIfNecessary(morphium.getARHelper().getField(o.getClass(), f).get(o)));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }

                    }
                    ctx.addRequest(up);
                }
                morphium.getCache().clearCacheIfNecessary(o.getClass());
                morphium.firePostStoreEvent(o, isNew);
            }

        }, c, AsyncOperationType.WRITE);
    }

    @Override
    public <T> void store(final List<T> lst, final String collectionName, AsyncOperationCallback<T> c) {
        if (lst == null || lst.size() == 0) {
            if (c != null)
                c.onOperationSucceeded(AsyncOperationType.WRITE, null, 0, lst, null);
            return;
        }
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        morphium.inc(StatisticKeys.WRITES_CACHED);

        final AsyncOperationCallback<T> finalC = c;
        addToWriteQueue(lst.get(0).getClass(), collectionName, new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
                Map<Object, Boolean> map = new HashMap<>();
                morphium.firePreStoreEvent(map);
                for (T o : lst) {
                    map.put(o, morphium.getARHelper().getId(o) == null);
                }
                List<Map<String, Object>> toInsert = new ArrayList<Map<String, Object>>();
                for (Map.Entry<Object, Boolean> entry : map.entrySet()) {
                    if (entry.getValue()) {
                        toInsert.add(morphium.getMapper().marshall(entry.getKey()));
                    } else {
                        store((T) entry.getKey(), morphium.getMapper().getCollectionName(entry.getKey().getClass()), finalC);
                    }
                }
                ctx.addRequest(new InsertBulkRequest(toInsert));
                morphium.firePostStore(map);
            }

        }, c, AsyncOperationType.WRITE);
    }

    @Override
    public <T> void updateUsingFields(final T ent, final String collection, AsyncOperationCallback<T> c, final String... fields) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(ent.getClass(), collection, new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.updateUsingFields(ent, collection, callback, fields);
                morphium.firePreUpdateEvent(ent.getClass(), MorphiumStorageListener.UpdateTypes.SET);
                Query<Object> query = morphium.createQueryFor(ent.getClass()).f(morphium.getARHelper().getIdFieldName(ent)).eq(morphium.getARHelper().getId(ent));
                if (collection != null)
                    query.setCollectionName(collection);
//                BulkRequestWrapper r = ctx.addFind(query);
                String[] flds = fields;
                if (flds.length == 0) {
                    flds = morphium.getARHelper().getAllFields(ent.getClass()).toArray(flds);
                }

                UpdateBulkRequest r = new UpdateBulkRequest();
                r.setMultiple(false);
                r.setUpsert(false);
                r.setQuery(query.toQueryObject());
                Map<String, Object> set = new HashMap<String, Object>();
                r.setCmd(morphium.getMap("$set", set));
                for (String f : flds) {
                    String fld = morphium.getARHelper().getFieldName(query.getType(), f);
                    set.put(fld, morphium.getARHelper().getValue(ent, f));
                }
                morphium.getCache().clearCacheIfNecessary(ent.getClass());
                morphium.firePostUpdateEvent(ent.getClass(), MorphiumStorageListener.UpdateTypes.SET);

            }
        }, c, AsyncOperationType.UPDATE);
    }

    @Override
    public <T> void set(final T toSet, final String collection, final String field, final Object value, final boolean upsert, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(toSet.getClass(), collection, new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.set(toSet, collection, field, value, upsert, multiple, callback);
                morphium.firePreUpdateEvent(toSet.getClass(), MorphiumStorageListener.UpdateTypes.SET);
                Query<Object> query = morphium.createQueryFor(toSet.getClass()).f(morphium.getARHelper().getIdFieldName(toSet)).eq(morphium.getARHelper().getId(toSet));
                if (collection != null) query.setCollectionName(collection);
                UpdateBulkRequest wr = new UpdateBulkRequest();
                wr.setUpsert(upsert);
                wr.setMultiple(multiple);
                wr.setQuery(query.toQueryObject());
                morphium.getCache().clearCacheIfNecessary(toSet.getClass());
                String fld = morphium.getARHelper().getFieldName(query.getType(), field);
                wr.setCmd(morphium.getMap("$set", morphium.getMap(fld, value)));
                ctx.addRequest(wr);
                morphium.firePostUpdateEvent(toSet.getClass(), MorphiumStorageListener.UpdateTypes.SET);
            }

        }, c, AsyncOperationType.SET);
    }


    @Override
    public <T> void set(final Query<T> query, final Map<String, Object> values, final boolean upsert, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(query.getType(), query.getCollectionName(), new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.set(toSet, collection, field, value, upsert, multiple, callback);
                morphium.firePreUpdateEvent(query.getType(), MorphiumStorageListener.UpdateTypes.SET);

                UpdateBulkRequest wr = new UpdateBulkRequest();
                wr.setUpsert(upsert);
                wr.setMultiple(multiple);
                morphium.getCache().clearCacheIfNecessary(query.getType());
                wr.setQuery(query.toQueryObject());
                Map<String, Object> set = new HashMap<String, Object>();
                wr.setCmd(morphium.getMap("$set", set));
                for (Map.Entry kv : values.entrySet()) {
                    String fld = morphium.getARHelper().getFieldName(query.getType(), kv.getKey().toString());
                    set.put(fld, kv.getValue());
                }
                ctx.addRequest(wr);
                morphium.firePostUpdateEvent(query.getType(), MorphiumStorageListener.UpdateTypes.SET);
            }

        }, c, AsyncOperationType.SET);
    }

    @Override
    public <T> void inc(final Query<T> query, final Map<String, Number> fieldsToInc, final boolean upsert, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(query.getType(), query.getCollectionName(), new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.set(toSet, collection, field, value, upsert, multiple, callback);
                morphium.firePreUpdateEvent(query.getType(), MorphiumStorageListener.UpdateTypes.INC);
                UpdateBulkRequest wr = new UpdateBulkRequest();
                wr.setQuery(query.toQueryObject());
                wr.setUpsert(upsert);
                Map<String, Object> inc = new HashMap<String, Object>();
                wr.setCmd(morphium.getMap("$inc", inc));
                morphium.getCache().clearCacheIfNecessary(query.getType());
                for (Map.Entry kv : fieldsToInc.entrySet()) {
                    String fld = morphium.getARHelper().getFieldName(query.getType(), kv.getKey().toString());
                    inc.put(fld, (Double) kv.getValue());
                }
                morphium.firePostUpdateEvent(query.getType(), MorphiumStorageListener.UpdateTypes.INC);
            }


        }, c, AsyncOperationType.INC);
    }


    @Override
    public <T> void inc(final Query<T> query, final String field, final Number amount, final boolean upsert, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(query.getType(), query.getCollectionName(), new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.set(toSet, collection, field, value, upsert, multiple, callback);

                UpdateBulkRequest wr = new UpdateBulkRequest();
                String fieldName = morphium.getARHelper().getFieldName(query.getType(), field);
                wr.setCmd(morphium.getMap("$inc", morphium.getMap(fieldName, amount)));
                wr.setUpsert(upsert);
                wr.setMultiple(multiple);
                wr.setQuery(query.toQueryObject());
                morphium.firePreUpdateEvent(query.getType(), MorphiumStorageListener.UpdateTypes.INC);
                morphium.getCache().clearCacheIfNecessary(query.getType());
//                wr.inc(fieldName, amount, multiple);
                ctx.addRequest(wr);
                morphium.firePostUpdateEvent(query.getType(), MorphiumStorageListener.UpdateTypes.INC);
            }


        }, c, AsyncOperationType.INC);
    }

    @Override
    public <T> void inc(final T obj, final String collection, final String field, final Number amount, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        morphium.inc(StatisticKeys.WRITES_CACHED);

        addToWriteQueue(obj.getClass(), collection, new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.set(toSet, collection, field, value, upsert, multiple, callback);
                morphium.firePreUpdateEvent(obj.getClass(), MorphiumStorageListener.UpdateTypes.INC);
                Query q = morphium.createQueryFor(obj.getClass()).f(morphium.getARHelper().getIdFieldName(obj)).eq(morphium.getARHelper().getId(obj));
                q.setCollectionName(collection);
                UpdateBulkRequest wr = new UpdateBulkRequest();
                String fieldName = morphium.getARHelper().getFieldName(obj.getClass(), field);
                wr.setCmd(morphium.getMap("$inc", morphium.getMap(fieldName, amount)));
                wr.setUpsert(false);
                wr.setMultiple(false);
                wr.setQuery(q.toQueryObject());
                morphium.getCache().clearCacheIfNecessary(obj.getClass());
                ctx.addRequest(wr);
                morphium.firePostUpdateEvent(obj.getClass(), MorphiumStorageListener.UpdateTypes.INC);
            }


        }, c, AsyncOperationType.INC);

    }


    @Override
    public <T> void pop(final T obj, final String collection, final String field, final boolean first, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(obj.getClass(), collection, new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.set(toSet, collection, field, value, upsert, multiple, callback);
                morphium.firePreUpdateEvent(obj.getClass(), MorphiumStorageListener.UpdateTypes.POP);
                Query q = morphium.createQueryFor(obj.getClass()).f(morphium.getARHelper().getIdFieldName(obj)).eq(morphium.getARHelper().getId(obj));
                UpdateBulkRequest wr = new UpdateBulkRequest();
                wr.setQuery(q.toQueryObject());
                wr.setUpsert(false);
                wr.setMultiple(false);
                morphium.getCache().clearCacheIfNecessary(obj.getClass());
                String fld = morphium.getARHelper().getFieldName(obj.getClass(), field);
                wr.setCmd(morphium.getMap("$pop", morphium.getMap(fld, first)));
                morphium.firePostUpdateEvent(obj.getClass(), MorphiumStorageListener.UpdateTypes.POP);
            }

        }, c, AsyncOperationType.WRITE);
    }


    @Override
    public void setMorphium(Morphium m) {
        morphium = m;

        directWriter = m.getConfig().getWriter();

        housekeeping = new Thread() {
            @SuppressWarnings("SynchronizeOnNonFinalField")
            public void run() {
                while (running) {
                    try {
                        //processing and clearing write cache...
                        List<Class<?>> localBuffer = new ArrayList<>();
                        synchronized (opLog) {
                            for (Class<?> clz : opLog.keySet()) {
                                localBuffer.add(clz);
                            }


                            for (Class<?> clz : localBuffer) {
                                if (opLog.get(clz) == null || opLog.get(clz).size() == 0) {
                                    continue;
                                }
                                WriteBuffer w = morphium.getARHelper().getAnnotationFromHierarchy(clz, WriteBuffer.class);
                                int size = 0;
                                int timeout = morphium.getConfig().getWriteBufferTime();
//                                WriteBuffer.STRATEGY strategy = WriteBuffer.STRATEGY.JUST_WARN;

                                if (w != null) {
                                    size = w.size();
                                    timeout = w.timeout();
//                                    strategy = w.strategy();
                                }
                                //can't be null
                                if (timeout == -1 && size > 0 && opLog.get(clz).size() < size) {
                                    continue; //wait for buffer to be filled
                                }

                                if (lastRun.get(clz) != null && System.currentTimeMillis() - lastRun.get(clz) < timeout) {
                                    //timeout not reached....
                                    continue;
                                }
                                lastRun.put(clz, System.currentTimeMillis());
                                List<WriteBufferEntry> localQueue;
                                localQueue = opLog.get(clz);
                                opLog.put(clz, new Vector<WriteBufferEntry>());

                                opLog.get(clz).addAll(flushToQueue(localQueue));
                            }


                        }
                    } catch (Exception e) {
                        logger.info("Got exception during write buffer handling!", e);
                    }

                    try {
                        if (morphium != null) {
                            if (morphium.getConfig() == null) {
                                running = false;
                                break;
                            }
                            Thread.sleep(morphium.getConfig().getWriteBufferTimeGranularity());
                        } else {
                            logger.warn("Morphium not set - assuming timeout of 1sec");
                            Thread.sleep(1000);
                        }
                    } catch (InterruptedException e) {
                    }
                }
            }


        };
        housekeeping.setDaemon(true);
        housekeeping.start();

        m.addShutdownListener(new ShutdownListener() {
            @Override
            public void onShutdown(Morphium m) {
                running = false;
                try {
                    long start = System.currentTimeMillis();
                    while (housekeeping.isAlive()) {
                        if (System.currentTimeMillis() - start > 1000) {
                            housekeeping.stop();
                            break;
                        }
                        Thread.sleep(50);
                    }
                } catch (Exception e) {
                    //swallow on shutdown
                }
            }
        });

    }

    @Override
    public <T> void remove(final List<T> lst, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        for (final T obj : lst) {
            remove(obj, null, c);
        }
    }

    @Override
    public <T> void remove(final Query<T> q, boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        final AsyncOperationCallback<T> cb = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(q.getType(), q.getCollectionName(), new BufferedBulkOp() {
                @Override
                public void exec(BulkRequestContext ctx) {
                    morphium.firePreRemoveEvent(q);
                    DeleteBulkRequest r = new DeleteBulkRequest();
                    r.setQuery(q.toQueryObject());
                    ctx.addRequest(r);
                    morphium.firePostRemoveEvent(q);
                }

            }, c, AsyncOperationType.REMOVE);
    }

    @Override
    public <T> void remove(final T o, final String collection, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(o.getClass(), collection, new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
                Query q = morphium.createQueryFor(o.getClass()).f(morphium.getARHelper().getIdFieldName(o.getClass())).eq(morphium.getARHelper().getId(o));
                if (collection != null) q.setCollectionName(collection);
                morphium.firePreRemoveEvent(q);
                DeleteBulkRequest r = new DeleteBulkRequest();
                r.setQuery(q.toQueryObject());
                ctx.addRequest(r);
                morphium.firePostRemoveEvent(q);
            }

        }, c, AsyncOperationType.REMOVE);
    }

    @Override
    public <T> void remove(final Query<T> q, AsyncOperationCallback<T> c) {
        remove(q, true, c);
    }

    @Override
    public <T> void pushPull(final boolean push, final Query<T> q, final String field, final Object value, final boolean upsert, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(q.getType(), q.getCollectionName(), new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
                UpdateBulkRequest r = new UpdateBulkRequest();
                r.setQuery(q.toQueryObject());
                r.setUpsert(upsert);
                r.setMultiple(multiple);

                morphium.getCache().clearCacheIfNecessary(q.getType());
                String fld = morphium.getARHelper().getFieldName(q.getType(), field);
                if (push) {
                    morphium.firePreUpdateEvent(q.getType(), MorphiumStorageListener.UpdateTypes.PUSH);
                    r.setCmd(morphium.getMap("$push", morphium.getMap(field, value)));
                    morphium.firePostUpdateEvent(q.getType(), MorphiumStorageListener.UpdateTypes.PUSH);
                } else {
                    morphium.firePreUpdateEvent(q.getType(), MorphiumStorageListener.UpdateTypes.PULL);
                    r.setCmd(morphium.getMap("$pull", morphium.getMap(field, value)));
                    morphium.firePostUpdateEvent(q.getType(), MorphiumStorageListener.UpdateTypes.PULL);
                }
                ctx.addRequest(r);
            }

        }, c, push ? AsyncOperationType.PUSH : AsyncOperationType.PULL);
    }

    @Override
    public <T> void pushPullAll(final boolean push, final Query<T> q, final String field, final List<?> value, final boolean upsert, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(q.getType(), q.getCollectionName(), new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.pushPull(push, query, field, value, upsert, multiple, callback);


                morphium.getCache().clearCacheIfNecessary(q.getType());
                String fld = morphium.getARHelper().getFieldName(q.getType(), field);
                if (push) {
                    morphium.firePreUpdateEvent(q.getType(), MorphiumStorageListener.UpdateTypes.PUSH);

                    for (Object o : value) {
                        UpdateBulkRequest r = new UpdateBulkRequest();
                        r.setQuery(q.toQueryObject());
                        r.setUpsert(upsert);
                        r.setMultiple(multiple);
                        r.setCmd(morphium.getMap("$push", morphium.getMap(fld, o)));
                        ctx.addRequest(r);
                    }
                    morphium.firePostUpdateEvent(q.getType(), MorphiumStorageListener.UpdateTypes.PUSH);
                } else {
                    morphium.firePreUpdateEvent(q.getType(), MorphiumStorageListener.UpdateTypes.PULL);
                    for (Object o : value) {
                        UpdateBulkRequest r = new UpdateBulkRequest();
                        r.setQuery(q.toQueryObject());
                        r.setUpsert(upsert);
                        r.setMultiple(multiple);
                        r.setCmd(morphium.getMap("$pull", morphium.getMap(fld, o)));
                        ctx.addRequest(r);
                    }
                    morphium.firePostUpdateEvent(q.getType(), MorphiumStorageListener.UpdateTypes.PULL);
                }
            }

        }, c, push ? AsyncOperationType.PUSH : AsyncOperationType.PULL);
    }

    @Override
    public <T> void unset(final T obj, final String collection, final String field, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(obj.getClass(), collection, new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.set(toSet, collection, field, value, upsert, multiple, callback);
                Query q = morphium.createQueryFor(obj.getClass()).f(morphium.getARHelper().getIdFieldName(obj)).eq(morphium.getARHelper().getId(obj));
                if (collection != null) q.setCollectionName(collection);
                UpdateBulkRequest wr = new UpdateBulkRequest();
                wr.setQuery(q.toQueryObject());
                wr.setMultiple(false);
                wr.setUpsert(false);
                String fld = morphium.getARHelper().getFieldName(obj.getClass(), field);
                wr.setCmd(morphium.getMap("$unset", morphium.getMap(fld, "")));
                morphium.getCache().clearCacheIfNecessary(obj.getClass());
                ctx.addRequest(wr);
            }

        }, c, AsyncOperationType.UNSET);
    }

    @Override
    public <T> void unset(final Query<T> query, final String field, final boolean multiple, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(query.getType(), query.getCollectionName(), new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.set(toSet, collection, field, value, upsert, multiple, callback);
                morphium.firePreUpdateEvent(query.getType(), MorphiumStorageListener.UpdateTypes.UNSET);
                UpdateBulkRequest wr = new UpdateBulkRequest();
                wr.setQuery(query.toQueryObject());
                wr.setMultiple(false);
                wr.setUpsert(false);
                String fld = morphium.getARHelper().getFieldName(query.getType(), field);
                wr.setCmd(morphium.getMap("$unset", morphium.getMap(fld, "")));
                ctx.addRequest(wr);
                morphium.getCache().clearCacheIfNecessary(query.getType());
                morphium.firePostUpdateEvent(query.getType(), MorphiumStorageListener.UpdateTypes.UNSET);
            }

        }, c, AsyncOperationType.UNSET);
    }

    @Override
    public <T> void unset(final Query<T> query, AsyncOperationCallback<T> c, final boolean multiple, final String... fields) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(query.getType(), query.getCollectionName(), new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
//                directWriter.set(toSet, collection, field, value, upsert, multiple, callback);
                morphium.firePreUpdateEvent(query.getType(), MorphiumStorageListener.UpdateTypes.UNSET);
                UpdateBulkRequest wr = new UpdateBulkRequest();
                wr.setQuery(query.toQueryObject());
                wr.setMultiple(false);
                wr.setUpsert(false);
                Map<String, Object> unset = new HashMap<>();
                wr.setCmd(morphium.getMap("$unset", unset));
                ctx.addRequest(wr);

//                BulkRequestWrapper wr = ctx.addFind(query);
                for (String f : fields) {
                    String fld = morphium.getARHelper().getFieldName(query.getType(), f);
                    unset.put(fld, "");
                }
                ctx.addRequest(wr);
                morphium.getCache().clearCacheIfNecessary(query.getType());
                morphium.firePostUpdateEvent(query.getType(), MorphiumStorageListener.UpdateTypes.UNSET);
            }


        }, c, AsyncOperationType.UNSET);
    }

    @Override
    public <T> void unset(final Query<T> query, AsyncOperationCallback<T> c, final boolean multiple, final Enum... fields) {
        String flds[] = new String[fields.length];
        int i = 0;
        for (Enum e : fields) {
            flds[i++] = e.name();
        }
        unset(query, c, multiple, flds);
    }

    @Override
    public <T> void dropCollection(final Class<T> cls, final String collection, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(cls, collection, new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
                directWriter.dropCollection(cls, collection, callback);
                morphium.getCache().clearCacheIfNecessary(cls);
            }


        }, c, AsyncOperationType.REMOVE);
    }

    public <T> void ensureIndex(final Class<T> cls, final String collection, final Map<String, Object> index, AsyncOperationCallback<T> c) {
        ensureIndex(cls, collection, index, null, c);
    }

    @Override
    public <T> void ensureIndex(final Class<T> cls, final String collection, final Map<String, Object> index, final Map<String, Object> options, AsyncOperationCallback<T> c) {
        if (c == null) {
            c = new AsyncOpAdapter<>();
        }
        final AsyncOperationCallback<T> callback = c;
        morphium.inc(StatisticKeys.WRITES_CACHED);
        addToWriteQueue(cls, collection, new BufferedBulkOp() {
            @Override
            public void exec(BulkRequestContext ctx) {
                directWriter.ensureIndex(cls, collection, index, options, callback);
            }

        }, c, AsyncOperationType.ENSURE_INDICES);
    }


    @Override
    public int writeBufferCount() {
        int cnt = 0;
        synchronized (opLog) {
            for (List<WriteBufferEntry> lst : opLog.values()) {
                cnt += lst.size();
            }
        }
        return cnt;
    }

    @Override
    public <T> void store(final List<T> lst, AsyncOperationCallback<T> c) {
        store(lst, null, c);
    }

    @Override
    public void flush() {
        List<Class<?>> localBuffer = new ArrayList<>();
        synchronized (opLog) {
            for (Class<?> clz : opLog.keySet()) {
                localBuffer.add(clz);
            }
            for (Class<?> clz : localBuffer) {
                if (opLog.get(clz) == null || opLog.get(clz).size() == 0) {
                    continue;
                }
                opLog.get(clz).addAll(flushToQueue(opLog.get(clz)));
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        onShutdown(morphium);

        super.finalize();
    }

    @Override
    public void onShutdown(Morphium m) {
        logger.info("Stopping housekeeping thread");
        running = false;
        flush();
        try {
            Thread.sleep((morphium.getConfig().getWriteBufferTimeGranularity()));

            if (housekeeping != null) housekeeping.stop();
        } catch (Throwable e) {
        }
    }

    private class WriteBufferEntry {
        private BufferedBulkOp toRun;
        private AsyncOperationCallback cb;
        private AsyncOperationType type;
        private long timestamp;
        private Class entityType;
        private String collection;

        private WriteBufferEntry(Class entitiyType, String collectionName, BufferedBulkOp toRun, long timestamp, AsyncOperationCallback c, AsyncOperationType t) {
            this.toRun = toRun;
            this.timestamp = timestamp;
            this.cb = c;
            this.type = t;
            this.entityType = entitiyType;
            this.collection = collectionName;
        }

        public String getCollectionName() {
            return collection;
        }

        public Class getEntityType() {
            return entityType;
        }

        public void setEntityType(Class entityType) {
            this.entityType = entityType;
        }

        public AsyncOperationType getType() {
            return type;
        }

        public void setType(AsyncOperationType type) {
            this.type = type;
        }

        public AsyncOperationCallback getCb() {
            return cb;
        }

        public void setCb(AsyncOperationCallback cb) {
            this.cb = cb;
        }

        public BufferedBulkOp getToRun() {
            return toRun;
        }

        public void setToRun(BufferedBulkOp toRun) {
            this.toRun = toRun;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    @Override
    public void setMaximumQueingTries(int n) {
        directWriter.setMaximumQueingTries(n);
    }

    @Override
    public void setPauseBetweenTries(int p) {
        directWriter.setPauseBetweenTries(p);
    }

    private class AsyncOpAdapter<T> implements AsyncOperationCallback<T> {

        @Override
        public void onOperationSucceeded(AsyncOperationType type, Query<T> q, long duration, List<T> result, T entity, Object... param) {
        }

        @Override
        public void onOperationError(AsyncOperationType type, Query<T> q, long duration, String error, Throwable t, T entity, Object... param) {

        }
    }

    private interface BufferedBulkOp {
        void exec(BulkRequestContext ctx);
    }
}
