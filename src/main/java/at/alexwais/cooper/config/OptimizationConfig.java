package at.alexwais.cooper.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "cooper.optimization")
@Data
public class OptimizationConfig {

    private OptimizationAlgorithm algorithm;
    private boolean enableColocation;

    public enum OptimizationAlgorithm {
        GA,
        CPLEX
    }

}
