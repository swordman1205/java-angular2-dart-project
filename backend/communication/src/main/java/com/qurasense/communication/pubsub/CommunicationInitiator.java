package com.qurasense.communication.pubsub;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.ServiceOptions;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.Subscription;
import com.qurasense.common.messaging.MessagingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("cloud")
public class CommunicationInitiator {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private CommunicationMessageReceiver messageReceiver;

    private Subscriber subscriber;

    @PostConstruct
    protected void start() throws Exception {
        // Your Google Cloud Platform project ID
        createTopic();
        createSubscriber();
    }

    private void createSubscriber() throws Exception {
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(ServiceOptions.getDefaultProjectId(),
                MessagingConstants.COMMUNICATION_SUBSCRIPTION_NAME);
        // create a subscriber bound to the asynchronous message receiver
        try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
            // eg. projectId = "my-test-project", topicId = "my-test-topic"
            ProjectTopicName topicName = ProjectTopicName.of(ServiceOptions.getDefaultProjectId(),
                    MessagingConstants.COMMUNICATION_TOPIC_NAME);
            // create a pull subscription with default acknowledgement deadline
            Subscription subscription = subscriptionAdminClient.createSubscription(
                            subscriptionName, topicName, PushConfig.getDefaultInstance(), 0);
        } catch (ApiException e) {
            if (e.getStatusCode().getCode() != StatusCode.Code.ALREADY_EXISTS) {
                throw new IllegalStateException(e);
            }
        }
        subscriber = Subscriber.newBuilder(subscriptionName, messageReceiver).build();
        subscriber.startAsync().awaitRunning();
        logger.info("subscriber start receiving messages");
    }

    @PreDestroy
    protected void close() throws Exception {
        logger.info("subscriber stop receiving messages");
        subscriber.stopAsync();
    }

    private void createTopic() throws Exception {
        ProjectTopicName topic = ProjectTopicName.of(ServiceOptions.getDefaultProjectId(), MessagingConstants.COMMUNICATION_TOPIC_NAME);
        try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
            topicAdminClient.createTopic(topic);
            logger.info("Topic {}:{} created", topic.getProject(), topic.getTopic());
        } catch (ApiException e) {
            // example : code = ALREADY_EXISTS(409) implies topic already exists
            if (e.getStatusCode().getCode() == StatusCode.Code.ALREADY_EXISTS) {
                logger.info("Topic {}:{} already exist", topic.getProject(), topic.getTopic());
            } else {
                logger.error("Error create topic: {}", e.getStatusCode().getCode());
            }
            logger.info("isRetryable: {}", e.isRetryable());
        }
    }

}
