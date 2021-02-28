package at.ac.tuwien.dsg.cooper.scheduler.dto;

import at.ac.tuwien.dsg.cooper.domain.ContainerType;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class OptResult {

    private Allocation allocation;
    private SystemMeasures underlyingMeasures;
    private Float fitness;
    private Float neutralFitness;
    private Long runtimeInMilliseconds;


    public OptResult(Model model, SystemMeasures underlyingMeasures, Map<VmInstance, List<ContainerType>> allocationMapping) {
        this.underlyingMeasures = underlyingMeasures;
        this.allocation = new Allocation(model, allocationMapping);
    }

    public OptResult(Model model, SystemMeasures underlyingMeasures, List<Allocation.AllocationTuple> allocationTuples) {
        this.underlyingMeasures = underlyingMeasures;
        this.allocation = new Allocation(model, allocationTuples);
    }

}
