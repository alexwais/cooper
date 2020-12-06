package at.alexwais.cooper.stuff.cloudsim;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class VmType {

    private String name;
    private int cpu;
    private int ram;

}
