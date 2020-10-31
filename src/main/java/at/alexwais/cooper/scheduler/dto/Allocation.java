package at.alexwais.cooper.scheduler.dto;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.MapUtils;
import at.alexwais.cooper.scheduler.Model;
import java.util.*;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


public class Allocation {

    private final Model model;
    private final Map<VmInstance, List<ContainerType>> vmContainerMapping;
    private final Map<Service, List<ContainerType>> allocatedContainersByService = new HashMap<>();

    public Allocation(Model model, Map<VmInstance, List<ContainerType>> vmContainerMapping) {
        this.model = model;
        this.vmContainerMapping = vmContainerMapping;

        this.getAllocatedContainers().forEach(c -> {
            MapUtils.putToMapList(allocatedContainersByService, c.getService(), c);
        });
    }

    public Allocation(Model model, List<AllocationTuple> allocationTuples) {
        this.model = model;

        this.vmContainerMapping = new HashMap<>();
        allocationTuples.stream()
                .filter(AllocationTuple::isAllocate)
                .forEach(a -> {
                    MapUtils.putToMapList(vmContainerMapping, a.getVm(), a.getContainer());
                });

        this.getAllocatedContainers().forEach(c -> {
            MapUtils.putToMapList(allocatedContainersByService, c.getService(), c);
        });
    }

    public Map<VmInstance, List<ContainerType>> getAllocationMap() {
        return vmContainerMapping;
    }

    public boolean isAllocated(ContainerType containerType, VmInstance vm) {
        var containersOnVm = vmContainerMapping.getOrDefault(vm, new ArrayList<>());
        return containersOnVm.contains(containerType);
    }

    public List<VmInstance> getAllocatedVms() {
        return vmContainerMapping.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<ContainerType> getAllocatedContainers() {
        return vmContainerMapping.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public List<AllocationTuple> getAllocatedTuples() {
        return vmContainerMapping.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(c -> new AllocationTuple(e.getKey(), c, true))
                )
                .collect(Collectors.toList());
    }

    public List<AllocationTuple> getTuples() {
        List<AllocationTuple> resultTuples = new ArrayList<>();
        model.getVms().values().forEach(vm -> {
            model.getContainerTypes().forEach(type -> {
                var containerList = getAllocationMap().getOrDefault(vm, Collections.emptyList());
                var allocate = containerList.contains(type);
                var tuple = new AllocationTuple(vm, type, allocate);
                resultTuples.add(tuple);
            });
        });
        return resultTuples;
    }

    public float getTotalCost() {
        return vmContainerMapping.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> e.getKey().getType().getCost())
                .reduce(0f, Float::sum);
    }


    public Map<String, Long> getServiceCapacity() {
        var capacityPerService = new HashMap<String, Long>();
        for (Map.Entry<Service, List<ContainerType>> allocatedServiceContainers : allocatedContainersByService.entrySet()) {
            var service = allocatedServiceContainers.getKey();
            var containers = allocatedServiceContainers.getValue();

            var serviceCapacity = containers.stream()
                    .map(ContainerType::getRpmCapacity)
                    .reduce(0L, Long::sum);
            capacityPerService.put(service.getName(), serviceCapacity);
        }
        return capacityPerService;
    }


    @Getter
    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class AllocationTuple {
        private final VmInstance vm;
        private final ContainerType container;
        private final boolean allocate;
    }
}
