package at.alexwais.cooper.api;

import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.OptResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import java.util.Map;
import java.util.Set;

public interface Optimizer {

    OptResult optimize(Allocation currentAllocation,
                       SystemMeasures systemMeasures,
                       Map<VmInstance, Set<Service>> cachedImages);

}
