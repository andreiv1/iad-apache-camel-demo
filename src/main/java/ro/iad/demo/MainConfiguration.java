package ro.iad.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MainConfiguration {
    @Value("${port}")
    private int port;

    @Value("${cache.folder}")
    private String cacheFolder;

    @Value("${backend.endpoint}")
    private String backendEndpoint;

    @Value("${backend.query}")
    private String backendQuery;

    @Value("${location.endpoint}")
    private String locationEndpoint;

    @Value("${location.query}")
    private String locationQuery;

    public int getPort() {
        return port;
    }

    public String getCacheFolder() {
        return cacheFolder;
    }

    public String getBackendEndpoint() {
        return backendEndpoint;
    }

    public String getBackendQuery() {
        return backendQuery;
    }

    public String getLocationQuery() {
        return locationQuery;
    }

    public void setLocationQuery(String locationQuery) {
        this.locationQuery = locationQuery;
    }

    public String getLocationEndpoint() {
        return locationEndpoint;
    }

    public void setLocationEndpoint(String locationEndpoint) {
        this.locationEndpoint = locationEndpoint;
    }
}
