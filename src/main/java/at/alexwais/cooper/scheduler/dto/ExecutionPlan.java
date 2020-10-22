package at.alexwais.cooper.scheduler.dto;

import at.alexwais.cooper.domain.Allocation;
import at.alexwais.cooper.domain.ContainerType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

@Data
@AllArgsConstructor
public class ExecutionPlan {

    private Allocation targetAllocation;

    private List<String> vmsToLaunch;
    private List<String> vmsToTerminate;

    private List<Pair<String, ContainerType>> containersToStart;
    private List<Pair<String, ContainerType>> containersToStop;

}
