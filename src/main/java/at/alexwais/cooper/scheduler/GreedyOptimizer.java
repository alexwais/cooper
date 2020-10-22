package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.domain.ContainerInstance;
import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GreedyOptimizer implements Optimizer{

    private final Model model;
    private final State state;

    private final Map<String, Long> loadDeltaPerService = new HashMap<>();

    public GreedyOptimizer(Model model, State state) {
        this.model = model;
        this.state = state;

        var serviceCapacity = state.getServiceCapacity();
        state.getTotalServiceLoad().forEach((serviceName, currentLoad)  -> {
            var loadCapacity = serviceCapacity.get(serviceName);
            loadDeltaPerService.put(serviceName, currentLoad - loadCapacity);
        });
    }

    private  Map<String, List<ContainerType>> vmContainerAllocation;
    private final List<VmInstance> vmsToProvision = new ArrayList<>();

    public OptimizationResult optimize(State state) {
        vmContainerAllocation = state.getRunningContainersByVm().entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(ContainerInstance::getConfiguration).collect(Collectors.toList())
                ));

        // Determine containers to provision/deprovision based on load delta
        ArrayList<ContainerType> containerTypesToProvision = new ArrayList<>();
        loadDeltaPerService.forEach((serviceName, delta) -> {
            if (delta > 0) { // underprovisioned
                containerTypesToProvision.addAll(allocateUnderprovisionedResources(serviceName, delta));
            } else if (delta < 0) { // overprovisioned
                var containersToDeallocate = deallocateOverprovisionedResources(serviceName, delta);
                containersToDeallocate.forEach(c -> {
                    var allocationList = vmContainerAllocation.get(c.getVm().getId());
                    if (!allocationList.contains(c.getConfiguration())) {
                        throw new IllegalStateException("Cannot deallocate missing container " + c);
                    }
                    allocationList.remove(c.getConfiguration());
                });
            }
        });

        // Place containerTypesToProvision on VMs and provision new VMs if required
        containerTypesToProvision.forEach(c -> {
            var reusedVm = tryToProvisionOnLeasedVm(c);
            if (reusedVm != null) {
                MapUtils.putToMapList(vmContainerAllocation, reusedVm.getId(), c);
            } else {
                var newVm = getFreeInstanceForContainer(c);
                vmsToProvision.add(newVm);
                MapUtils.putToMapList(vmContainerAllocation, newVm.getId(), c);
            }
        });

        // Remove overprovisioned VMs
        List<VmInstance> vmsToDeprovision = new ArrayList<>();
        state.getLeasedVms().forEach(vm -> {
            var containerList = vmContainerAllocation.get(vm.getId());
            var isUsedForContainerAllocation = containerList != null && !containerList.isEmpty();
            if (!isUsedForContainerAllocation) {
                vmsToDeprovision.add(vm);
            }
        });

        // Combine allocation state of VMs
        var resultAllocatedVms = model.getVms().values().stream()
                .collect(Collectors.toMap(VmInstance::getId, vm -> {
                    var isAlreadyLeased = state.getLeasedVms().contains(vm);
                    var isRequiredToProvisionNewContainer = vmsToProvision.contains(vm);
                    var isToBeDeprovisioned = vmsToDeprovision.contains(vm);
                    var allocate = (isAlreadyLeased || isRequiredToProvisionNewContainer) && !isToBeDeprovisioned;

                    var containerList = vmContainerAllocation.get(vm.getId());
                    var hasContainersAllocated = containerList != null && !containerList.isEmpty();
                    if (!allocate && hasContainersAllocated) {
                        throw new IllegalStateException("Deallocated VM " + vm.getId() + " has containers allocated!");
                    }

                    return allocate;
                }));

        var alloc = vmContainerAllocation.entrySet().stream()
                .collect(Collectors.toMap(e -> model.getVms().get(e.getKey()), Map.Entry::getValue));

        return new OptimizationResult(model, alloc);
    }


    private VmInstance getFreeInstanceForContainer(ContainerType type) {
        return model.getVms().values().stream()
                .filter(vm -> !state.getLeasedVms().contains(vm))
                .filter(vm -> !vmsToProvision.contains(vm))
                .filter(vm -> Validator.isContainerPlacableOnVm(type, vm, Collections.emptyList()))
                .findFirst().orElseThrow(() -> new IllegalStateException("No free vm instances left"));
    }

    private VmInstance tryToProvisionOnLeasedVm(ContainerType type) {
        for (var vmId : vmContainerAllocation.keySet()) {
            var vm = model.getVms().get(vmId);
            var allocatedContainers = vmContainerAllocation.get(vm.getId());

            if (Validator.isContainerPlacableOnVm(type, vm, allocatedContainers)) {
                return vm;
            }
        }
        return null;
    }

    private List<ContainerType> allocateUnderprovisionedResources(String serviceName, long delta) {
        var service = model.getServices().get(serviceName);
        var types = service.getContainerTypes().stream()
                .sorted(Comparator.comparingLong(ContainerType::getRpmCapacity).reversed())
                .iterator();

        var result = new ArrayList<ContainerType>();

        var currentType = types.next();
        while (delta > 0) {
            var capacity = currentType.getRpmCapacity();
            if (delta < capacity && types.hasNext()) {
                currentType = types.next();
                continue;
            }
            result.add(currentType);
            delta -= capacity;
        }

        return result;
    }

    private List<ContainerInstance> deallocateOverprovisionedResources(String serviceName, long delta) {
        var runningContainersIterator = state.getRunningContainersByService(serviceName).stream().iterator();

        var result = new ArrayList<ContainerInstance>();
        while (delta < 0) {
            var c = runningContainersIterator.next();
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
