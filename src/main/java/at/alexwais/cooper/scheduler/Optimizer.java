package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import java.util.Map;
import java.util.Set;

public interface Optimizer {

    OptimizationResult optimize(Allocation previousAllocation, SystemMeasures systemMeasures, Map<VmInstance, Set<Service>> imageCacheState);

}
