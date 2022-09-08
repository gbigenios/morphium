package de.caluga.morphium.messaging.jms;

import javax.jms.JMSException;
import javax.jms.Topic;

public class JMSTopic extends JMSDestination implements Topic {
    private String topicName;

    public JMSTopic(String topicName) {
        this.topicName = topicName;
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public String getTopicName() throws JMSException {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }
}
