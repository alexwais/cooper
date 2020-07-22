package at.alexwais.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Instance {

    private String id;
    private InstanceType type;

}
