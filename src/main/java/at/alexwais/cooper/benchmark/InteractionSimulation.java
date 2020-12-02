package at.alexwais.cooper.benchmark;

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

                var vmNode = vmNode(containers, assignedContainerLoad);
                vmNodes.add(vmNode);
            }

            var dataCenterNode = new AggregatingInteractionNode(model, vmNodes);
            dataCenterNodes.add(dataCenterNode);
        }

        var rootNode = new AggregatingInteractionNode(model, dataCenterNodes);
        var result = rootNode.initialize();

        var remainder = result.getOverflowPerService().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getName(), Map.Entry::getValue));

        var remainderSum = remainder.values().stream().mapToDouble(v -> v).sum();
        log.info("Remainder: {}", remainder);
        if (remainderSum > 0.1) {
            throw new IllegalStateException("Remainder!");
        }
    }


    private AggregatingInteractionNode vmNode(List<Allocation.AllocationTuple> containers,
                                              Map<Allocation.AllocationTuple, Double> assignedContainerLoad) {
        var containerNodes = new ArrayList<ContainerInteractionNode>();

        for (var tuple : containers) {
            var container = tuple.getContainer();
            var assignedLoad = assignedContainerLoad.get(tuple);

            var node = new ContainerInteractionNode(model, container, assignedLoad);
            containerNodes.add(node);
        }

        return new AggregatingInteractionNode(model, containerNodes);
    }

}
