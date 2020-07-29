package at.alexwais.cooper.exec;

import at.alexwais.cooper.domain.ContainerInstance;
import at.alexwais.cooper.domain.Instance;
import java.util.*;
import java.util.stream.Collectors;


public class GreedyOptimizer {

    private final Model model;
    private final State state;

    private final Map<String, Long> loadDeltaPerService = new HashMap<>();

    public GreedyOptimizer(Model model, State state) {
        this.model = model;
        this.state = state;

        Map<String, Long> conatinerCapacityPerService = new HashMap<>();

        model.getServices().values().forEach(s -> {
            var rpmCapacitySum = s.getContainers().stream()
                    .filter(c -> state.getRunningContainers().contains(c))
                    .map(c -> c.getConfiguration().getRpmCapacity())
                    .reduce(0L, Long::sum);
            conatinerCapacityPerService.put(s.getName(), rpmCapacitySum);
        });

        state.getServiceLoad().forEach((serviceName, currentLoad)  -> {
            var loadCapacity = conatinerCapacityPerService.get(serviceName);
            loadDeltaPerService.put(serviceName, currentLoad - loadCapacity);
        });
    }


    private final List<ContainerInstance> containersToProvision = new ArrayList<>();
    private final List<ContainerInstance> containersToDeprovision = new ArrayList<>();
    private final List<Instance> vmsToProvision = new ArrayList<>();
    // TODO deprovision vms
    private Map<String, List<ContainerInstance>> vmContainerAllocation = new HashMap<>();

    public OptimizationResult optimize() {
        loadDeltaPerService.forEach((serviceName, delta) -> {
            if (delta > 0) { // underprovisioned
                containersToProvision.addAll(allocateUnderprovisionedResources(serviceName, delta));
            } else if (delta < 0) { // overprovisioned
                containersToDeprovision.addAll(deallocateOverprovisionedResources(serviceName, delta));
            }
        });

        vmContainerAllocation = state.getVmContainerAllocation().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(e.getValue())));

        containersToDeprovision.forEach(c -> {
            var vmId = state.getContainerVmAllocation().get(c.getId());
            vmContainerAllocation.get(vmId).remove(c);
        });

        containersToProvision.forEach(c -> {
            var reusedVm = tryToProvisionOnLeasedVm(c);
            if (reusedVm != null) {
                var vmContainerList = vmContainerAllocation.get(reusedVm.getId());
                if (vmContainerList != null) {
                    vmContainerList.add(c);
                } else {
                    vmContainerAllocation.put(reusedVm.getId(), new ArrayList<>(Collections.singletonList(c)));
                }
            } else {
                var newVm = getFreeInstance();
                vmsToProvision.add(newVm);
                vmContainerAllocation.put(newVm.getId(), new ArrayList<>(Collections.singletonList(c)));
            }
        });

        var resultAllocatedVms = model.getVms().values().stream()
                .collect(Collectors.toMap(Instance::getId, vm -> {
                    var isAlreadyLeased = state.getLeasedVms().contains(vm);
                    var isRequiredToProvisionNewContainer = vmsToProvision.contains(vm);
                    return isAlreadyLeased || isRequiredToProvisionNewContainer;
                }));

        var resultContainerAllocation = new HashMap<String, String>();
        vmContainerAllocation.forEach((vmId, containers) -> {
            containers.forEach(c -> resultContainerAllocation.put(c.getId(), vmId));
        });

        return new OptimizationResult(resultAllocatedVms, resultContainerAllocation);
    }


    private Instance getFreeInstance() {
        return model.getVms().values().stream()
                .filter(vm -> !state.getLeasedVms().contains(vm))
                .filter(vm -> !vmsToProvision.contains(vm))
                .findFirst().orElseThrow(() -> new IllegalStateException("No free vm instances left"));
    }

    private Instance tryToProvisionOnLeasedVm(ContainerInstance container) {
        for (var vmId : vmContainerAllocation.keySet()) {
            var vm = model.getVms().get(vmId);
            var vmCpuCapacity = vm.getType().getCpuCores() * 1024;
            var vmMemoryCapacity = vm.getType().getMemory();

            var allocatedContainers = vmContainerAllocation.get(vm.getId());

            var allocatedCpuCapacity = allocatedContainers == null ? 0 : allocatedContainers.stream()
                    .map(c -> c.getConfiguration().getCpuShares())
                    .reduce(0, Integer::sum);
            var allocatedMemoryCapacity = allocatedContainers == null ? 0 : allocatedContainers.stream()
                    .map(c -> c.getConfiguration().getMemory().toMegabytes())
                    .reduce(0L, Long::sum);

            var hasEnoughCpuAvailable = allocatedCpuCapacity + container.getConfiguration().getCpuShares() <= vmCpuCapacity;
            var hasEnoughMemoryAvailable = allocatedMemoryCapacity + container.getConfiguration().getMemory().toMegabytes() <= vmMemoryCapacity;

            if (hasEnoughCpuAvailable && hasEnoughMemoryAvailable) {
                return vm;
            }
        }
        return null;
    }

    private List<ContainerInstance> allocateUnderprovisionedResources(String serviceName, long delta) {
        var service = model.getServices().get(serviceName);
        var freeContainers = service.getContainers().stream()
                .filter(c -> !state.getRunningContainers().contains(c))
                .iterator();

        var result = new ArrayList<ContainerInstance>();

        while (delta > 0 && freeContainers.hasNext()) {
            var c = freeContainers.next();
            result.add(c);
            delta -= c.getConfiguration().getRpmCapacity();
        }

        if (delta > 0) {
            throw new IllegalStateException("No free container instances left for service: " + serviceName);
        }

        return result;
    }

    private List<ContainerInstance> deallocateOverprovisionedResources(String serviceName, long delta) {
        var service = model.getServices().get(serviceName);
        var runningContainers = service.getContainers().stream()
                .filter(c -> state.getRunningContainers().contains(c))
                .iterator();

        var result = new ArrayList<ContainerInstance>();

        while (delta < 0) {
            var c = runningContainers.next();
            var isNotReleasable = delta + c.getConfiguration().getRpmCapacity() > 0;
            if (isNotReleasable) {
                break;
            }
            result.add(c);
            delta += c.getConfiguration().getRpmCapacity();
        }

        return result;
    }

}
