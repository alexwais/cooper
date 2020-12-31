package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.AnalysisResult;
import at.alexwais.cooper.scheduler.dto.OptResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private OptResult lastOptResult;

    // Load measured by Monitor
    @Setter
    private SystemMeasures currentSystemMeasures;

    @Setter
    private boolean inGracePeriod = false;

    @Getter
    private ProviderState providerState = new ProviderState();

    @Getter
    private Map<VmInstance, Set<Service>> imageCacheState = new HashMap<>(); // TODO use set everywhere applicable

    @Getter
    private long imageDownloads = 0L;


    public Pair<Integer, Integer> getFreeCapacity(VmInstance vm) {
        if (!currentTargetAllocation.getRunningVms().contains(vm)) {
            return null;
        }

        var allocatedContainers = providerState.getRunningContainersByVm(vm);

        var allocatedCpuCapacity = allocatedContainers == null ? 0 : allocatedContainers.stream()
                .map(a -> a.getContainer().getCpuShares())
                .reduce(0, Integer::sum);
        var allocatedMemoryCapacity = allocatedContainers == null ? 0 : allocatedContainers.stream()
                .map(a -> a.getContainer().getMemory())
                .reduce(0, Integer::sum);

        var freeCpuCapacity = vm.getType().getCpuUnits() - allocatedCpuCapacity;
        var freeMemoryCapacity = vm.getType().getMemory() - allocatedMemoryCapacity;

        return Pair.of(freeCpuCapacity, freeMemoryCapacity);
    }


    public void resetCacheState(VmInstance vm) {
        imageCacheState.get(vm).clear();
    }

    public void updateCacheState(VmInstance vm, Service service) {
        var list = imageCacheState.getOrDefault(vm, new HashSet<>());
        if (!list.contains(service)) {
            list.add(service);
            imageCacheState.put(vm, list);
            imageDownloads++;
        }
    }

}
