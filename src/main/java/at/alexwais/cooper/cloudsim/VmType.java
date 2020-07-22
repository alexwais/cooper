package at.alexwais.cooper.cloudsim;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Getter
public class VmType {

    private String name;
    private int cpu;
    private int ram;

}
