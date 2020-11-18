package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;

public interface Optimizer {

    OptimizationResult optimize(Allocation previousAllocation, SystemMeasures systemMeasures);

}
