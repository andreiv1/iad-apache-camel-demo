package ro.iad.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MainConfiguration {
    @Value("${port}")
    private int port;

    public int getPort() {
        return port;
    }
}
