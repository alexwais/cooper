package at.alexwais.cooper.domain;

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

    public Allocation(Model model, Map<VmInstance, List<ContainerType>> vmContainerMapping) {
        this.model = model;
        this.vmContainerMapping = vmContainerMapping;
    }

    public Allocation(Model model, List<AllocationTuple> allocationTuples) {
        this.model = model;

        this.vmContainerMapping = new HashMap<>();
        allocationTuples.stream()
                .filter(AllocationTuple::isAllocate)
                .forEach(a -> {
                    MapUtils.putToMapList(vmContainerMapping, a.getVm(), a.getContainer());
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


    @Getter
    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class AllocationTuple {
        private final VmInstance vm;
        private final ContainerType container;
        private final boolean allocate;
    }
}
