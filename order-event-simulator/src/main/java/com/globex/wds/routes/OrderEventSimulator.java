package  com.globex.wds.routes;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderEventSimulator extends RouteBuilder{
  @Override
  public void configure() throws Exception {
    from("platform-http:/?httpMethodRestrict=POST")
            .wireTap("seda:produceOrders")
            .setBody(simple("Request received"));

    from("seda:produceOrders")
            .log(" produceOrders has been started")
            .removeHeaders("*")
            .log(" order.json content= ${body}")
            .split().jsonpathWriteAsString("$.orders[*]")
                .log(" inside split ${body}")
                .unmarshal().json(JsonLibrary.Gson)
                .setHeader("idempotentKey",simple("${body[orderId]}"))
                .marshal().json(JsonLibrary.Gson)
                .log(" inside split after marshal ${body}")
                .to("kafka:order-created-event?brokers={{camel.component.kafka.brokers}}")
                .log(" message has been sent to kafka topic")
            .end();
}
 
}