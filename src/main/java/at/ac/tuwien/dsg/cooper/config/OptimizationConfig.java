package at.ac.tuwien.dsg.cooper.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "cooper.optimization")
@Data
public class OptimizationConfig {

    private OptimizationAlgorithm strategy;

    public enum OptimizationAlgorithm {
        GA,
        GA_C,
        ILP,
        ILP_C,
        FF
    }

}
