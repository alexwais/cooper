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
    private final Collection<VmInstance> runningVMs;
    private final Map<VmInstance, List<ContainerType>> vmContainerMapping;
    private final Map<Service, List<AllocationTuple>> allocatedContainersByService = new HashMap<>();

    public Allocation(Model model, Map<VmInstance, List<ContainerType>> vmContainerMapping) {
        this(model, vmContainerMapping.keySet(), vmContainerMapping);
    }

    public Allocation(Model model, Collection<VmInstance> runningVms, Map<VmInstance, List<ContainerType>> vmContainerMapping) {
        this.model = model;
        this.runningVMs = runningVms;
        this.vmContainerMapping = vmContainerMapping;

        var inconsistentMapping = this.vmContainerMapping.keySet().stream()
                .anyMatch(vm -> !this.runningVMs.contains(vm));

        if (inconsistentMapping) {
            throw new IllegalStateException("cannot allocate containers on not running VM");
        }

        for (var e : vmContainerMapping.entrySet()) {
            for (var containerType : e.getValue()) {
                MapUtils.putToMapList(allocatedContainersByService, containerType.getService(), new AllocationTuple(e.getKey(), containerType, true));
            }
        }
    }

    public Allocation(Model model, List<AllocationTuple> allocationTuples) {
        this.model = model;
        this.vmContainerMapping = new HashMap<>();
        allocationTuples.stream()
                .filter(AllocationTuple::isAllocate)
                .forEach(a -> {
                    MapUtils.putToMapList(vmContainerMapping, a.getVm(), a.getContainer());
                });
        this.runningVMs = vmContainerMapping.keySet();

        for (var e : vmContainerMapping.entrySet()) {
            for (var containerType : e.getValue()) {
                MapUtils.putToMapList(allocatedContainersByService, containerType.getService(), new AllocationTuple(e.getKey(), containerType, true));
            }
        }
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

    // TODO VMs running, even if unused -> i.e. private cloud!
    public Collection<VmInstance> getRunningVms() {
        return runningVMs;
    }

    public List<AllocationTuple> getAllocatedContainersOnVm(VmInstance vm) {
        return vmContainerMapping.getOrDefault(vm, new ArrayList<>()).stream()
                .map(c -> new AllocationTuple(vm, c, true))
                .collect(Collectors.toList());
    }

    public Map<Service, List<AllocationTuple>> getAllocatedContainersByService() {
        return allocatedContainersByService;
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
        return getAllocatedVms().stream()
                .map(vm -> vm.getType().getCost())
                .reduce(0f, Float::sum);
    }


    public Map<String, Long> getServiceCapacity() {
        var capacityPerService = new HashMap<String, Long>();
        for (var allocatedServiceInstance : allocatedContainersByService.entrySet()) {
            var service = allocatedServiceInstance.getKey();
            var containerInstances = allocatedServiceInstance.getValue();

            var serviceCapacity = containerInstances.stream()
                    .map(c -> c.getContainer().getRpmCapacity())
                    .reduce(0L, Long::sum);
            capacityPerService.put(service.getName(), serviceCapacity);
        }
        return capacityPerService;
    }


    private List<ContainerType> getAllocatedContainers() {
        return vmContainerMapping.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }


    @Getter
    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class AllocationTuple {
        private final VmInstance vm;
        private final ContainerType container;
        private final boolean allocate;

        public boolean isSameAllocation(AllocationTuple other) {
            return other.vm == this.vm && other.container == this.container;
        }
    }
}
