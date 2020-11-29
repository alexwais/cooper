package at.alexwais.cooper.scheduler.dto;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.Model;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class OptimizationResult {

    private Allocation allocation;
    private SystemMeasures underlyingMeasures;
    private Float fitness;
    private Float neutralFitness;
    private Long runtimeInMilliseconds;


    public OptimizationResult(Model model, SystemMeasures underlyingMeasures, Map<VmInstance, List<ContainerType>> allocationMapping, Long runtimeInMilliseconds) {
        this.underlyingMeasures = underlyingMeasures;
        this.allocation = new Allocation(model, allocationMapping);
        this.runtimeInMilliseconds = runtimeInMilliseconds;
    }

    public OptimizationResult(Model model, SystemMeasures underlyingMeasures, List<Allocation.AllocationTuple> allocationTuples, Long runtimeInMilliseconds) {
        this.underlyingMeasures = underlyingMeasures;
        this.allocation = new Allocation(model, allocationTuples);
        this.runtimeInMilliseconds = runtimeInMilliseconds;
    }

    public OptimizationResult(Model model, SystemMeasures underlyingMeasures, Map<VmInstance, List<ContainerType>> allocationMapping, Float fitness, Long runtimeInMilliseconds) {
        this(model, underlyingMeasures, allocationMapping, runtimeInMilliseconds);
        this.fitness = fitness;
    }

}
