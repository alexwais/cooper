package at.alexwais.cooper.scheduler.dto;

import at.alexwais.cooper.domain.Allocation;
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

    private Model model;

    private Allocation allocation;

    private Float fitness;

    private Long runtimeInMilliseconds;


    public OptimizationResult(Model model, Map<VmInstance, List<ContainerType>> allocationMapping) {
        this.model = model;
        this.allocation = new Allocation(model, allocationMapping);
    }

    public OptimizationResult(Model model, Map<VmInstance, List<ContainerType>> allocationMapping, Float fitness, Long runtimeInMilliseconds) {
        this(model, allocationMapping);
        this.fitness = fitness;
        this.runtimeInMilliseconds = runtimeInMilliseconds;
    }

}
