package de.caluga.morphium.messaging.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;

public class JMSConnectionConsumer implements ConnectionConsumer {
    @SuppressWarnings("RedundantThrows")
    @Override
    public ServerSessionPool getServerSessionPool() throws JMSException {
        throw new IllegalArgumentException("not implemented yet, sorry");
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public void close() throws JMSException {

    }
}
