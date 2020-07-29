package at.alexwais.cooper.config;

import java.util.List;
import lombok.Data;


@Data
public class ServiceConfig {

    private List<ContainerConfigurationConfig> containerConfigurations;

}
