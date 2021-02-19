package at.ac.tuwien.dsg.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ContainerType {

    private String label;

    private Integer cpuUnits;

    private Integer memory; // MB

    private Long rpmCapacity;

    private final Service service;

}
