package at.ac.tuwien.dsg.cooper.scheduler;

import at.ac.tuwien.dsg.cooper.api.Optimizer;
import at.ac.tuwien.dsg.cooper.domain.ContainerType;
import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.OptResult;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

@Slf4j
public class FirstFitOptimizer implements Optimizer {

    private final Model model;
    private final Validator validator;

    public FirstFitOptimizer(Model model) {
        this.model = model;
        this.validator = new Validator(model);
    }


    public OptResult optimize(Allocation currentAllocation, SystemMeasures systemMeasures, Map<VmInstance, Set<Service>> cachedImages) {
        var stopWatch = new StopWatch();
        stopWatch.start();

        var allocationTuples = doIt(currentAllocation, systemMeasures);

        stopWatch.stop();

        return new OptResult(model, systemMeasures, allocationTuples, stopWatch.getTotalTimeMillis());
    }


    public List<Allocation.AllocationTuple> doIt(Allocation previousAllocation, SystemMeasures systemMeasures) {
        var allocationMap = new Allocation(previousAllocation).getAllocationMap();

        var missingServiceCapacity = validator.missingCapacityPerService(new Allocation(model, allocationMap), systemMeasures.getTotalServiceLoad())
                .entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        var overprovisionedServiceCapacity = validator.overprovisionedCapacityPerService(new Allocation(model, allocationMap), systemMeasures.getTotalServiceLoad())
                .entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


        // first, remove overprovisioned containers from existing VMs
        for (var entry : allocationMap.entrySet()) {
            var allocatedContainers = entry.getValue();

            var containersToRemove = new ArrayList<ContainerType>();
            for (var c : allocatedContainers) {
                if (overprovisionedServiceCapacity.containsKey(c.getService())) {
                    var excessCapacity = overprovisionedServiceCapacity.get(c.getService());
                    if (c.getRpmCapacity() <= excessCapacity) {
                        containersToRemove.add(c);
                    }
                }
            }
            allocatedContainers.removeAll(containersToRemove);
            containersToRemove.forEach(c -> {
                    var previousOvercapacity = overprovisionedServiceCapacity.get(c.getService());
                    overprovisionedServiceCapacity.put(c.getService(), previousOvercapacity - c.getRpmCapacity());
            });
        }

        // add additional containers to existing VMs
        for (var entry : allocationMap.entrySet()) {
            var vm = entry.getKey();
            var allocatedContainers = entry.getValue();

            var updateMissingCapacity = new HashMap<Service, Long>();
            for (var e : missingServiceCapacity.entrySet()) {
                var service = e.getKey();
                var missingCapacity = e.getValue();
                var containerToPlace = tryToPlaceServiceContainerOnVm(vm, allocatedContainers, service, missingCapacity, previousAllocation);
                if (containerToPlace != null) {
                    allocatedContainers.add(containerToPlace);
                    updateMissingCapacity.put(service, missingCapacity - containerToPlace.getRpmCapacity());
                }
            }

            for (var u : updateMissingCapacity.entrySet()) {
                var s = u.getKey();
                var remainingMissingCapacity = u.getValue();
                if (remainingMissingCapacity > 0) {
                    missingServiceCapacity.put(s, remainingMissingCapacity);
                } else {
                    missingServiceCapacity.remove(s);
                }
            }
        }

        // compute additionally needed containers - to place on new VMs
        var additionalNeededContainerInstances = new ArrayList<ContainerType>();
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

                additionalNeededContainerInstances.add(bestFittingContainer);
                remainingCapacity -= bestFittingContainer.getRpmCapacity();
            }
        }


        // add additional containers to existing VMs
