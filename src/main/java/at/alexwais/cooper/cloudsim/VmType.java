package at.alexwais.cooper.cloudsim;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class VmType {

    private String name;
    private int cpu;
    private int ram;

}
