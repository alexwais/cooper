package at.ac.tuwien.dsg.cooper.config;

import java.util.ArrayList;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "distance")
public class DataCenterDistanceConfigList extends ArrayList<DateCenterDistanceConfig> {

}