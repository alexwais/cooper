package at.alexwais.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ContainerInstance {

    private final String id;

    private final ContainerConfiguration configuration;

    private final Service service;

}
