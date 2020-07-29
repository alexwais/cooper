package at.alexwais.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.unit.DataSize;

@Getter
@AllArgsConstructor
public class ContainerConfiguration {

    private String label;

    private Integer cpuShares;

    private DataSize memory; // MB

    private Long rpmCapacity;

    private final Service service;

}
