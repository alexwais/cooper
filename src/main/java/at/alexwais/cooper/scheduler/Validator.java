package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.dto.Allocation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Validator {

    private Model model;

    public boolean isAllocationValid(Allocation resourceAllocation, Allocation previousAllocation, Map<String, Integer> serviceLoad) {
        return violations(resourceAllocation, previousAllocation, serviceLoad) == 0;
    }
    public boolean isAllocationValidNeutral(Allocation resourceAllocation, Map<String, Integer> serviceLoad) {
        return neutralViolations(resourceAllocation, serviceLoad) == 0;
    }

    public int violations(Allocation resourceAllocation, Allocation previousAllocation, Map<String, Integer> serviceLoad) {
        var violations = 0;
        violations += calcOverallocatedVmViolations(resourceAllocation, previousAllocation);
        violations += calcServiceUnderprovisioningViolations(resourceAllocation, serviceLoad);
        return violations;
    }

    public int neutralViolations(Allocation resourceAllocation, Map<String, Integer> serviceLoad) {
        var violations = 0;
        violations += calcOverallocatedVmViolations(resourceAllocation, null);
        violations += calcServiceUnderprovisioningViolations(resourceAllocation, serviceLoad);
        return violations;
    }

    public int calcOverallocatedVmViolations(Allocation resourceAllocation, Allocation previousAllocation) {
        var violations = 0;
        for (var e : resourceAllocation.getAllocationMap().entrySet()) {
            var vm = e.getKey();
            var containers = e.getValue();

            Boolean isOverallocated;
            if (previousAllocation == null) { // ignore previous allocation -> get neutral violations
                isOverallocated = isVmOverallocated(vm, containers, null);
            } else {
                var previousContainers = previousAllocation.getAllocatedContainersOnVm(vm).stream()
                        .map(Allocation.AllocationTuple::getContainer)
                        .collect(Collectors.toList());
                isOverallocated = isVmOverallocated(vm, containers, previousContainers);
            }

            if (isOverallocated) {
                violations++;
            }
        }
        return violations;
    }

    public boolean isVmOverallocated(VmInstance vm, List<ContainerType> containers, List<ContainerType> previousContainers) {
        var cpuCapacity = vm.getType().getCpuUnits();
        var memoryCapacity = vm.getType().getMemory();

        var allocatedCpuPerService = containers.stream()
                .collect(Collectors.toMap(ContainerType::getService, ContainerType::getCpuShares));
        var allocatedMemoryPerService = containers.stream()
                .collect(Collectors.toMap(ContainerType::getService, ContainerType::getMemory));

        var abandonedCpuPerService = previousContainers == null ? new HashMap<Service, Integer>() : previousContainers.stream()
                .collect(Collectors.toMap(ContainerType::getService, ContainerType::getCpuShares));
        var abandonedMemoryPerService = previousContainers == null ? new HashMap<Service, Integer>() : previousContainers.stream()
                .collect(Collectors.toMap(ContainerType::getService, ContainerType::getMemory));

        var totalCpu = model.getServices().values().stream()
                .map(s -> Math.max(allocatedCpuPerService.getOrDefault(s, 0), abandonedCpuPerService.getOrDefault(s, 0)))
                .reduce(0, Integer::sum);
        var totalMemory = model.getServices().values().stream()
                .map(s -> Math.max(allocatedMemoryPerService.getOrDefault(s, 0), abandonedMemoryPerService.getOrDefault(s, 0)))
                .reduce(0, Integer::sum);

        var hasEnoughCpuAvailable = totalCpu <= cpuCapacity;
        var hasEnoughMemoryAvailable = totalMemory <= memoryCapacity;

        return !hasEnoughCpuAvailable || !hasEnoughMemoryAvailable;
    }

    private int calcServiceUnderprovisioningViolations(Allocation resourceAllocation, Map<String, Integer> serviceLoad) {
        var violations = 0;
        for (Map.Entry<String, Integer> entry : serviceLoad.entrySet()) {
            var serviceName = entry.getKey();
            var load = entry.getValue();
            var capacity = resourceAllocation.getServiceCapacity().getOrDefault(serviceName, 0L);
            if (capacity < load) {
                violations += load - capacity;
//                violations++;
            }
        }
        return violations;
    }



    public Map<Service, Long> missingCapacityPerService(Allocation resourceAllocation, Map<String, Integer> serviceLoad) {
        return serviceLoad.entrySet().stream()
                .collect(Collectors.toMap(e -> model.getServices().get(e.getKey()),
                        e -> {
                            var serviceName = e.getKey();
                            var load = e.getValue();
                            var capacity = resourceAllocation.getServiceCapacity().getOrDefault(serviceName, 0L);
                            if (capacity < load) {
                                return load - capacity;
                            } else {
                                return 0L;
                            }
                        }
                ));
    }

}
