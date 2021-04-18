package at.ac.tuwien.dsg.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ContainerInstance {

    private final ContainerType type;

    private final Service service;

    private final VmInstance vm;

}
