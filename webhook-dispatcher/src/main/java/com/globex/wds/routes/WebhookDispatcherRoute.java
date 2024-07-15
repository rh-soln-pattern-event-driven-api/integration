package  com.globex.wds.routes;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.component.jms.JmsComponent;

 @ApplicationScoped
public class WebhookDispatcherRoute extends RouteBuilder{

    @Inject
    ConnectionFactory connectionFactory;


    @Override
    public void configure() {  
        getContext().addComponent("jms", JmsComponent.jmsComponent(connectionFactory));
  
        from("jms:queue:webhookQueue?transacted=true")//connectionFactory=#mqConnectionFactory&
    //  from("amqp:queue:webhookQueue?maxConcurrentConsumers=10") //exchangePattern=InOnly&maxConcurrentConsumers=1
                
                //Unrecoverable Error
                //.onException(Exception.class)
                  //      .log(" exception occurred ${exception}")
                        //.handled(true)
                    //    .to("direct:deadLetterChannel")
                //.end()
                .transacted()
                .log("Received Webhook request with payload ${body}   header macSecret=${headers.macSecret} , webhookUrl=${headers.webhookUrl} , retryCount=${headers.retryCount} , 3scale-endpoint=${headers.apiEndpoint}")
                .to("direct:setProperties")
                .to("direct:callConsumerWebhook")
                .choice()
                    .when(simple("${header.CamelHttpResponseCode} startsWith '2'"))
                       .log("consumer Webhook respone with 2XX   , ${header.CamelHttpResponseCode}")
                    .otherwise()
                      .to("direct:retryHandler") 
                .endChoice()
        ;

        from("direct:callConsumerWebhook")
                .removeHeaders("*")
                .setHeader("Exchange.HTTP_METHOD", constant("POST"))
                .setHeader("Exchange.CONTENT_TYPE", constant("application/json"))
                .setHeader("user_key", exchangeProperty("macSecret"))
                .setHeader("webhook-url", exchangeProperty("webhookUrl"))
                //set timeout for webhook response to 30 seconds
                .toD("${exchangeProperty.apiEndpoint}?throwExceptionOnFailure=false&bridgeEndpoint=false&connectTimeout=30000&socketTimeout=30000")
        ;   

        from("direct:setProperties")
            .setProperty("macSecret", simple("${headers.macSecret}"))
            .setProperty("webhookUrl", simple("${headers.webhookUrl}"))
            .setProperty("apiEndpoint", simple("${headers.apiEndpoint}"))
            .setProperty("max.retry", simple("{{max.retry}}"))
            .setProperty("retry.interval", simple("{{retry.interval}}"))
            .setProperty("notification", simple("${body}"))
            .setProperty("retryCount", simple("${headers.retryCount}")) 
        ;
        from("direct:retryHandler")
              .log("Failed to send to Webhook with responsecode= ${header.CamelHttpResponseCode}")
              .removeHeaders("*")
              .setHeader("macSecret",exchangeProperty("macSecret"))
              .setHeader("webhookUrl",exchangeProperty("webhookUrl"))
              .setHeader("apiEndpoint",exchangeProperty("apiEndpoint"))
              
              .setBody(exchangeProperty("notification"))
              .process(exchange -> {
                    Long maxRetry=exchange.getProperty("max.retry",Long.class);
                    Long retyInterval=exchange.getProperty("retry.interval",Long.class);
                    Long retryCount=exchange.getProperty("retryCount", Long.class);
                    if (retryCount<maxRetry){
                          retryCount=retryCount+1;
                          // exponential backoff formula
                          long deliveryDelay = 1000*(retyInterval+(int)Math.pow(retryCount,4));  //Math.min(INITIAL_DELAY * (2*retryCount),MAX_DELAY);
                          exchange.getIn().setHeader("retryCount",retryCount.toString());
                          exchange.setProperty("queueName", "webhookQueue");
                          exchange.setProperty("deliveryDelay", ""+deliveryDelay);
                    } else {
                          exchange.setProperty("queueName", "webhookQueueDLQ");
                          exchange.setProperty("deliveryDelay", "0");
                          } 
              })
              .log("Sending message to queue=${exchangeProperty.queueName} with deliveryDelay=${exchangeProperty.deliveryDelay}")
              .toD("jms:queue:${exchangeProperty.queueName}?deliveryDelay=${exchangeProperty.deliveryDelay}")
        ;
        from("direct:deadLetterChannel")
            .log("Failed message: ${body}")
            .to("jms:queue:webhookQueueDLQ")
        ;

    }
 

}