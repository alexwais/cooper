package at.alexwais.cooper.genetic;

import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.Validator;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class FitnessFunction {

    private final Model model;
    private final Validator validator;

    public float eval(Allocation resourceAllocation, SystemMeasures measures) {
        return eval(resourceAllocation, null, measures);
    }

    public float eval(Allocation resourceAllocation, Allocation previousAllocation, SystemMeasures measures) {
        // Term 1 - Minimize Cost
        var totalCost = resourceAllocation.getTotalCost();

        // Term 2 - Grace Period Cost
        // TODO use running vs. used VMs?
        var gracePeriodCost = measures.getCurrentAllocation().getUsedVms().stream()
                .filter(vm -> !resourceAllocation.getUsedVms().contains(vm))
                .map(vm -> vm.getType().getCost())
                .reduce(0f, Float::sum);

        // Term 3 - Exploiting Co-Location
        var distanceBonus = 0f;
        for (Allocation.AllocationTuple allocationInstanceA : resourceAllocation.getAllocatedTuples()) {
            for (Allocation.AllocationTuple allocationInstanceB : resourceAllocation.getAllocatedTuples()) {
                var vmA = allocationInstanceA.getVm();
                var vmB = allocationInstanceB.getVm();
                var containerA = allocationInstanceA.getContainer();
                var containerB = allocationInstanceB.getContainer();

                var distance = model.getDistanceBetween(vmA, vmB);
                if (distance > 0) { // prevent division by zero
                    var affinity = measures.getAffinityBetween(containerA.getService(), containerB.getService());
                    distanceBonus -= (affinity / distance);
                }
            }
        }

        // Term 4 - Minimize Over-Provisioning
        var overProvisionedCapacity = 0;
        var allocatedContainersByService = resourceAllocation.getAllocatedContainersByService();
        for (var allocatedServiceInstances : allocatedContainersByService.entrySet()) {
            var service = allocatedServiceInstances.getKey();
            var containerInstances = allocatedServiceInstances.getValue();

            var serviceCapacity = containerInstances.stream()
                    .map(a -> a.getContainer().getRpmCapacity())
                    .reduce(0L, Long::sum);
            var serviceLoad = measures.getTotalServiceLoad().get(service.getName());

            var overProvisionedServiceCapacity = Math.abs(serviceCapacity - serviceLoad);
            overProvisionedCapacity += overProvisionedServiceCapacity;
        }

        int violations = 0;
        if (previousAllocation == null) {
            violations = validator.neutralViolations(resourceAllocation, measures.getTotalServiceLoad());
        } else {
            violations = validator.violations(resourceAllocation, previousAllocation, measures.getTotalServiceLoad());
        }

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

}
