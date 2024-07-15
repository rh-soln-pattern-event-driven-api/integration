package  com.globex.wds.routes;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.camel.Exchange;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.component.netty.http.NettyHttpConfiguration;
import org.apache.camel.component.netty.http.NettyHttpComponent;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

 
 
 
 

 @ApplicationScoped
public class CamelProxyRoute extends RouteBuilder{

    private static final String HMAC_ALGO = "HmacSHA256";
 
    
    @Override
    public void configure() throws Exception {
        onException(Throwable.class) 
        .handled(true) 
        .process(exchange -> {
            Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
            log.error("Exception caught in CamelProxy route: ", exception);
    });
    
   File keystoreFile = new File("/etc/ssl/keystore.jks");
   getContext().getRegistry().bind("keystoreFile", keystoreFile);
 
 
 
 
    from("netty-http:proxy://0.0.0.0:9443?ssl=true&throwExceptionOnFailure=false&bridgeErrorHandler=false"
     + "&keyStoreFile=#keystoreFile"
     + "&passphrase=changeit"
    + "&trustStoreFile=#keystoreFile"
    )
   
        .filter(header("webhook-url").isNull())
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400)) // Set HTTP status 400 (Bad Request)
            .setBody(constant("Missing webhook-url header")) // Set response body
            .stop() // Stop further processing
        .end() // End the filter block
        .filter(header("user_key").isNull())
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400)) // Set HTTP status 400 (Bad Request)
            .setBody(constant("Missing user_key header")) // Set response body
            .stop() // Stop further processing
        .end() // End the filter block
        .log( "Incoming headers: ${headers.webhook-url}")
        .log( "Incoming   body: ${body}")
        //get HMAC secret from Header which sent by Dispatcher
        //createHMAC("password",body)
        //set HMAC in header
        .process(exchange -> {
            String body = exchange.getIn().getBody(String.class);
            String mac=createHmac(exchange.getIn().getHeader("user_key", String.class),body);
           // System.out.println("mac="+mac);
            exchange.getIn().setHeader("X-HMAC-SIGNATURE", mac);
        })
        .toD("netty-http:"
        + "${headers.webhook-url}"
        + "?synchronous=true&throwExceptionOnFailure=false&bridgeEndpoint=false")
    ;  
 
  }

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