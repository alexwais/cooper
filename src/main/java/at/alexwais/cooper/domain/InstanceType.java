package at.alexwais.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InstanceType {

    private String label;

    private Integer cpuCores;

    private Integer memory; // MB

    private Float cost; // USD

}
