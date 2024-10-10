package ro.iad.demo;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;

public class MainRouteBuilder extends RouteBuilder {
    @Autowired
    private MainConfiguration configuration;

    @Override
    public void configure() throws Exception {
        onException(Throwable.class)
                .log("${body}");

        restConfiguration()
                .host("0.0.0.0")
                .port(configuration.getPort())
                .component("jetty");

        rest("/api")
                .get("/names/{name}")
                .to("direct:searchName");

        from("direct:searchName")
                .setBody().constant("OK");
                //.simple("You are searching for '{header.name}'");

    }
}
