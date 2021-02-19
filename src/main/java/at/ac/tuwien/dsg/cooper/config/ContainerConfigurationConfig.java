package at.ac.tuwien.dsg.cooper.config;

import lombok.Data;
import org.springframework.util.unit.DataSize;

@Data
public class ContainerConfigurationConfig {

    private String label;

    private Integer cpuUnits;

    private DataSize memory; // MB

    private Long rpmCapacity;

}
