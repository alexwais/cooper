package at.ac.tuwien.dsg.cooper.scheduler.dto;

import at.ac.tuwien.dsg.cooper.domain.ContainerType;
import at.ac.tuwien.dsg.cooper.domain.DataCenter;
import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.MapUtils;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import java.util.*;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


public class Allocation {

    private final Model model;
    private final List<VmInstance> runningVMs;
    private final Map<VmInstance, List<ContainerType>> vmContainerMapping;
    private final Map<Service, List<AllocationTuple>> allocatedContainersByService = new HashMap<>();

    /**
     * Creates an empty allocation (for initial state)
     */
    public Allocation(Model model) {
        this.model = model;
        this.runningVMs = new ArrayList<>();
        this.vmContainerMapping = new HashMap<>();
    }

    public Allocation(Allocation toClone) {
        this(toClone.model, toClone.getTuples());
    }

    public Allocation(Model model, Map<VmInstance, List<ContainerType>> vmContainerMapping) {
        this(model, vmContainerMapping.keySet(), vmContainerMapping);
    }

    public Allocation(Model model, Collection<VmInstance> runningVms, Map<VmInstance, List<ContainerType>> vmContainerMapping) {
        this.model = model;
        this.runningVMs = new ArrayList<>(runningVms);
        this.vmContainerMapping = vmContainerMapping;

        init();
    }

    public Allocation(Model model, List<AllocationTuple> allocationTuples) {
        this.model = model;
        this.vmContainerMapping = new HashMap<>();
        allocationTuples.stream()
                .filter(AllocationTuple::isAllocate)
                .forEach(a -> {
                    MapUtils.putToMapList(vmContainerMapping, a.getVm(), a.getContainer());
                });
        this.runningVMs = new ArrayList<>(vmContainerMapping.keySet());

        init();
    }

    private void init() {
        var inconsistentMapping = this.vmContainerMapping.keySet().stream()
                .anyMatch(vm -> !this.runningVMs.contains(vm));

        if (inconsistentMapping) {
            throw new IllegalStateException("cannot allocate containers on not running VM");
        }

        // always add on-premise VMs to running VMs
        model.getDataCenters().values().stream()
                .filter(DataCenter::isOnPremise)
                .forEach(dc -> {
                    dc.getVmInstances().forEach(vm -> {
                        if (!runningVMs.contains(vm)) {
                            runningVMs.add(vm);
                        }
                    });
                });

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

    public List<VmInstance> getUsedVms() {
        return vmContainerMapping.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // VMs running, even if unused -> i.e. private cloud!
    public List<VmInstance> getRunningVms() {
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
        return getRunningVms().stream()
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
