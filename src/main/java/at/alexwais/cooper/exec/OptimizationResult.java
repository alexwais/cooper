package at.alexwais.cooper.exec;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import java.util.List;
import java.util.Map;
import lombok.*;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class OptimizationResult {

    private Map<String, Boolean> vmAllocation;
//    private Map<String, List<String>> containerAllocation; // VM IDs to a List of Container Types

    private List<AllocationTuple> containerAllocation;

    private Float fitness;

    @Getter
    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class AllocationTuple {
        private final VmInstance vm;
        private final ContainerType type;
        private final boolean allocate;
    }

}
