package at.alexwais.cooper.config;

import lombok.Data;
import org.springframework.util.unit.DataSize;

@Data
public class InstanceTypeConfig {

    private String label;

    private Integer cpuCores;

    private DataSize memory; // MB

    private Float cost = 0f; // USD; 0 in case of on-premise instances

    private Integer count = null; // in case of on-premise instances

}
