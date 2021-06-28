package de.caluga.morphium.messaging.jms;

import javax.jms.JMSException;
import javax.jms.TextMessage;

public class JMSTextMessage extends JMSMessage implements TextMessage {
    @SuppressWarnings("RedundantThrows")
    @Override
    public String getText() throws JMSException {
        return getValue();
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public void setText(String string) throws JMSException {
        setValue(string);
    }
}
