package at.ac.tuwien.dsg.cooper.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "cooper.optimization")
@Data
public class OptimizationConfig {

    private OptimizationAlgorithm strategy;
    private Float gaLatencyWeight;

    public enum OptimizationAlgorithm {
        GA,
        GA_NC,
        ILP,
        ILP_NC,
        FF
    }

}
