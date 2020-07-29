package at.alexwais.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Instance {

    private final String id;

    private final InstanceType type;

}
