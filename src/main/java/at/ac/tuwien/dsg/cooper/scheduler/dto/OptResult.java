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


    public OptResult(Model model, SystemMeasures underlyingMeasures, Map<VmInstance, List<ContainerType>> allocationMapping, Long runtimeInMilliseconds) {
        this.underlyingMeasures = underlyingMeasures;
        this.allocation = new Allocation(model, allocationMapping);
        this.runtimeInMilliseconds = runtimeInMilliseconds;
    }

    public OptResult(Model model, SystemMeasures underlyingMeasures, List<Allocation.AllocationTuple> allocationTuples, Long runtimeInMilliseconds) {
        this.underlyingMeasures = underlyingMeasures;
        this.allocation = new Allocation(model, allocationTuples);
        this.runtimeInMilliseconds = runtimeInMilliseconds;
    }

    public OptResult(Model model, SystemMeasures underlyingMeasures, Map<VmInstance, List<ContainerType>> allocationMapping, Float fitness, Long runtimeInMilliseconds) {
        this(model, underlyingMeasures, allocationMapping, runtimeInMilliseconds);
        this.fitness = fitness;
    }

}
