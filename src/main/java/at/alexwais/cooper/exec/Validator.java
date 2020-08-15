package at.alexwais.cooper.exec;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import java.util.List;

public class Validator {

    public static boolean isContainerPlacableOnVm(ContainerType type, VmInstance vm, List<ContainerType> allocatedContainers) {
        var vmCpuCapacity = vm.getType().getCpuCores() * 1024;
        var vmMemoryCapacity = vm.getType().getMemory();

        var allocatedCpuCapacity = allocatedContainers == null ? 0 : allocatedContainers.stream()
                .map(ContainerType::getCpuShares)
                .reduce(0, Integer::sum);
        var allocatedMemoryCapacity = allocatedContainers == null ? 0 : allocatedContainers.stream()
                .map(c -> c.getMemory().toMegabytes())
                .reduce(0L, Long::sum);

        var hasEnoughCpuAvailable = allocatedCpuCapacity + type.getCpuShares() <= vmCpuCapacity;
        var hasEnoughMemoryAvailable = allocatedMemoryCapacity + type.getMemory().toMegabytes() <= vmMemoryCapacity;
        var hasTypeNotAllocated = !allocatedContainers.contains(type);

        return hasEnoughCpuAvailable && hasEnoughMemoryAvailable && hasTypeNotAllocated;
    }

}
