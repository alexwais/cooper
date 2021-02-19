package at.ac.tuwien.dsg.cooper.scheduler;

import at.ac.tuwien.dsg.cooper.api.Optimizer;
import at.ac.tuwien.dsg.cooper.domain.*;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.OptResult;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.WeightedPseudograph;
import org.springframework.util.StopWatch;

@Getter
public class Model {

    private final Map<String, DataCenter> dataCenters;
    private final List<VmType> vmTypes;
    private final Map<String, VmInstance> vms;
    private final Map<String, Service> services;
    private final List<ContainerType> containerTypes;
    private final Map<String, Map<String, Float>> interactionMultiplication;
    private WeightedPseudograph<String, DefaultWeightedEdge> dataCenterDistanceGraph;

    public Model(List<DataCenter> dataCenters,
                 List<Service> services,
                 Map<String, Map<String, Float>> interactionMultiplication,
                 WeightedPseudograph<String, DefaultWeightedEdge> dataCenterDistanceGraph) {
        this.dataCenters = dataCenters.stream()
                .collect(Collectors.toMap(DataCenter::getName, Function.identity()));

        this.vmTypes = dataCenters.stream()
                .flatMap(dc -> dc.getVmTypes().stream())
                .collect(Collectors.toList());

        this.vms = dataCenters.stream()
                .flatMap(dc -> dc.getVmInstances().stream())
                .collect(Collectors.toMap(VmInstance::getId, Function.identity()));

        this.services = services.stream()
                .collect(Collectors.toMap(Service::getName, Function.identity()));

        this.containerTypes = services.stream()
                .flatMap(s -> s.getContainerTypes().stream())
                .collect(Collectors.toList());

        this.interactionMultiplication = interactionMultiplication;

        this.dataCenterDistanceGraph = dataCenterDistanceGraph;
    }

    public double getDistanceBetween(VmInstance vmA, VmInstance vmB) {
        if (vmA == vmB) return 0;
        return getDistanceBetween(vmA.getDataCenter(), vmB.getDataCenter());
    }

    public double getDistanceBetween(DataCenter dcA, DataCenter dcB) {
        var edge = dataCenterDistanceGraph.getEdge(dcA.getName(), dcB.getName());
        return dataCenterDistanceGraph.getEdgeWeight(edge);
    }

    @Slf4j
    public static class FirstFitOptimizer implements Optimizer {

        private final Model model;
        private final Validator validator;

        public FirstFitOptimizer(Model model) {
            this.model = model;
            this.validator = new Validator(model);
        }


        public OptResult optimize(Allocation previousAllocation, SystemMeasures systemMeasures, Map<VmInstance, Set<Service>> cachedImages) {
            var stopWatch = new StopWatch();
            stopWatch.start();

            var allocationTuples = doIt(previousAllocation, systemMeasures);

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

            // determine needed additional containers (First-Fit Decreasing)
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

            // add additional containers to existing VMs where possible - First Fit

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

            // place remaining containers on new VMs, cheaper first
            var unleasedVmsCheapestFirst = model.getVms().values().stream()
                    .filter(vm -> !allocationMap.containsKey(vm))
                    .sorted(Comparator.comparingDouble(vm -> vm.getType().getCost()))
                    .collect(Collectors.toList());
    //        Collections.shuffle(unleasedVmsCheapestFirst);

            // try to place smaller containers first
    //        remainingContainersToPlace.sort(Comparator.comparingDouble(ContainerType::getMemory));

            // allocate additional containers on cheapest VMs first
            for (var vm : unleasedVmsCheapestFirst) {
                var remaining = new ArrayList<ContainerType>();
                var containers = new ArrayList<ContainerType>();
                for (var container : remainingContainersToPlace) {
    //                if (!placedServices.isEmpty()) { // this would be one-for-each
    //                    remainingAdditionalContainers.add(container);
    //                    continue;
    //                }

                    var containsSameService = containers.stream().map(ContainerType::getService)
                            .anyMatch(s -> s == container.getService());
                    if (containsSameService) {
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
}
