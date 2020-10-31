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

    private final OptimizationResult optimizationResult;

    private final List<String> vmsToLaunch;
    private final List<String> vmsToTerminate;

    private final List<Pair<String, ContainerType>> containersToStart;
    private final List<Pair<String, ContainerType>> containersToStop;


    public ExecutionPlan(boolean isReallocation) {
        this.isReallocation = isReallocation;
        this.optimizationResult = null;
        this.vmsToLaunch = null;
        this.vmsToTerminate = null;
        this.containersToStart = null;
        this.containersToStop = null;
    }

}
