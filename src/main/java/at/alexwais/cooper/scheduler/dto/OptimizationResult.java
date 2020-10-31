package at.alexwais.cooper.scheduler.dto;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.ActivityMeasures;
import at.alexwais.cooper.scheduler.Model;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class OptimizationResult {

    private Allocation allocation;
    private ActivityMeasures measures;
    private Float fitness;
    private Long runtimeInMilliseconds;


    public OptimizationResult(Model model, ActivityMeasures measures, Map<VmInstance, List<ContainerType>> allocationMapping) {
        this.measures = measures;
        this.allocation = new Allocation(model, allocationMapping);
    }

    public OptimizationResult(Model model, ActivityMeasures measures, List<Allocation.AllocationTuple> allocationTuples) {
        this.measures = measures;
        this.allocation = new Allocation(model, allocationTuples);
    }

    public OptimizationResult(Model model, ActivityMeasures measures, Map<VmInstance, List<ContainerType>> allocationMapping, Float fitness, Long runtimeInMilliseconds) {
        this(model, measures, allocationMapping);
        this.fitness = fitness;
        this.runtimeInMilliseconds = runtimeInMilliseconds;
    }

}
