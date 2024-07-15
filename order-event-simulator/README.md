# order-event-simulator

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/wds-creator-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

## Related Guides

- OpenShift ([guide](https://quarkus.io/guides/deploying-to-openshift)): Generate OpenShift resources from annotations
- Camel Core ([guide](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel/4.4/html/red_hat_build_of_apache_camel_for_quarkus_reference/camel-quarkus-extensions-reference#extensions-core)): Camel core functionality and basic Camel languages: Constant, ExchangeProperty, Header, Ref, Simple and Tokenize
- Camel Kafka ([guide](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel/4.4/html/red_hat_build_of_apache_camel_for_quarkus_reference/camel-quarkus-extensions-reference#extensions-kafka)): Sent and receive messages to/from an Apache Kafka broker
- Camel MicroProfile Health ([guide](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel/4.4/html/red_hat_build_of_apache_camel_for_quarkus_reference/camel-quarkus-extensions-reference#extensions-microprofile-health)): Expose Camel health checks via MicroProfile Health
- Camel Infinispan ([guide](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel/4.4/html/red_hat_build_of_apache_camel_for_quarkus_reference/camel-quarkus-extensions-reference#extensions-infinispan)): Read and write from/to Infinispan distributed key/value store and data grid
- Camel HTTP ([guide](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel/4.4/html/red_hat_build_of_apache_camel_for_quarkus_reference/camel-quarkus-extensions-reference#extensions-http)): Send requests to external HTTP servers using Apache HTTP Client 5.x
