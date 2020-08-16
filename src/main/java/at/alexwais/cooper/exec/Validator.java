package at.alexwais.cooper.exec;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Validator {

    private Model model;

    public boolean isAllocationValid(Map<VmInstance, List<ContainerType>> allocation, Map<String, Long> serviceLoad) {
        return violations(allocation, serviceLoad) == 0;
    }

    public int violations(Map<VmInstance, List<ContainerType>> allocation, Map<String, Long> serviceLoad) {
        var allocatedContainers = allocation.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        var allocatedContainersByService = new HashMap<String, List<ContainerType>>();
        allocatedContainers.forEach(c -> {
            MapUtils.putToMapList(allocatedContainersByService, c.getService().getName(), c);
        });

        var capacityPerService = new HashMap<String, Long>();
        for (Service s : model.getServices().values()) {
            var rpmCap = allocatedContainersByService.getOrDefault(s.getName(), Collections.emptyList()).stream()
                    .map(ContainerType::getRpmCapacity)
                    .reduce(0L, Long::sum);
            capacityPerService.put(s.getName(), rpmCap);
        }


        var violations = 0;

        // Validate VMs are not overloaded
        for (Map.Entry<VmInstance, List<ContainerType>> e : allocation.entrySet()) {
            var vm = e.getKey();
            var containers = e.getValue();
            var isOverallocated = isVmOverallocated(vm, containers);
            if (isOverallocated) {
                violations++;
            }
        }

        // Validate Services are not underprovisioned
        for (Map.Entry<String, Long> entry : serviceLoad.entrySet()) {
            var serviceName = entry.getKey();
            var load = entry.getValue();
            var capacity = capacityPerService.get(serviceName);
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
