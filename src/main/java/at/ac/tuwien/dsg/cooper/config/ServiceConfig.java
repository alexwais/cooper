package at.ac.tuwien.dsg.cooper.config;

import java.util.List;
import java.util.Map;
import lombok.Data;


@Data
public class ServiceConfig {

    private Map<String, Float> downstreamServices;
    private List<ContainerConfigurationConfig> containerConfigurations;

}
