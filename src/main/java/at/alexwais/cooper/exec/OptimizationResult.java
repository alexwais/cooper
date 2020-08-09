package at.alexwais.cooper.exec;

import at.alexwais.cooper.domain.ContainerConfiguration;
import at.alexwais.cooper.domain.VmInstance;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
public class OptimizationResult {

    private Map<String, Boolean> vmAllocation;
//    private Map<String, List<String>> containerAllocation; // VM IDs to a List of Container Types

    public List<AllocationTuple> containerAllocation;

    @Getter
    @RequiredArgsConstructor
    public static class AllocationTuple {
        private final VmInstance vm;
        private final ContainerConfiguration type;
        private final boolean allocate;
    }

}
