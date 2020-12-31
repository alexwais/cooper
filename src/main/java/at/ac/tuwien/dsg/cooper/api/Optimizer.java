package at.ac.tuwien.dsg.cooper.api;

import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.OptResult;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import java.util.Map;
import java.util.Set;

public interface Optimizer {

    OptResult optimize(Allocation currentAllocation,
                       SystemMeasures systemMeasures,
                       Map<VmInstance, Set<Service>> cachedImages);

}
