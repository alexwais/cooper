package at.ac.tuwien.dsg.cooper.genetic;

import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import at.ac.tuwien.dsg.cooper.scheduler.Validator;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class FitnessFunction {

    private final Model model;
    private final Validator validator;

    // TODO /1000
    private static final int W_COST = 1000;
    private static final int W_GRACE_PERIOD_WASTE = 500;
    private static final float W_DISTANCE = 0.1f;
    private static final float W_OVER_PROVISIONING = 0.1f;
    private static final float W_CONTAINER_IMAGE_CACHING = 0.1f;
    private static final float W_CONSTRAINT_VIOLATIONS = 10_000_000f;


    /**
     * Ignores the previous allocation (w.r.t. grace period cost and overallocation constraints), constituting
     * a comparable measure over time (i.e., this neutral fitness stays comparable even when the "previous allocation" changes over time)
     * @param resourceAllocation
     * @param measures
     * @return
     */
    public float evalNeutral(Allocation resourceAllocation, SystemMeasures measures) {
        return eval(resourceAllocation, null, measures, null);
    }

    public float eval(Allocation resourceAllocation, Allocation previousAllocation, SystemMeasures measures, Map<VmInstance, Set<Service>> imageCacheState) {
//        var simulation = new InteractionSimulation(model, resourceAllocation, measures);
//        try {
//            simulation.simulate();
//        } catch (Exception e) {
//
//        }


        // Term 1 - Minimize Cost
        var totalCost = resourceAllocation.getTotalCost();

        // Term 2 - Grace Period Cost
        var gracePeriodCost = previousAllocation == null ? 0 :
                previousAllocation.getUsedVms().stream()
                .filter(vm -> !resourceAllocation.getUsedVms().contains(vm))
                .map(vm -> vm.getType().getCost())
                .reduce(0f, Float::sum);

        // Term 3 - Exploiting Co-Location
        // TODO calculation style - differs from ILP objective function...?
        var distanceBonus = 0f;
        for (Allocation.AllocationTuple allocationInstanceA : resourceAllocation.getAllocatedTuples()) {
            for (Allocation.AllocationTuple allocationInstanceB : resourceAllocation.getAllocatedTuples()) {
                var vmA = allocationInstanceA.getVm();
                var vmB = allocationInstanceB.getVm();
                var containerA = allocationInstanceA.getContainer();
                var containerB = allocationInstanceB.getContainer();

                var distance = model.getDistanceBetween(vmA, vmB);
                var affinity = measures.getAffinityBetween(containerA.getService(), containerB.getService());
//                if (distance > 0) {
                    distanceBonus -= (affinity / distance);
//                } else {
//                    // prevent division by zero
//                    distanceBonus -= affinity;
//                }
            }
        }

        // Term 4 - Minimize Over-Provisioning
        long overProvisionedCapacity = 0L;
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

        // Term 5 - Container Image Caching
        long uncachedContainers = 0L;
        if (imageCacheState != null) {
            for (var alloc : resourceAllocation.getAllocatedTuples()) {
                var isImageCached = imageCacheState.getOrDefault(alloc.getVm(), Set.of())
                        .contains(alloc.getContainer().getService());
                if (!isImageCached) uncachedContainers++;
            }
        }

        long violations;
        if (previousAllocation == null) {
            violations = validator.neutralViolations(resourceAllocation, measures.getTotalServiceLoad());
        } else {
            violations = validator.violations(resourceAllocation, previousAllocation, measures.getTotalServiceLoad());
        }


        var term1_cost = totalCost * W_COST;
        var term2_gracePeriodCost = gracePeriodCost * W_GRACE_PERIOD_WASTE;
        var term3_distance = distanceBonus * W_DISTANCE;
        var term4_overProvisioning = overProvisionedCapacity * W_OVER_PROVISIONING;
        var term5_uncachedContainerImages = uncachedContainers * W_CONTAINER_IMAGE_CACHING;

        var term6_constraintViolations = violations * W_CONSTRAINT_VIOLATIONS;

        var fitness = term1_cost + term2_gracePeriodCost + term3_distance + term4_overProvisioning + term5_uncachedContainerImages
                + term6_constraintViolations;

        return fitness;
    }

}