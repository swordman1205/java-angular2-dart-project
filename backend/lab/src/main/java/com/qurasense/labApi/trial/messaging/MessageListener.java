package com.qurasense.labApi.trial.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.ServiceOptions;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.pubsub.v1.*;
import com.qurasense.common.messaging.MessagingConstants;
import com.qurasense.common.messaging.QurasenseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;

@Component("trialMessageListener")
@Profile({"cloud", "emulator"})
public class MessageListener {

    @Autowired
    private ObjectMapper objectMapper;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String PROJECT_ID = ServiceOptions.getDefaultProjectId();

    private MessageReceiver receiver = new MessageReceiver() {
        @Override
        public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
            try {
                String jsonString = message.getData().toStringUtf8();
                QurasenseMessage msg = objectMapper.readerFor(QurasenseMessage.class).readValue(jsonString);
                if (msg.getType() == QurasenseMessage.QurasenseMessageType.USER_CREATED) {
                    msg.getUserId();
                    //TODO: persist
                } else if (msg.getType() == QurasenseMessage.QurasenseMessageType.USER_AUTHENTICATED) {
                    //just example, this case should be handled in sync way
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            consumer.ack();
        }
    };
    private Subscriber subscriber;

    @PostConstruct
    protected void init() throws Exception {
        // Create a new subscription
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(PROJECT_ID, MessagingConstants.HEALTH_AUTH_SUBSCRIPTION_NAME);

        try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
            // eg. projectId = "my-test-project", topicId = "my-test-topic"
            ProjectTopicName topicName = ProjectTopicName.of(PROJECT_ID, MessagingConstants.HEALTH_AUTH_TOPIC_NAME);
            // eg. subscriptionId = "my-test-subscription"
            // create a pull subscription with default acknowledgement deadline
            Subscription subscription =
                    subscriptionAdminClient.createSubscription(
                            subscriptionName, topicName, PushConfig.getDefaultInstance(), 0);
        } catch (Exception e) {
            //subscription already exist
        }

        subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
        subscriber.startAsync();
    }

    @PreDestroy
    protected void tearDown() throws Exception {
        if (subscriber != null) {
            subscriber.stopAsync();
        }
    }
}
