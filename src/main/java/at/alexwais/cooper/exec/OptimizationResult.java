package at.alexwais.cooper.exec;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.*;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class OptimizationResult {

    private Model model;

    private Map<VmInstance, List<ContainerType>> allocation;


    public List<OptimizationResult.AllocationTuple> getTuples() {
        List<OptimizationResult.AllocationTuple> resultTuples = new ArrayList<>();
        model.getVms().values().forEach(vm -> {
            model.getContainerTypes().forEach(type -> {
                var containerList = allocation.getOrDefault(vm, Collections.emptyList());
                var allocate = containerList.contains(type);
                var tuple = new OptimizationResult.AllocationTuple(vm, type, allocate);
                resultTuples.add(tuple);
            });
        });
        return resultTuples;
    }

    @Getter
    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class AllocationTuple {
        private final VmInstance vm;
        private final ContainerType type;
        private final boolean allocate;
    }

}
