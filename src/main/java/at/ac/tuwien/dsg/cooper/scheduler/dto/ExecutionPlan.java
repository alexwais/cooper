package at.ac.tuwien.dsg.cooper.scheduler.dto;

import at.ac.tuwien.dsg.cooper.domain.ContainerType;
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
    private final OptResult optResult;

    private final List<String> vmsToLaunch;
    private final List<String> vmsToTerminate;

    private final List<Pair<String, ContainerType>> containersToStart;
    private final List<Pair<String, ContainerType>> containersToStop;


    public ExecutionPlan(Allocation targetAllocation) {
        this.isReallocation = true;
        this.targetAllocation = targetAllocation;
        this.drainedTargetAllocation = null;
        this.optResult = null;
        this.vmsToLaunch = null;
        this.vmsToTerminate = null;
        this.containersToStart = null;
        this.containersToStop = null;
    }
    public ExecutionPlan(Allocation targetAllocation, Allocation drainedTargetAllocation) {
        this.isReallocation = true;
        this.targetAllocation = targetAllocation;
        this.drainedTargetAllocation = drainedTargetAllocation;
        this.optResult = null;
        this.vmsToLaunch = null;
        this.vmsToTerminate = null;
        this.containersToStart = null;
        this.containersToStop = null;
    }
    public ExecutionPlan(Allocation targetAllocation, OptResult optResult) {
        this.isReallocation = true;
        this.targetAllocation = targetAllocation;
        this.drainedTargetAllocation = null;
        this.optResult = optResult;
        this.vmsToLaunch = null;
        this.vmsToTerminate = null;
        this.containersToStart = null;
        this.containersToStop = null;
    }

    public ExecutionPlan(boolean isReallocation, Allocation targetAllocation) {
        this.isReallocation = isReallocation;
        this.targetAllocation = targetAllocation;
        this.drainedTargetAllocation = null;
        this.optResult = null;
        this.vmsToLaunch = null;
        this.vmsToTerminate = null;
        this.containersToStart = null;
        this.containersToStop = null;
    }

}
