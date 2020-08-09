package at.alexwais.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class VmInstance {

    private final String id;

    private final VmType type;

}
