package at.alexwais.cooper.scheduler;

import static at.alexwais.cooper.scheduler.MapUtils.putToMapList;
import static at.alexwais.cooper.scheduler.MapUtils.removeContainerFromMapList;

import at.alexwais.cooper.domain.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

@Getter
@RequiredArgsConstructor
public class State {

    private final Model model;

    @Setter
    private Allocation currentTargetAllocation;

    // Load measured by Monitor
    @Setter
    private Map<String, Integer> externalServiceLoad;
    @Setter
    private Map<String, Integer> internalServiceLoad;
    @Setter
    private Map<String, Integer> totalServiceLoad;
    @Setter
    private Integer totalSystemLoad;
    @Setter
    private SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> interactionGraph;

    @Setter
    private SimpleWeightedGraph<String, DefaultWeightedEdge> serviceAffinity;
//    private final Map<Service, Map<Service, Float>> serviceAffinity;

//    private final Map<String, String> containerVmAllocation = new HashMap<>();

    private final Map<String, List<ContainerInstance>> runningContainersByVm = new HashMap<>();
    private final Map<String, List<ContainerInstance>> runningContainersByType = new HashMap<>();
    private final Map<String, List<ContainerInstance>> runningContainersByService = new HashMap<>();

    private final Map<String, Long> leasedProviderVms = new HashMap<>();
    private final Map<ContainerInstance, Long> runningProviderContainers = new HashMap<>();

    public List<VmInstance> getLeasedVms() {
        return this.leasedProviderVms.keySet().stream()
                .map(k -> model.getVms().get(k))
                .collect(Collectors.toList());
    }

    public List<ContainerInstance> getRunningContainersByService(String service) {
        return this.runningContainersByService.getOrDefault(service, Collections.emptyList());
    }

    public List<ContainerInstance> getRunningContainersByVm(String vmId) {
        return this.runningContainersByVm.getOrDefault(vmId, Collections.emptyList());
    }

    public Map<String, Long> getServiceCapacity() {
        Map<String, Long> containerCapacityPerService = new HashMap<>();

        model.getServices().values().forEach(s -> {
            var rpmCapacitySum = getRunningContainersByService(s.getName()).stream()
                    .map(c -> c.getConfiguration().getRpmCapacity())
                    .reduce(0L, Long::sum);
            containerCapacityPerService.put(s.getName(), rpmCapacitySum);
        });

        return containerCapacityPerService;
    }

    public Pair<Integer, Long> getFreeCapacity(String vmId) {
        if (!leasedProviderVms.containsKey(vmId)) {
            return null;
        }

        var vm = model.getVms().get(vmId);
        var allocatedContainers = runningContainersByVm.get(vm.getId());

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

    public double getAffinityBetween(Service serviceA,
                                     Service serviceB) {
        var isSameService = serviceA.equals(serviceB);
        if (isSameService) {
            // No affinity between same service possible, graph contains no loops
            return 0;
        }
        var edge = serviceAffinity.getEdge(serviceA.getName(), serviceB.getName());
        var affinity = serviceAffinity.getEdgeWeight(edge);
        return affinity;
    }


    public void allocateContainerInstance(String vmId, ContainerType type, long providerId) {
        var vm = model.getVms().get(vmId);
        var isDuplicate = getRunningContainersByVm(vm.getId()).stream()
                .anyMatch(c -> c.getConfiguration().equals(type));

        if (isDuplicate)
            throw new IllegalStateException("Container of type " + type.getLabel() + " already allocated on VM " + vmId);

        var container = new ContainerInstance(type, type.getService(), vm);

        putToMapList(runningContainersByVm, container.getVm().getId(), container);
        putToMapList(runningContainersByType, container.getConfiguration().getLabel(), container);
        putToMapList(runningContainersByService, container.getService().getName(), container);

        runningProviderContainers.put(container, providerId);
    }

    public void deallocateContainerInstance(ContainerInstance container) {
        removeContainerFromMapList(runningContainersByVm, container.getVm().getId(), container);
        removeContainerFromMapList(runningContainersByType, container.getConfiguration().getLabel(), container);
        removeContainerFromMapList(runningContainersByService, container.getService().getName(), container);
        runningProviderContainers.remove(container);
    }

    public void releaseVm(String vmId) {
        var runningContainers = new ArrayList<>(getRunningContainersByVm(vmId));
        runningContainers.forEach(this::deallocateContainerInstance);
        leasedProviderVms.remove(vmId);
    }

}
