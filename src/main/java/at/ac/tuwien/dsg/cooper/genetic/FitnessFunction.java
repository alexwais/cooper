package at.ac.tuwien.dsg.cooper.genetic;

import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.interaction.InteractionSimulation;
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

    // TODO finalize doc
    private static final float W_COST = 1;
    private static final float W_GRACE_PERIOD_WASTE = 0.4f;
    private final float wLatency;
    private static final float W_OVER_PROVISIONING = 0.0001f;
    private static final float W_CONTAINER_IMAGE_CACHING = 0.0001f;
    private static final float W_CONSTRAINT_VIOLATIONS = 1_000f; // prev. 10_000_000f;


    /**
     * Ignores the previous allocation (w.r.t. grace period cost and overallocation constraints), constituting
     * a comparable measure over time (i.e., this neutral fitness stays comparable even when the "previous allocation" changes over time)
     * @param resourceAllocation
     * @param measures
     * @return
     */
    public float evalNeutral(Allocation resourceAllocation, SystemMeasures measures) {
        return eval(resourceAllocation, null, measures, null, false);
    }

    public float eval(Allocation resourceAllocation,
                      Allocation previousAllocation,
                      SystemMeasures measures,
                      Map<VmInstance, Set<Service>> imageCacheState,
                      boolean skipColocation) {

        var enableColocation = !skipColocation;

        // Term 1 - Minimize Cost
        var totalCost = resourceAllocation.getTotalCost();

        // Term 2 - Grace Period Cost
        var gracePeriodCost = previousAllocation == null ? 0 :
                previousAllocation.getUsedVms().stream()
                .filter(vm -> !resourceAllocation.getUsedVms().contains(vm))
                .map(vm -> vm.getType().getCost())
                .reduce(0f, Float::sum);

        // Term 3 - Exploiting Co-Location
        Float latency = null;
        if (enableColocation) {
            try {
                var simulation = new InteractionSimulation(model, resourceAllocation, measures);
                simulation.simulate();
                latency = simulation.getInteractionRecorder().getAverageLatency().floatValue();
            } catch (IllegalStateException ex) {
                latency = W_CONSTRAINT_VIOLATIONS;
            }
        }


        // calculation style differs from ILP objective function...?
//        var distanceBonus = 0f;
//        for (Allocation.AllocationTuple allocationInstanceA : resourceAllocation.getAllocatedTuples()) {
//            for (Allocation.AllocationTuple allocationInstanceB : resourceAllocation.getAllocatedTuples()) {
//                var vmA = allocationInstanceA.getVm();
//                var vmB = allocationInstanceB.getVm();
//                var containerA = allocationInstanceA.getContainer();
//                var containerB = allocationInstanceB.getContainer();
//
//                var distance = model.getDistanceBetween(vmA, vmB);
//                var affinity = measures.getAffinityBetween(containerA.getService(), containerB.getService());
//                if (distance > 0) {
//                    distanceBonus -= (affinity / distance);
//                } else {
//                    // prevent division by zero
//                    distanceBonus -= affinity;
//                }
//            }
//        }
//        var internalLoad = measures.getInternalServiceLoad().values().stream().reduce(Integer::sum).get();
//        distanceBonus = distanceBonus / internalLoad;

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
//        var term3_colocation = distanceBonus * 0.5f;
        var term4_overProvisioning = overProvisionedCapacity * W_OVER_PROVISIONING;
        var term5_uncachedContainerImages = uncachedContainers * W_CONTAINER_IMAGE_CACHING;
        var term6_constraintViolations = violations * W_CONSTRAINT_VIOLATIONS;

        var fitness = term1_cost + term2_gracePeriodCost + term4_overProvisioning + term5_uncachedContainerImages
                + term6_constraintViolations;

        if (enableColocation) {
            var term3_colocation = (latency * wLatency);
//            var term3_colocation = (distanceBonus * wLatency);
            fitness += term3_colocation;
        }

        return fitness;
    }

}
