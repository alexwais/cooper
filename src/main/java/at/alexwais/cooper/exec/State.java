package at.alexwais.cooper.exec;

import at.alexwais.cooper.domain.ContainerInstance;
import at.alexwais.cooper.domain.Instance;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@Getter
@RequiredArgsConstructor
public class State {

    private final Model model;

    private Map<String, Long> serviceLoad = new HashMap<>();

    private final Map<String, List<ContainerInstance>> vmContainerAllocation = new HashMap<>();
    private final Map<String, String> containerVmAllocation = new HashMap<>();

    private final Map<String, Long> leasedProviderVms = new HashMap<>();
    private final Map<String, Long> runningProviderContainers = new HashMap<>();

    public List<Instance> getLeasedVms() {
        return this.leasedProviderVms.keySet().stream()
                .map(k -> model.getVms().get(k))
                .collect(Collectors.toList());
    }

    public List<ContainerInstance> getRunningContainers() {
        return this.runningProviderContainers.keySet().stream()
                .map(k -> model.getContainers().get(k))
                .collect(Collectors.toList());
    }

    public Pair<Integer, Long> getFreeCapacity(String vmId) {
        if (!leasedProviderVms.containsKey(vmId)) {
            return null;
        }

        var vm = model.getVms().get(vmId);
        var allocatedContainers = vmContainerAllocation.get(vm.getId());

        var allocatedCpuCapacity = allocatedContainers == null ? 0 : allocatedContainers.stream()
                .map(c -> c.getConfiguration().getCpuShares())
                .reduce(0, Integer::sum);
        var allocatedMemoryCapacity = allocatedContainers == null ? 0 : allocatedContainers.stream()
                .map(c -> c.getConfiguration().getMemory().toMegabytes())
                .reduce(0L, Long::sum);

        var freeCpuCapacity = vm.getType().getCpuCores() * 1024 - allocatedCpuCapacity;
        var freeMemoryCapacity = vm.getType().getMemory() - allocatedMemoryCapacity;

        return Pair.of(freeCpuCapacity, freeMemoryCapacity);
    }

    public void allocateContainer(String containerId, String vmId) {
        containerVmAllocation.put(containerId, vmId);

        var container = model.getContainers().get(containerId);

        var vmContainerList = vmContainerAllocation.get(vmId);
        if (vmContainerList != null) {
            vmContainerList.add(container);
        } else {
            vmContainerAllocation.put(vmId, new ArrayList<>(Collections.singletonList(container)));
        }
    }

    public void deallocateContainer(String containerId) {
        var vmId = containerVmAllocation.get(containerId);
        containerVmAllocation.remove(containerId);

        var container = model.getContainers().get(containerId);

        var vmContainerList = vmContainerAllocation.get(vmId);
        vmContainerList.remove(container);

        if (vmContainerList.isEmpty()) {
            vmContainerAllocation.remove(vmId);
        }
    }

}
