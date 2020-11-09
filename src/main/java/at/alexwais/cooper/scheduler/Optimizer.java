package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;

public interface Optimizer {

    OptimizationResult optimize(Allocation previousAllocation, SystemMeasures systemMeasures);

}
