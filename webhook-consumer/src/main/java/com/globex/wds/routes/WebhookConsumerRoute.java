package  com.globex.wds.routes;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.support.jsse.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.camel.Exchange;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.*;
import io.quarkus.runtime.annotations.RegisterForReflection;
 

 @ApplicationScoped
public class WebhookConsumerRoute extends RouteBuilder{

    @Override
    public void configure() {
        // Define a route that listens for HTTP POST requests on the /webhook endpoint
        
        from("netty-http:http://0.0.0.0:9080/?httpMethodRestrict=POST")
          .log("Received Webhook POST with  payload: ${body}")
          .log("received hmac ${header.X-HMAC-SIGNATURE} macSecret=${{macSecret}}")
          .setProperty("macSecret", simple("{{macSecret}}"))
          .filter(header("X-HMAC-SIGNATURE").isNull())
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400)) // Set HTTP status 400 (Bad Request)
            .setBody(constant("Missing X-HMAC-SIGNATURE header")) // Set response body
            .stop() // Stop further processing
          .end() // End the filter block
          .process(exchange -> {
            String macMessage=exchange.getIn().getHeader("X-HMAC-SIGNATURE", String.class);
            String apiKey=exchange.getProperty("macSecret",String.class);
            if (macMessage==null)
                
                throw new IllegalArgumentException("Stopping the route due to missed macMessage header value");
            
            String body = exchange.getIn().getBody(String.class);
            System.out.println("message body="+body);
            String calcualtedMac=createHmac(apiKey,body);
            System.out.println("calcuated mac="+calcualtedMac);
            System.out.println("mac message recceived in the header="+macMessage);
            // check if calaculated message is equal to mac message then authorize the request otherwide dont process the message
           if (calcualtedMac.equals(macMessage))
           exchange.getIn().setHeader("validMac","true");
           else
           exchange.getIn().setHeader("validMac","false");            
             
        })
          
          .choice()
          .when(header("validMac").isEqualTo("true"))
          .log("valid Mac Message , process your webhook message asynchronously here body= ${body}")
          .to("seda:processWebhook")

          .otherwise()
              .log("Invalid Mac Message")
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
      .end();  
      
      from("seda:processWebhook")
      .log("Processing webhook asynchronously: ${body}")
      .delay(1000) // Simulate some processing delay
      .log("Webhook processing complete");
    }

    private static final String HMAC_ALGO = "HmacSHA256";
    private String createHmac(String secret, String message) {
    try {
        Mac sha256Hmac = Mac.getInstance(HMAC_ALGO);
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
        sha256Hmac.init(secretKey);

        byte[] hash = sha256Hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
        throw new RuntimeException("Failed to generate HMAC", e);
    }
}

}