package at.alexwais.cooper.config;

import lombok.Data;
import org.springframework.util.unit.DataSize;

@Data
public class ContainerConfigurationConfig {

    private String label;

    private Integer cpuShares;

    private DataSize memory; // MB

    private Long rpmCapacity;

}
