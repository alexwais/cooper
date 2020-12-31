package at.ac.tuwien.dsg.cooper.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VmType {

    private final String label;

    private final Integer cpuCores;

    private final Integer memory; // MB

    private final Float cost; // USD

    private final DataCenter dataCenter;

    public Integer getCpuUnits() {
        return this.cpuCores * 1024;
    }

}
