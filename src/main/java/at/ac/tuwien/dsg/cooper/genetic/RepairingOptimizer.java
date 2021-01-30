package at.ac.tuwien.dsg.cooper.genetic;

import at.ac.tuwien.dsg.cooper.api.Optimizer;
import at.ac.tuwien.dsg.cooper.domain.ContainerType;
import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.MapUtils;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import at.ac.tuwien.dsg.cooper.scheduler.Validator;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.OptResult;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

@Slf4j
public class RepairingOptimizer implements Optimizer {

    private final Model model;
    private final Validator validator;

    public RepairingOptimizer(Model model) {
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

        // determine needed additional containers
        var containersToPlace = new ArrayList<ContainerType>();
        for (var e : missingServiceCapacity.entrySet()) {
            var service = e.getKey();
            var missingCapacity = e.getValue();

            var descendingContainerTypes = service.getContainerTypes().stream()
                    .sorted(Comparator.comparingLong(ContainerType::getRpmCapacity).reversed())
                    .collect(Collectors.toList());

            for (int i = 0; i < descendingContainerTypes.size(); i++) {
                var type = descendingContainerTypes.get(i);
                var nextSmallerType = i + 1 < descendingContainerTypes.size() ? descendingContainerTypes.get(i + 1) : null;
                while (missingCapacity > 0 &&
                        (missingCapacity >= type.getRpmCapacity() || nextSmallerType == null || missingCapacity > nextSmallerType.getRpmCapacity())) {
                    containersToPlace.add(type);
                    missingCapacity -= type.getRpmCapacity();
                }
            }
        }

        var remainingContainersToPlace = new ArrayList<ContainerType>();

        // add additional containers to existing VMs where possible
        // place largest first
        containersToPlace.sort(Comparator.comparing(ContainerType::getMemory).reversed());
        outer:
        for (var container : containersToPlace) {
            for (var entry : allocationMap.entrySet()) {
                var vm = entry.getKey();
                var allocatedContainers = entry.getValue();
                var previousContainers = previousAllocation.getAllocatedContainersOnVm(vm).stream()
                        .map(Allocation.AllocationTuple::getContainer)
                        .collect(Collectors.toList());

                if (allocatedContainers.stream().anyMatch(c -> c.getService() == container.getService())) {
                    continue;
                }

                var containersToTest = new ArrayList<>(allocatedContainers);
                containersToTest.add(container);
                var containerFits = !validator.isVmOverallocated(vm, containersToTest, previousContainers);

                if (containerFits) {
                    allocatedContainers.add(container);
                    continue outer;
                }
            }
            remainingContainersToPlace.add(container);
        }

        // place remaining containers on new VMs



        var unleasedVmsCheapestFirst = model.getVms().values().stream()
                .filter(vm -> !allocationMap.containsKey(vm))
                .sorted(Comparator.comparingDouble(vm -> vm.getType().getCost()))
                .collect(Collectors.toList());

        // allocate additional containers on cheapest VMs first (one-for-each)
        for (var vm : unleasedVmsCheapestFirst) {
            var remaining = new ArrayList<ContainerType>();
            var containers = new ArrayList<ContainerType>();
            for (var container : remainingContainersToPlace) {
//                if (!placedServices.isEmpty()) { // this would be one-for-each
//                    remainingAdditionalContainers.add(container);
//                    continue;
//                }

                if (containers.stream().map(ContainerType::getService).collect(Collectors.toList())
                        .contains(container.getService())) {
                    remaining.add(container);
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
                    remaining.add(container);
                }
            }

            remainingContainersToPlace = remaining;
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

}
