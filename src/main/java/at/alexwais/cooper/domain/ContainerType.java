package at.alexwais.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.util.unit.DataSize;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ContainerType {

    private String label;

    private Integer cpuShares;

    private DataSize memory; // MB

    private Long rpmCapacity;

    private final Service service;

}