//        for (var e : allocationMap.entrySet()) {
//            var vm = e.getKey();
//            var containers = e.getValue();
//
//            var previousContainers = previousAllocation.getAllocatedContainersOnVm(vm).stream()
//                    .map(Allocation.AllocationTuple::getContainer)
//                    .collect(Collectors.toList());
//
//            var remainingAdditionalContainers = new ArrayList<ContainerType>();
//
//            for (var container : additionalNeededContainerInstances) {
////                if (!placedServices.isEmpty()) { // this would be one-for-each
////                    remainingAdditionalContainers.add(container);
////                    continue;
////                }
//
//                if (containers.stream().map(ContainerType::getService).collect(Collectors.toList())
//                        .contains(container.getService())) {
//                    remainingAdditionalContainers.add(container);
//                    continue;
//                }
//
//                var containersToTry = new ArrayList<>(containers);
//                containersToTry.add(container);
//                var containerFits = !validator.isVmOverallocated(vm, containersToTry, previousContainers);
//                if (containerFits) {
//                    containers.add(container);
////                    MapUtils.putToMapList(allocationMap, vm, container);
////                    allocationMap.put(vm, Collections.singletonList(container));
//                } else {
//                    remainingAdditionalContainers.add(container);
//                }
//            }
//
//            allocationMap.put(vm, containers);
//            additionalNeededContainerInstances = remainingAdditionalContainers;
//        }


        var unleasedVmsCheapestFirst = model.getVms().values().stream()
                .filter(vm -> !allocationMap.containsKey(vm))
                .sorted(Comparator.comparingDouble(vm -> vm.getType().getCost()))
                .collect(Collectors.toList());

        // allocate additional containers on cheapest VMs first
        for (var vm : unleasedVmsCheapestFirst) {
            var remainingAdditionalContainers = new ArrayList<ContainerType>();
            var containers = new ArrayList<ContainerType>();
            for (var container : additionalNeededContainerInstances) {
//                if (!placedServices.isEmpty()) { // this would be one-for-each
//                    remainingAdditionalContainers.add(container);
//                    continue;
//                }

                if (containers.stream().map(ContainerType::getService).collect(Collectors.toList())
                        .contains(container.getService())) {
                    remainingAdditionalContainers.add(container);
                    continue;
                }

                var containersToTry = new ArrayList<>(containers);
                containersToTry.add(container);
                var containerFits = !validator.isVmOverallocated(vm, containersToTry, null);
                if (containerFits) {
                    containers.add(container);
                    MapUtils.putToMapList(allocationMap, vm, container);
//                    allocationMap.put(vm, Collections.singletonList(container));
                } else {
                    remainingAdditionalContainers.add(container);
                }
            }

            additionalNeededContainerInstances = remainingAdditionalContainers;
        }


        var remainingVms = model.getVms().values().stream()
                .filter(vm -> !allocationMap.containsKey(vm))
                .collect(Collectors.toList());

        if (remainingVms.isEmpty()) {
            log.warn("No VM remaining!");
        }

        var allocation = new Allocation(model, allocationMap);
        return allocation.getAllocatedTuples();
    }


    private ContainerType tryToPlaceServiceContainerOnVm(final VmInstance vm,
                                                         final List<ContainerType> allocatedContainers,
                                                         final Service service,
                                                         final Long missingServiceCapacity,
                                                         final Allocation previousAllocation) {
        var previousContainers = previousAllocation.getAllocatedContainersOnVm(vm).stream()
                .map(Allocation.AllocationTuple::getContainer)
                .collect(Collectors.toList());

        var servicesPresent = allocatedContainers.stream()
                .map(ContainerType::getService)
                .collect(Collectors.toList());
//        servicesPresent.addAll(previousContainers.stream()
//                .map(ContainerType::getService)
//                .collect(Collectors.toList()));
        if (servicesPresent.contains(service)) {
            return null;
        }

        var containersToTry = new ArrayList<ContainerType>();
        // add all smaller containers than required capacity
        containersToTry.addAll(service.getContainerTypes().stream()
                .filter(c -> c.getRpmCapacity() < missingServiceCapacity)
                .collect(Collectors.toList()));
        // add next biggest to required capacity
        service.getContainerTypes().stream()
                .filter(c -> c.getRpmCapacity() >= missingServiceCapacity)
                .min(Comparator.comparingLong(ContainerType::getRpmCapacity))
                .ifPresent(containersToTry::add);

        // try to  place largest container possible
        containersToTry.sort(Comparator.comparingLong(ContainerType::getRpmCapacity).reversed());

        for (var c : containersToTry) {
            var containersToTest = new ArrayList<>(allocatedContainers);
            containersToTest.add(c);
            var containerFits = !validator.isVmOverallocated(vm, containersToTest, previousContainers);
            if (containerFits) {
                return c;
            }
        }

        return null;
    }

}
