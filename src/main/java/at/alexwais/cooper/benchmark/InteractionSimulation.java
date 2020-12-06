package at.alexwais.cooper.benchmark;

import at.alexwais.cooper.domain.DataCenter;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class InteractionSimulation {

    private final Model model;
    private final Allocation allocation;
    private final SystemMeasures measures;

    @Getter
    private final InteractionRecorder interactionRecorder = new InteractionRecorder();


    public void simulate() {
        var assignedContainerLoad = computeAssignedContainerLoad();

        var dataCenterNodes = new HashMap<AggregatingInteractionNode, DataCenter>();
        for (var dataCenter : model.getDataCenters().values()) {
            var dataCenterNode = dataCenterNode(dataCenter, assignedContainerLoad);
            dataCenterNodes.put(dataCenterNode, dataCenter);
        }

        var distanceGraph = new HashMap<InteractionNode, Map<InteractionNode, Integer>>();
        for (var s : dataCenterNodes.entrySet()) {
            var sourceNode = s.getKey();
            var sourceDataCenter = s.getValue();
            for (var t : dataCenterNodes.entrySet()) {
                var targetNode = t.getKey();
                var targetDataCenter = t.getValue();

                var distanceLatency = (int) model.getDistanceBetween(sourceDataCenter, targetDataCenter);
                var toMap = distanceGraph.getOrDefault(sourceNode, new HashMap<>());
                toMap.put(targetNode, distanceLatency);
                distanceGraph.put(sourceNode, toMap);
            }
        }

        var rootNode = new AggregatingInteractionNode("root", model, new ArrayList<>(dataCenterNodes.keySet()), distanceGraph, interactionRecorder);
        var result = rootNode.initialize();

        var processedLoad = result.getProcessedLoad().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getName(), Map.Entry::getValue));
        var remainingOverflow = result.getInducedOverflow().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getName(), Map.Entry::getValue));

        var processedSum = processedLoad.values().stream().mapToDouble(v -> v).sum();
        var totalProcessedSum = rootNode.totalProcessedLoad.values().stream().mapToDouble(v -> v).sum();
        var remainderSum = remainingOverflow.values().stream().mapToDouble(v -> v).sum();
        log.info("Processed external load: {} | Processed total load: {} | Remaining overflow: {}", processedSum, totalProcessedSum, remainingOverflow);

        if (remainderSum > 0.1) {
            throw new IllegalStateException("Remainder!");
        }
        var diff = measures.getTotalSystemLoad() - totalProcessedSum;
        if (Math.abs(diff) > 0.1) {
            throw new IllegalStateException("Total load discrepancy!");
        }
    }

    private Map<Allocation.AllocationTuple, Double> computeAssignedContainerLoad() {
        var assignedContainerLoad = new HashMap<Allocation.AllocationTuple, Double>();
        for (var serviceAndLoad : measures.getExternalServiceLoad().entrySet()) {
            var serviceName = serviceAndLoad.getKey();
            var service = model.getServices().get(serviceName);

            var overallServiceLoad = serviceAndLoad.getValue();
            var overallServiceCapacity = allocation.getServiceCapacity().get(serviceName);

            var availableContainers = allocation.getAllocatedContainersByService().getOrDefault(service, Collections.emptyList());

            // Assign external load for each container
            for (var c : availableContainers) {
                var containerShare = (double) c.getContainer().getRpmCapacity() / (double) overallServiceCapacity;
                var proRataLoad = overallServiceLoad * containerShare;
                assignedContainerLoad.put(c, proRataLoad);
            }
        }
        return assignedContainerLoad;
    }


    private AggregatingInteractionNode dataCenterNode(DataCenter dataCenter, Map<Allocation.AllocationTuple, Double> assignedContainerLoad) {
        var vmNodes = new ArrayList<AggregatingInteractionNode>();
        for (var vm : dataCenter.getVmInstances()) {
            var containers = allocation.getAllocatedContainersOnVm(vm);
            if (containers == null || containers.isEmpty()) continue;

            var vmNode = vmNode(vm, containers, assignedContainerLoad);
            vmNodes.add(vmNode);
        }

        var dataCenterInternalLatency = (int) model.getDistanceBetween(dataCenter, dataCenter);
        var distanceGraph = new HashMap<InteractionNode, Map<InteractionNode, Integer>>();
        for (var sourceNode : vmNodes) {
            for (var targetNode : vmNodes) {
                var toMap = distanceGraph.getOrDefault(sourceNode, new HashMap<>());
                toMap.put(targetNode, dataCenterInternalLatency);
                distanceGraph.put(sourceNode, toMap);
            }
        }

        return new AggregatingInteractionNode(dataCenter.getName(), model, vmNodes, distanceGraph, interactionRecorder);
    }

    private AggregatingInteractionNode vmNode(VmInstance vm,
                                              List<Allocation.AllocationTuple> containers,
                                              Map<Allocation.AllocationTuple, Double> assignedContainerLoad) {
        var containerNodes = new ArrayList<ContainerInteractionNode>();
        for (var tuple : containers) {
            var container = tuple.getContainer();
            var assignedLoad = assignedContainerLoad.get(tuple);

            var node = new ContainerInteractionNode(container.getLabel(), model, container, assignedLoad);
            containerNodes.add(node);

        }

        var distanceGraph = new HashMap<InteractionNode, Map<InteractionNode, Integer>>();
        for (var sourceNode : containerNodes) {
            for (var targetNode : containerNodes) {
                var toMap = distanceGraph.getOrDefault(sourceNode, new HashMap<>());
                toMap.put(targetNode, 1);
                distanceGraph.put(sourceNode, toMap);
            }
        }

        return new AggregatingInteractionNode(vm.getId(), model, containerNodes, distanceGraph, interactionRecorder);
    }

}
