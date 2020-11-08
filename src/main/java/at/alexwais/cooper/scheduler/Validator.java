package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.dto.Allocation;
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

        // Validate VMs are not overloaded
        for (Map.Entry<VmInstance, List<ContainerType>> e : resourceAllocation.getAllocationMap().entrySet()) {
            var vm = e.getKey();
            var containers = e.getValue();
            var previousContainers = previousAllocation.getAllocatedContainersOnVm(vm).stream()
                    .map(Allocation.AllocationTuple::getContainer)
                    .collect(Collectors.toList());
            var isOverallocated = isVmOverallocated(vm, containers, previousContainers);
            if (isOverallocated) {
                violations++;
            }
        }

        // Validate Services are not underprovisioned
        for (Map.Entry<String, Integer> entry : serviceLoad.entrySet()) {
            var serviceName = entry.getKey();
            var load = entry.getValue();
            var capacity = resourceAllocation.getServiceCapacity().getOrDefault(serviceName, 0L);
            if (capacity < load) {
                violations += load - capacity;
                // violations++;
            }
        }

        return violations;
    }


    public int neutralViolations(Allocation resourceAllocation, Map<String, Integer> serviceLoad) {
        var violations = 0;

        // Validate VMs are not overloaded
        for (Map.Entry<VmInstance, List<ContainerType>> e : resourceAllocation.getAllocationMap().entrySet()) {
            var vm = e.getKey();
            var containers = e.getValue();
            var isOverallocated = isVmOverallocatedNeutral(vm, containers);
            if (isOverallocated) {
                violations++;
            }
        }

        // Validate Services are not underprovisioned
        for (Map.Entry<String, Integer> entry : serviceLoad.entrySet()) {
            var serviceName = entry.getKey();
            var load = entry.getValue();
            var capacity = resourceAllocation.getServiceCapacity().getOrDefault(serviceName, 0L);
            if (capacity < load) {
                violations += load - capacity;
                // violations++;
            }
        }

        return violations;
    }

    private static boolean isVmOverallocated(VmInstance vm, List<ContainerType> containers, List<ContainerType> previousContainers) {
        var allocatedServices = containers.stream().map(ContainerType::getService).collect(Collectors.toList());

        var cpuCapacity = vm.getType().getCpuCores() * 1024;
        var memoryCapacity = vm.getType().getMemory();

        var allocatedCpu = containers.stream()
                .map(ContainerType::getCpuShares)
                .reduce(0, Integer::sum);
        var allocatedMemory = containers.stream()
                .map(t -> t.getMemory().toMegabytes())
                .reduce(0L, Long::sum);

        var abandonedCpu = previousContainers.stream()
                .filter(c -> !allocatedServices.contains(c.getService()))
                .map(ContainerType::getCpuShares)
                .reduce(0, Integer::sum);
        var abandonedMemory = previousContainers.stream()
                .filter(c -> !allocatedServices.contains(c.getService()))
                .map(t -> t.getMemory().toMegabytes())
                .reduce(0L, Long::sum);

        var hasEnoughCpuAvailable = allocatedCpu + abandonedCpu <= cpuCapacity;
        var hasEnoughMemoryAvailable = allocatedMemory + abandonedMemory <= memoryCapacity;

        return !hasEnoughCpuAvailable || !hasEnoughMemoryAvailable;
    }

    private static boolean isVmOverallocatedNeutral(VmInstance vm, List<ContainerType> containers) {
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
