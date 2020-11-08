package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.AnalysisResult;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

@Getter
@RequiredArgsConstructor
public class State {

    private final Model model;

    @Setter
    private Allocation currentTargetAllocation; // may differ from the lastOptimizationResult's allocation during the grace period
    @Setter
    private AnalysisResult currentAnalysisResult;
    @Setter
    private OptimizationResult lastOptimizationResult;

    // Load measured by Monitor
    @Setter
    private SystemMeasures currentSystemMeasures;

    @Setter
    private boolean inGracePeriod = false;

    @Getter
    private ProviderState providerState = new ProviderState();


    public Pair<Integer, Long> getFreeCapacity(VmInstance vm) {
        if (!currentTargetAllocation.getRunningVms().contains(vm)) {
            return null;
        }

        var allocatedContainers = providerState.getRunningContainersByVm(vm);

        var allocatedCpuCapacity = allocatedContainers == null ? 0 : allocatedContainers.stream()
                .map(a -> a.getContainer().getCpuShares())
                .reduce(0, Integer::sum);
        var allocatedMemoryCapacity = allocatedContainers == null ? 0 : allocatedContainers.stream()
                .map(a -> a.getContainer().getMemory().toMegabytes())
                .reduce(0L, Long::sum);

        var freeCpuCapacity = vm.getType().getCpuCores() * 1024 - allocatedCpuCapacity;
        var freeMemoryCapacity = vm.getType().getMemory() - allocatedMemoryCapacity;

        return Pair.of(freeCpuCapacity, freeMemoryCapacity);
    }

}
