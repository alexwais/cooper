package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.domain.Allocation;
import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Validator {

    private Model model;

    public boolean isAllocationValid(Allocation resourceAllocation, Map<String, Integer> serviceLoad) {
        return violations(resourceAllocation, serviceLoad) == 0;
    }

    public int violations(Allocation resourceAllocation, Map<String, Integer> serviceLoad) {
        var allocatedContainersByService = new HashMap<Service, List<ContainerType>>();
        resourceAllocation.getAllocatedContainers().forEach(c -> {
            MapUtils.putToMapList(allocatedContainersByService, c.getService(), c);
        });

        // TODO refactor duplicated code in fitness function - move to allocation as helper
        var capacityPerService = new HashMap<String, Long>();
        for (Map.Entry<Service, List<ContainerType>> allocatedServiceContainers : allocatedContainersByService.entrySet()) {
            var service = allocatedServiceContainers.getKey();
            var containers = allocatedServiceContainers.getValue();

            var serviceCapacity = containers.stream()
                    .map(ContainerType::getRpmCapacity)
                    .reduce(0L, Long::sum);
            capacityPerService.put(service.getName(), serviceCapacity);
        }


        var violations = 0;

        // Validate VMs are not overloaded
        for (Map.Entry<VmInstance, List<ContainerType>> e : resourceAllocation.getAllocationMap().entrySet()) {
            var vm = e.getKey();
            var containers = e.getValue();
            var isOverallocated = isVmOverallocated(vm, containers);
            if (isOverallocated) {
                violations++;
            }
        }

        // Validate Services are not underprovisioned
        for (Map.Entry<String, Integer> entry : serviceLoad.entrySet()) {
            var serviceName = entry.getKey();
            var load = entry.getValue();
            var capacity = capacityPerService.getOrDefault(serviceName, 0L);
            if (capacity < load) {
                violations += load - capacity;
                // violations++;
            }
        }

        return violations;
    }

    public static boolean isContainerPlacableOnVm(ContainerType type, VmInstance vm, List<ContainerType> allocatedContainers) {
        var totalContainersAfterProvisioning = new ArrayList<>(allocatedContainers);
        totalContainersAfterProvisioning.add(type);

        var hasTypeNotAllocated = !allocatedContainers.contains(type);
        var hasEnoughCapacity = !isVmOverallocated(vm, totalContainersAfterProvisioning);

        return hasTypeNotAllocated && hasEnoughCapacity;
    }

    private static boolean isVmOverallocated(VmInstance vm, List<ContainerType> containers) {
        var cpuCapacity = vm.getType().getCpuCores() * 1024;
        var memoryCapacity = vm.getType().getMemory();

        var allocatedCpu = containers.stream()
                .map(ContainerType::getCpuShares)
                .reduce(0, Integer::sum);
        var allocatedMemory = containers.stream()
                .map(t -> t.getMemory().toMegabytes())
                .reduce(0L, Long::sum);

        var hasEnoughCpuAvailable = allocatedCpu <= cpuCapacity;
        var hasEnoughMemoryAvailable = allocatedMemory <= memoryCapacity;

        return !hasEnoughCpuAvailable || !hasEnoughMemoryAvailable;
    }

}
