package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.Allocation;
import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.MapUtils;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.State;
import at.alexwais.cooper.scheduler.Validator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

@RequiredArgsConstructor
public class FitnessFunction {

    private final Model model;
    private final Validator validator;


    public float eval(Allocation resourceAllocation, State state) {
        // Term 1 - Minimize Cost
        var totalCost = resourceAllocation.getTotalCost();

        // Term 2 - Grace Period Cost
        var gracePeriodCost = state.getLeasedVms().stream()
                .filter(vm -> !resourceAllocation.getAllocatedVms().contains(vm))
                .map(vm -> vm.getType().getCost())
                .reduce(0f, Float::sum);

        // Term 3 - Exploiting Co-Location
//        var distanceLatency = 0f;
//        for (Allocation.AllocationTuple allocationInstanceA : resourceAllocation.getAllocatedTuples()) {
//            for (Allocation.AllocationTuple allocationInstanceB : resourceAllocation.getAllocatedTuples()) {
//                var vmA = allocationInstanceA.getVm();
//                var vmB = allocationInstanceB.getVm();
//                var containerA = allocationInstanceA.getContainer();
//                var containerB = allocationInstanceB.getContainer();
//
//                var distance = getDistanceBetween(vmA, vmB);
//                var affinity = getAffinityBetween(containerA, containerB, state.getServiceAffinity());
//                distanceLatency += (affinity * distance);
//            }
//        }

        // Term 3 - Exploiting Co-Location
        var distanceBonus = 0f;
        for (Allocation.AllocationTuple allocationInstanceA : resourceAllocation.getAllocatedTuples()) {
            for (Allocation.AllocationTuple allocationInstanceB : resourceAllocation.getAllocatedTuples()) {
                var vmA = allocationInstanceA.getVm();
                var vmB = allocationInstanceB.getVm();
                var containerA = allocationInstanceA.getContainer();
                var containerB = allocationInstanceB.getContainer();

                var distance = getDistanceBetween(vmA, vmB);
                var affinity = getAffinityBetween(containerA, containerB, state.getServiceAffinity());
                distanceBonus -= (affinity / distance);
            }
        }

        // Term 4 - Minimize Over-Provisioning
        var overProvisionedCapacity = 0;
        var allocatedContainersByService = new HashMap<Service, List<ContainerType>>();
        resourceAllocation.getAllocatedContainers().forEach(c -> {
            MapUtils.putToMapList(allocatedContainersByService, c.getService(), c);
        });
        for (Map.Entry<Service, List<ContainerType>> allocatedServiceContainers : allocatedContainersByService.entrySet()) {
            var service = allocatedServiceContainers.getKey();
            var containers = allocatedServiceContainers.getValue();

            var serviceCapacity = containers.stream()
                    .map(ContainerType::getRpmCapacity)
                    .reduce(0L, Long::sum);
            var serviceLoad = state.getTotalServiceLoad().get(service.getName());

            var overProvisionedServiceCapacity = Math.abs(serviceCapacity - serviceLoad);
            overProvisionedCapacity += overProvisionedServiceCapacity;
        }


        var violations = validator.violations(resourceAllocation, state.getTotalServiceLoad());


        var w_cost = 100;
        var w_gradePeriodWaste = 50;
        var w_distance = 100;
        var w_overProvisioning = 1;

        var term1_cost = totalCost * w_cost;
        var term2_gracePeriodCost = gracePeriodCost * w_gradePeriodWaste;
        var term3_distance = distanceBonus * w_distance;
        var term4_overProvisioning = overProvisionedCapacity * w_overProvisioning;

        var fitness = term1_cost + term2_gracePeriodCost + term3_distance + term4_overProvisioning
                + violations * 10_000_000f;

//        var fitness = totalCost * 100 - affinityBonus * 100 + overProvisionedCapacity + violations * 10_000_000f; // - containersOnSameVm * 100;
        return fitness;
    }


    private double getDistanceBetween(VmInstance vmA, VmInstance vmB) {
        if (vmA == vmB) return 1;

        var edge = model.getDataCenterDistanceGraph().getEdge(vmA.getDataCenter().getName(), vmB.getDataCenter().getName());
        var distance = model.getDataCenterDistanceGraph().getEdgeWeight(edge);
        return distance;
    }


    private double getAffinityBetween(ContainerType containerA,
                                      ContainerType containerB,
                                      SimpleWeightedGraph<String, DefaultWeightedEdge> serviceAffinityGraph) {
        var isSameService = containerA.getService().equals(containerB.getService());
        if (isSameService) {
            // No affinity between same service possible, graph contains no loops
            return 0;
        }
        var edge = serviceAffinityGraph.getEdge(containerA.getService().getName(), containerB.getService().getName());
        var affinity = serviceAffinityGraph.getEdgeWeight(edge);
        return affinity;
    }

}
