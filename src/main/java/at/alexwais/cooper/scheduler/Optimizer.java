package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.scheduler.dto.OptimizationResult;

public interface Optimizer {

    OptimizationResult optimize(State state);

}
