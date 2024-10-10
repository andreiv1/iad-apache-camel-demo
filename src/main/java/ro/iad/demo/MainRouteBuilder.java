package ro.iad.demo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MainRouteBuilder extends RouteBuilder {

    private class Result {
        @JsonIgnore
        private double latitude;

        @JsonIgnore
        private double longitude;

        @JsonProperty
        private int count;

        @JsonProperty
        private String district;

        @JsonIgnore
        private String country;

        public Result(double latitude, double longitude, int count) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.count = count;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public int getCount() {
            return count;
        }

        public String getDistrict() {
            return district;
        }

        public String getCountry() {
            return country;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public void setDistrict(String district) {
            this.district = district;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public void merge(Result result) {
            if(district.equals(result.getDistrict())) {
                count += result.getCount();
            }
        }
    }
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
                .removeHeader("Camel")
                .choice()
                    .when().method(getClass(), "isInCache").to("direct:getFromCache")
                    .otherwise().to("direct:getFromBackend");

        from("direct:getFromCache")
                .setHeader("Content-Type").constant("application/json")
                .setBody().method(getClass(), "readFromCache");


        from("direct:getFromBackend")
                .setHeader("Accept").constant("application/json")
                .setHeader(Exchange.HTTP_QUERY).method(getClass(), "getBackendEndpoint")
                .to(configuration.getBackendEndpoint())
                .convertBodyTo(String.class)
                .unmarshal().json(JsonLibrary.Jackson)
                .bean(getClass(), "parseResults")
                .split().body()
                .parallelProcessing()
                .enrich("direct:resolveLocation", (originalExchange, enrichedExchange) -> {
                    Result result = originalExchange.getIn().getBody(Result.class);
                    Properties properties = enrichedExchange.getIn().getBody(Properties.class);
                    result.setDistrict(properties.getProperty("district"));
                    result.setCountry(properties.getProperty("country"));
                    return originalExchange;
                })
                .filter(exchange -> {
                    var result = exchange.getIn().getBody(Result.class);
                    return !result.getDistrict().isEmpty();
                })
                .aggregate(simple("${body.district"), ((oldExchange, newExchange) -> {
                    Result result = newExchange.getIn().getBody(Result.class);
                    if(oldExchange == null){
                        return newExchange;
                    } else {
                        oldExchange.getIn().getBody(Result.class).merge(result);
                        return oldExchange;
                    }
                }))
                .completionTimeout(3000)
                .filter(exchange -> exchange.getIn().getBody(Result.class).getCount() > 0)
                .resequence(simple("${body.count}"))
                .aggregate(constant(true), ((oldExchange, newExchange) -> {
                    Result result = newExchange.getIn().getBody(Result.class);
                    if(oldExchange == null) {
                        List<Result> results = new ArrayList<Result>();
                        results.add(result);
                        newExchange.getIn().setBody(results);
                        return newExchange;
                    } else {
                        List<Result> results = (List<Result>) oldExchange.getIn().getBody(List.class);
                        results.add(result);
                        return oldExchange;
                    }
                }))
                .completionTimeout(3000)
                .marshal().json(JsonLibrary.Jackson)
                .setHeader("Content-Type").constant("application.json");

        from("direct:resolveLocation")
                .removeHeaders("Camel*")
                .setHeader("Accept").constant("application/json")
                .setHeader(Exchange.HTTP_QUERY).method(getClass(), "getLocationQuery")
                .to(configuration.getLocationQuery())
                .convertBodyTo(String.class)
                .unmarshal().json(JsonLibrary.Jackson)
                .wireTap()
                .bean(getClass(),"resolveLocation");
    }

    private Path resolvePath(String path){
        return Paths.get(configuration.getCacheFolder(), path);
    }
    public boolean isInCache(Exchange exchange) {
        String name = exchange.getIn().getHeader("name", String.class);
        return resolvePath(name + ".json").toFile().exists();
    }

    public String readFromCache(Exchange exchange) throws IOException {
        String name = exchange.getIn().getHeader("name", String.class);
        byte[] bytes = Files.readAllBytes(resolvePath(name + "json"));
        return new String(bytes);
    }

    public String getBackendQuery(Exchange exchange) {
        String name = exchange.getIn().getHeader("name", String.class);
        return MessageFormat.format(configuration.getBackendQuery(), name);
    }

    public List<Result> parseResults(Exchange exchange) {
        List<Result> results = new ArrayList<>();
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        for (var item : (List<Map<String,Object>>) body.get("ani")) {
            int count = (int) item.get("count");
            var area = (Map<String, Object>) item.get("area");
            double latitude = Double.parseDouble((String) area.get("centroid_lat"));
            double longitude = Double.parseDouble((String) area.get("centroid_lng"));
            results.add(new Result(latitude, longitude, count));
        }

        return results;
    }


    public String getLocationQuery(Exchange exchange) {
        Result result = exchange.getIn().getBody(Result.class);
        return MessageFormat.format("bridgeEndpoint=true" + configuration.getLocationQuery(),
                Double.toString(result.getLatitude()), Double.toString(result.getLongitude()));
    }

    public Properties resolveLocation(Exchange exchange) {
        Properties properties = new Properties();
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        var address = (Map<String, Object>) body.get("address");
        var district = address.get("county");

        if(district == null) {
            district = "";
        }

        var country = (String) address.get("country");

        if(country == null) {
            country = "";
        }

        properties.put("district", district);
        properties.put("country", country);
        return properties;
    }
}
