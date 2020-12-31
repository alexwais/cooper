package at.ac.tuwien.dsg.cooper.config;

import java.util.HashMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "services")
public class ServiceConfigMap extends HashMap<String, ServiceConfig> {
     

}