package at.alexwais.cooper;

import lombok.Data;
import org.springframework.util.unit.DataSize;

@Data
public class InstanceTypeConfig {

    private String label;

    private Integer cpuCores;

    private DataSize memory; // MB

    private Float cost; // USD

}
