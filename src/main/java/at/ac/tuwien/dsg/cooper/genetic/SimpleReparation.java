package at.ac.tuwien.dsg.cooper.genetic;

import at.ac.tuwien.dsg.cooper.domain.ContainerType;
import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.MapUtils;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import at.ac.tuwien.dsg.cooper.scheduler.Validator;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SimpleReparation {

    private final Model model;
    private final SystemMeasures measures;
    private final Validator validator;
    private final Allocation previousAllocation;

    public HashMap<VmInstance, List<ContainerType>> repairGeneticAllocation(final Allocation allocation) {
        var repairedAllocation = new HashMap<VmInstance, List<ContainerType>>();
        var containersToMove = new ArrayList<ContainerType>();

        var allocations = new ArrayList<>(allocation.getAllocationMap().entrySet());
        Collections.shuffle(allocations);

        // Remove & move excess containers from VMs
        for (var e : allocations) {
            var vm = e.getKey();
            var containers = e.getValue();

            var previousContainers = previousAllocation.getAllocatedContainersOnVm(vm).stream()
                    .map(Allocation.AllocationTuple::getContainer)
                    .collect(Collectors.toList());

            var isVmOverallocated = validator.isVmOverallocated(vm, containers, previousContainers);

            if (!isVmOverallocated) {
                repairedAllocation.put(vm, containers);
            } else {
                var repairedContainers = new ArrayList<ContainerType>();
                Collections.shuffle(containers);
                for (var container : containers) {
                    var containersToTest = new ArrayList<>(repairedContainers);
                    containersToTest.add(container);
                    var containerFits = !validator.isVmOverallocated(vm, containersToTest, previousContainers);
                    if (containerFits) {
                        repairedContainers.add(container);
                    } else {
                        containersToMove.add(container);
                    }
                }
                repairedAllocation.put(vm, repairedContainers);
            }
        }

        // calc capacity of containers to move
        var containersToMoveByService = new HashMap<Service, List<ContainerType>>();
        containersToMove.forEach(c -> {
            MapUtils.putToMapList(containersToMoveByService, c.getService(), c);
        });
        var movingContainersCapacity = containersToMoveByService.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().stream().map(ContainerType::getRpmCapacity).reduce(0L, Long::sum)
        ));

        // calc missing service capacity (considering capacity of containers to move)
        var missingServiceCapacity = validator.missingCapacityPerService(new Allocation(model, repairedAllocation), measures.getTotalServiceLoad())
                .entrySet().stream()
                .filter(e -> e.getValue() - movingContainersCapacity.getOrDefault(e.getKey(), 0L) > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // compute additionally needed containers to fulfill load requirements
        var additionalNeededContainers = new ArrayList<ContainerType>();
        for (var entry : missingServiceCapacity.entrySet()) {
            var service = entry.getKey();
            var remainingCapacity = entry.getValue();

            while (remainingCapacity > 0) {
                var missingCapacity = remainingCapacity;

                var nextSmallestContainer =  service.getContainerTypes().stream()
                        .filter(c -> c.getRpmCapacity() < missingCapacity)
                        .max(Comparator.comparingLong(ContainerType::getRpmCapacity));
                var nextBiggestContainer = service.getContainerTypes().stream()
                        .filter(c -> c.getRpmCapacity() >= missingCapacity)
                        .min(Comparator.comparingLong(ContainerType::getRpmCapacity));
                var bestFittingContainer = nextBiggestContainer.orElse(nextSmallestContainer.orElse(null));

//                var randomContainer = service.getContainerTypes().stream()
//                        .collect(Collectors.collectingAndThen(Collectors.toList(), collected -> {
//                            Collections.shuffle(collected);
//                            return collected;
//                        })).get(0);
                additionalNeededContainers.add(bestFittingContainer);
                remainingCapacity -= bestFittingContainer.getRpmCapacity();
            }
        }

        // containersToMove + additionalNeededContainers -> that's the containers we need to place
        var containersToAllocate = Stream.concat(containersToMove.stream(), additionalNeededContainers.stream())
                .collect(Collectors.toList());

        // Try allocating on (already allocated) VMs
        for (Map.Entry<VmInstance, List<ContainerType>> e : repairedAllocation.entrySet()) {
            var vm = e.getKey();
            var presentContainers = e.getValue();

            var remainingContainers = tryToFitContainersOnVm(vm, presentContainers, containersToAllocate);
            containersToAllocate = remainingContainers;
        }


        var abandonedVms = previousAllocation.getRunningVms().stream()
                .filter(vm -> !repairedAllocation.containsKey(vm))
                .collect(Collectors.toList());
        var otherVms = model.getVms().values().stream()
                .filter(vm -> !repairedAllocation.containsKey(vm))
                .filter(vm -> !abandonedVms.contains(vm))
                .collect(Collectors.toList());
        var nonAllocatedVms = Stream.concat(abandonedVms.stream(), otherVms.stream())
                .sorted(Comparator.comparingDouble(vm -> vm.getType().getCost()))
                .collect(Collectors.toList());
//                .collect(Collectors.collectingAndThen(Collectors.toList(), collected -> {
//                    Collections.shuffle(collected);
//                    return collected;
//                }));


        // Try allocating on any other / abandoned VMs
        for (var vm: nonAllocatedVms) {
            var allocatedContainers = new ArrayList<ContainerType>();

            var remainingContainers = tryToFitContainersOnVm(vm, allocatedContainers, containersToAllocate);

            if (!allocatedContainers.isEmpty()) {
                repairedAllocation.put(vm, allocatedContainers);
            }
            containersToAllocate = remainingContainers;
        }

//        log.debug("Remaining excess containers after reparation: {} (moved {})", excessContainers.size(), excessBeforeMoving);
//        if (!validator.isAllocationValid(new Allocation(model, repairedAllocation), previousAllocation, measures.getTotalServiceLoad())) {
//            log.warn("Invalid!");
//        }

        return repairedAllocation;
    }


    private List<ContainerType> tryToFitContainersOnVm(final VmInstance vm, final List<ContainerType> allocatedContainers, final List<ContainerType> containersToFit) {
        var presentServices = allocatedContainers.stream()
                .map(ContainerType::getService)
                .collect(Collectors.toList());
        var previousContainers = previousAllocation.getAllocatedContainersOnVm(vm).stream()
                .map(Allocation.AllocationTuple::getContainer)
                .collect(Collectors.toList());

        var remainingContainersToMove = new ArrayList<ContainerType>();
        for (int i = 0; i < containersToFit.size(); i++) {
            var c = containersToFit.get(i);

            // Only 1 container instance per service can be allocated on the same VM
            if (presentServices.contains(c.getService())) {
                remainingContainersToMove.add(c);
                continue;
            }

            var containersToTest = new ArrayList<>(allocatedContainers);
            containersToTest.add(c);
            var containerFits = !validator.isVmOverallocated(vm, containersToTest, previousContainers); // TODO track remaining resources separatly
            if (containerFits) {
                allocatedContainers.add(c);
                presentServices.add(c.getService());
            } else {
                remainingContainersToMove.add(c);
            }
        }

        return remainingContainersToMove;
    }

}
