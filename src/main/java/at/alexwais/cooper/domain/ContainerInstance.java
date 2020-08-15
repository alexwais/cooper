package at.alexwais.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ContainerInstance {

//    private final String id;

    private final ContainerType configuration;

    private final Service service;

    private final VmInstance vm;

}
