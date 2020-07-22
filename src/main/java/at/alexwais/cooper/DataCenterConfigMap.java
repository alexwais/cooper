package at.alexwais.cooper;

import java.util.HashMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "data-centers")
public class DataCenterConfigMap extends HashMap<String, DataCenterConfig> {
     

}