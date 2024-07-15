package com.globex.wds.creator;

import org.apache.camel.builder.RouteBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteIdempotentRepository;
import org.apache.camel.component.infinispan.remote.*;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.infinispan.client.hotrod.RemoteCacheManager;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.*;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.Config;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import org.apache.camel.component.jms.JmsComponent;
import java.util.Random;

@ApplicationScoped
public class WebhookCreatorRoute extends RouteBuilder {

  @Inject
  ConnectionFactory connectionFactory;

  @Inject
  @ConfigProperty(name = "quarkus.infinispan-client.hosts")
  String infinispanHost;

  @Inject
  @ConfigProperty(name = "idempotentConsumer.skip.duplicate", defaultValue = "true")
  boolean skipDuplicate;

  InfinispanRemoteIdempotentRepository idempotentRepository = new InfinispanRemoteIdempotentRepository(
      "webhook-creator-idempotency-cache");

  private KafkaManualCommit manualCommit;

  @Override
  public void configure() {

    getContext().addComponent("jms", JmsComponent.jmsComponent(connectionFactory));

    InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
    configuration.setHosts(infinispanHost);
    idempotentRepository.setConfiguration(configuration);

    from(
        "kafka:{{kafka.event.topic.name}}?brokers={{camel.component.kafka.brokers}}&groupId={{kafka.consumer.groupId}}&autoOffsetReset=earliest&autoCommitEnable=false&allowManualCommit=true&breakOnFirstError=true")
        // Unrecoverable Error
        .onException(Exception.class)
        .log(" exception occurred ${exception}")
        .process(exchange -> {
          Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
          cause.printStackTrace(); // This prints the stack trace to standard error
        })
        .end()
        .transacted()
        .process(exchange -> {
          manualCommit = exchange.getIn().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
        })
        .log(
            " Received Order created with  partition: ${header[kafka.PARTITION]} with idempotentKey=${header[idempotentKey]}")
        .setProperty("event", simple("${body}"))
        .setProperty("3scale-admin-portal", simple("{{3scale.admin.portal}}"))
        .setProperty("api-endpoint", simple("{{api.product.webhook.endpoint}}"))

        .idempotentConsumer(simple("${header[idempotentKey]}"), idempotentRepository).skipDuplicate(skipDuplicate)

        .log("Received Order created with orderId: ${header[idempotentKey]}")
        .to("direct:getApplicationsFromCache")
        .log("Cache content=${body}")
        .choice()
        .when(body().isNull())// cache is empty ,get consumer applications using 3scale REST API
        .log(" Cache is empty ,call 3scale API")
        .removeHeaders("*")
        .to("direct:getApplicationsFrom3scale")
        .log("3scale API response with ${body}")
        .setProperty("applications", simple("${body}"))
        .log("putApplicationsInCache ${body}")
        .to("direct:putApplicationsInCache")
        .setBody(exchangeProperty("applications"))
        .unmarshal().json(JsonLibrary.Gson)
        .to("direct:processApplications")
        .endChoice()

        .otherwise()// Cache is not empty
        .unmarshal().json(JsonLibrary.Gson)
        .to("direct:processApplications")
        .endChoice()
        .end()
        .process(exchange -> {
          if (manualCommit != null)
            manualCommit.commit();
        })
        .log("Processing Order event has been completed successfully.");

    from("direct:processApplications")
        .split().jsonpath("$.applications")
        .choice()
        .when()
        .simple(
            "${body[application][state]} == 'live' && ${body[application][webhook-url]} != null && ${body[application][webhook-url]} != ''")
        .log("The state is 'live' and the webhook-url is not empty.  ")
        .removeHeaders("*")
        // send message to amqp queue with event body and headers webhook-url ,user_key,
        .setHeader("macSecret").jsonpath("$.application.user_key", String.class)
        .setHeader("webhookUrl").jsonpath("$.application.webhook-url", String.class)
        .setHeader("apiEndpoint", simple("${exchangeProperty.api-endpoint}"))
        .setHeader("retryCount", constant(0L))// initalize the retryCount
        .setBody(exchangeProperty("event"))
        .to("jms:queue:webhookQueue?disableReplyTo=true")
        .endChoice()
        .end();

    from("direct:getApplicationsFromCache")
        .setHeader(InfinispanConstants.OPERATION, constant(InfinispanOperation.GET))
        .setHeader(InfinispanConstants.KEY, simple("{{3scale.product.id}}"))
        .to("infinispan:3scale-consumer-applications-cache?hosts={{quarkus.infinispan-client.hosts}}");

    from("direct:putApplicationsInCache")
        .setHeader(InfinispanConstants.LIFESPAN_TIME).constant(120L) // Lifespan in seconds
        .setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT).constant(TimeUnit.SECONDS.toString())
        .convertBodyTo(String.class)
        .setHeader(InfinispanConstants.VALUE, body())
        .setHeader(InfinispanConstants.KEY, simple("{{3scale.product.id}}"))
        .setHeader(InfinispanConstants.OPERATION, constant(InfinispanOperation.PUT))
        .to("infinispan:3scale-consumer-applications-cache?hosts={{quarkus.infinispan-client.hosts}}");

    from("direct:getApplicationsFrom3scale")
        .setHeader("Exchange.HTTP_METHOD", constant("GET"))
        .setHeader("Exchange.CONTENT_TYPE", constant("application/json"))
        .setHeader("Exchange.HTTP_QUERY", simple(
            "service_id={{3scale.product.id}}&access_token={{3scale.access.token}}"))
        .toD(
            "${exchangeProperty.3scale-admin-portal}/admin/api/applications.json?bridgeEndpoint=true&throwExceptionOnFailure=true");

  }

}