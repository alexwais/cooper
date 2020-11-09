package at.alexwais.cooper.scheduler.dto;

import at.alexwais.cooper.domain.ContainerType;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@Data
@RequiredArgsConstructor
public class ExecutionPlan {

    private boolean isReallocation = true;

    private final Allocation targetAllocation;
    private final Allocation drainedTargetAllocation;
    private final OptimizationResult optimizationResult;

    private final List<String> vmsToLaunch;
    private final List<String> vmsToTerminate;

    private final List<Pair<String, ContainerType>> containersToStart;
    private final List<Pair<String, ContainerType>> containersToStop;


    public ExecutionPlan(Allocation targetAllocation) {
        this.isReallocation = true;
        this.targetAllocation = targetAllocation;
        this.drainedTargetAllocation = null;
        this.optimizationResult = null;
        this.vmsToLaunch = null;
        this.vmsToTerminate = null;
        this.containersToStart = null;
        this.containersToStop = null;
    }
    public ExecutionPlan(Allocation targetAllocation, Allocation drainedTargetAllocation) {
        this.isReallocation = true;
        this.targetAllocation = targetAllocation;
        this.drainedTargetAllocation = drainedTargetAllocation;
        this.optimizationResult = null;
        this.vmsToLaunch = null;
        this.vmsToTerminate = null;
        this.containersToStart = null;
        this.containersToStop = null;
    }
    public ExecutionPlan(Allocation targetAllocation, OptimizationResult optimizationResult) {
        this.isReallocation = true;
        this.targetAllocation = targetAllocation;
        this.drainedTargetAllocation = null;
        this.optimizationResult = optimizationResult;
        this.vmsToLaunch = null;
        this.vmsToTerminate = null;
        this.containersToStart = null;
        this.containersToStop = null;
    }

    public ExecutionPlan(boolean isReallocation, Allocation targetAllocation) {
        this.isReallocation = isReallocation;
        this.targetAllocation = targetAllocation;
        this.drainedTargetAllocation = null;
        this.optimizationResult = null;
        this.vmsToLaunch = null;
        this.vmsToTerminate = null;
        this.containersToStart = null;
        this.containersToStop = null;
    }

}
