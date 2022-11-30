package de.caluga.test.mongo.suite.messaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import de.caluga.morphium.messaging.MessageListener;
import de.caluga.morphium.messaging.MessageRejectedException;
import de.caluga.morphium.messaging.Messaging;
import de.caluga.morphium.messaging.Msg;
import de.caluga.test.mongo.suite.base.MorphiumTestBase;
import de.caluga.test.mongo.suite.base.TestUtils;

public class RejectTests extends MorphiumTestBase {
    private boolean gotMessage=false;
    private boolean gotMessage3=false;
    private boolean gotMessage2=false;
    private boolean gotMessage1=false;
   @Test
    public void allRejectedTest() throws Exception {

        Messaging sender = null;
        Messaging rec1 = null;
        Messaging rec2 = null;
        try {
            sender = new Messaging(morphium, 100, false);
            sender.setSenderId("sender");
            rec1 = new Messaging(morphium, 100, false);
            rec1.setSenderId("rec1");
            rec2 = new Messaging(morphium, 100, false);
            rec2.setSenderId("rec2");
            morphium.dropCollection(Msg.class, sender.getCollectionName(), null);
            Thread.sleep(10);
            sender.start();
            rec1.start();
            rec2.start();
            Thread.sleep(2000);


            rec1.addMessageListener(new MessageListener<Msg>() {

                @Override
                public Msg onMessage(Messaging msg, Msg m) throws InterruptedException {
                    gotMessage1=true;
                    throw new MessageRejectedException("rec1 rejected", true);
                }
                
            });

            rec2.addMessageListener(new MessageListener<Msg>() {

                @Override
                public Msg onMessage(Messaging msg, Msg m) throws InterruptedException {
                    gotMessage2=true;
                    throw new MessageRejectedException("rec2 rejected", true);
                }
                
            });

            gotMessage=gotMessage1=gotMessage2=gotMessage3=false;
            Msg m=new Msg("test","value","msg");
            m.setExclusive(true);
            sender.sendMessage(m);
            TestUtils.waitForConditionToBecomeTrue(5000, "Was not received by both listeners?", ()->gotMessage1&&gotMessage2);
            log.info("Both tried processing!");

        } finally {
            if (sender!=null) sender.terminate();
            if (rec1!=null) rec1.terminate();
            if (rec2!=null) rec2.terminate();
        }
        MessageRejectedException ex=new MessageRejectedException("rejected",true,false);
    }

    @Test
    public void testRejectException() {
        MessageRejectedException ex = new MessageRejectedException("rejected", true, true);
        assertTrue(ex.isContinueProcessing());
        assertTrue(ex.isSendAnswer());

        ex = new MessageRejectedException("rejected");
        ex.setSendAnswer(true);
        ex.setContinueProcessing(true);
        assertTrue(ex.isContinueProcessing());
        assertTrue(ex.isSendAnswer());

        ex = new MessageRejectedException("rejected", true);
        assertTrue(ex.isContinueProcessing());
        assertFalse(ex.isSendAnswer());
    }

    @Test
    public void testRejectExclusiveMessage() throws Exception {
        Messaging sender = null;
        Messaging rec1 = null;
        Messaging rec2 = null;
        try {
            sender = new Messaging(morphium, 100, false);
            sender.setSenderId("sender");
            rec1 = new Messaging(morphium, 100, false);
            rec1.setSenderId("rec1");
            rec2 = new Messaging(morphium, 100, false);
            rec2.setSenderId("rec2");
            morphium.dropCollection(Msg.class, sender.getCollectionName(), null);
            Thread.sleep(10);
            sender.start();
            rec1.start();
            rec2.start();
            Thread.sleep(2000);
            final AtomicInteger recFirst = new AtomicInteger(0);

            gotMessage = false;

            rec1.addMessageListener((msg, m) -> {
                if (recFirst.get() == 0) {
                    recFirst.set(1);
                    throw new MessageRejectedException("rejected", true, true);
                }
                gotMessage = true;
                return null;
            });
            rec2.addMessageListener((msg, m) -> {
                if (recFirst.get() == 0) {
                    recFirst.set(1);
                    throw new MessageRejectedException("rejected", true, true);
                }
                gotMessage = true;
                return null;
            });
            sender.addMessageListener((msg, m) -> {
                if (m.getInAnswerTo() == null) {
                    log.error("Message is not an answer! ERROR!");
                    return null;
                } else {
                    log.info("Got answer");
                }
                gotMessage3 = true;
                log.info("Receiver " + m.getSender() + " rejected message");
                return null;
            });


            sender.sendMessage(new Msg("test", "message", "value", 3000000, true));
            while (!gotMessage) {
                Thread.sleep(500);
            }
            assert (gotMessage);
            assert (gotMessage3);
        } finally {
            sender.terminate();
            rec1.terminate();
            rec2.terminate();
        }


    }


    @Test
    public void testRejectMessage() throws Exception {
        Messaging sender = null;
        Messaging rec1 = null;
        Messaging rec2 = null;
        try {
            sender = new Messaging(morphium, 100, false);
            rec1 = new Messaging(morphium, 100, false);
            rec2 = new Messaging(morphium, 500, false);
            morphium.dropCollection(Msg.class, sender.getCollectionName(), null);
            Thread.sleep(10);
            sender.start();
            rec1.start();
            rec2.start();
            Thread.sleep(2000);
            gotMessage1 = false;
            gotMessage2 = false;
            gotMessage3 = false;

            rec1.addMessageListener((msg, m) -> {
                gotMessage1 = true;
                throw new MessageRejectedException("rejected", true, true);
            });
            rec2.addMessageListener((msg, m) -> {
                gotMessage2 = true;
                log.info("Processing message " + m.getValue());
                return null;
            });
            sender.addMessageListener((msg, m) -> {
                if (m.getInAnswerTo() == null) {
                    log.error("Message is not an answer! ERROR!");
                    return null;
                }
                gotMessage3 = true;
                log.info("Receiver rejected message");
                return null;
            });

            sender.sendMessage(new Msg("test", "message", "value"));

        } finally {
            sender.terminate();
            rec1.terminate();
            rec2.terminate();
        }


    }

    
}
