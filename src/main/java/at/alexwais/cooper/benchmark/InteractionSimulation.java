package at.alexwais.cooper.benchmark;

import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class InteractionSimulation {

    private Model model;


    public void simulate(Allocation allocation, SystemMeasures measures) {
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

        var dataCenterNodes = new ArrayList<AggregatingInteractionNode>();
        for (var dataCenter : model.getDataCenters().values()) {
            var vmNodes = new ArrayList<AggregatingInteractionNode>();
            for (var vm : dataCenter.getVmInstances()) {
                var containers = allocation.getAllocatedContainersOnVm(vm);
                if (containers == null || containers.isEmpty()) continue;

                var vmNode = vmNode(vm, containers, assignedContainerLoad);
                vmNodes.add(vmNode);
            }

            var dataCenterNode = new AggregatingInteractionNode(dataCenter.getName(), 50, model, vmNodes);
            dataCenterNodes.add(dataCenterNode);
        }

        var rootNode = new AggregatingInteractionNode("root", 100, model, dataCenterNodes);
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
        if (Math.abs(diff) >= 1) {
            throw new IllegalStateException("Total load discrepancy!");
        }
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

        return new AggregatingInteractionNode(vm.getId(), 0, model, containerNodes);
    }

}
