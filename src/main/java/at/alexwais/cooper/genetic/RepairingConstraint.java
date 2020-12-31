package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.MapUtils;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.Validator;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import io.jenetics.Phenotype;
import io.jenetics.engine.Constraint;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Repairs overallocated VMs.
 */
@Slf4j
public final class RepairingConstraint implements Constraint<DistributedIntegerGene, Float> {

    private final Model model;
    private final SystemMeasures measures;
    private final Validator validator;
    private final AllocationCodec mapping;
    private final Allocation previousAllocation;


    public RepairingConstraint(final Model model, final SystemMeasures measures, final Validator validator, final AllocationCodec mapping, final Allocation previousAllocation) {
        this.model = model;
        this.measures = measures;
        this.validator = validator;
        this.mapping = mapping;
        this.previousAllocation = previousAllocation;
    }


    @Override
    public boolean test(final Phenotype<DistributedIntegerGene, Float> individual) {
        var genotype = individual.genotype();
        var allocationToTest = new Allocation(model, mapping.serviceRowSquareDecoder(genotype));

        // TODO test overalloc vs. underprovisioning vs. both?
        // for now only consider overallocation...
        var valid = validator.calcOverallocatedVmViolations(allocationToTest, previousAllocation) == 0;
//        var valid = validator.isAllocationValid(allocationToTest, previousAllocation, measures.getTotalServiceLoad());
        return valid;
    }

    @Override
    public Phenotype<DistributedIntegerGene, Float> repair(final Phenotype<DistributedIntegerGene, Float> individual, final long generation) {
        var genotype = individual.genotype();
        var allocation = new Allocation(model, mapping.serviceRowSquareDecoder(genotype));

        Map<VmInstance, List<ContainerType>> repairedAllocation = new HashMap<>();
		List<ContainerType> containersToMove = new ArrayList<>();

		var allocations = new ArrayList<>(allocation.getAllocationMap().entrySet());
        Collections.shuffle(allocations);

		// Remove excess containers from VMs
        for (var e : allocations) {
            var vm = e.getKey();
            var containers = e.getValue();
            Collections.shuffle(containers);

            var previousContainers = previousAllocation.getAllocatedContainersOnVm(vm).stream()
                    .map(Allocation.AllocationTuple::getContainer)
                    .collect(Collectors.toList());

            var isVmOverallocated = validator.isVmOverallocated(vm, containers, previousContainers);

            if (!isVmOverallocated) {
				repairedAllocation.put(vm, containers);
			} else {
            	var repairedContainers = new ArrayList<ContainerType>();
				for (var container : containers) {
					var containersToTest = new ArrayList<>(repairedContainers);
					containersToTest.add(container);
					var containerFits = !validator.isVmOverallocated(vm, containersToTest, previousContainers);
					if (containerFits) {
						repairedContainers.add(container);
					} else {
						containersToMove.add(container);
					}
				}
				repairedAllocation.put(vm, repairedContainers);
			}
        }

        // calc capacity of containers to move
        var containersToMoveByService = new HashMap<Service, List<ContainerType>>();
        containersToMove.forEach(c -> {
            MapUtils.putToMapList(containersToMoveByService, c.getService(), c);
        });
        var movingContainersCapacity = containersToMoveByService.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().stream().map(ContainerType::getRpmCapacity).reduce(0L, Long::sum)
        ));

        // calc missing service capacity (considering capacity of containers to move)
        var missingServiceCapacity = validator.missingCapacityPerService(new Allocation(model, repairedAllocation), measures.getTotalServiceLoad())
                .entrySet().stream()
                .filter(e -> e.getValue() - movingContainersCapacity.getOrDefault(e.getKey(), 0L) > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // compute additionally needed containers to fulfill load requirements
        var additionalNeededContainers = new ArrayList<ContainerType>();
        for (var entry : missingServiceCapacity.entrySet()) {
            var service = entry.getKey();
            var missingCapacity = entry.getValue();

            while (missingCapacity > 0) {
                var randomContainer = service.getContainerTypes().stream()
                        .collect(Collectors.collectingAndThen(Collectors.toList(), collected -> {
                            Collections.shuffle(collected);
                            return collected;
                        })).get(0);
                additionalNeededContainers.add(randomContainer);
                missingCapacity -= randomContainer.getRpmCapacity();
            }
        }

        // containersToMove + additionalNeededContainers -> that's the containers we need to place
        var containersToAllocate = Stream.concat(containersToMove.stream(), additionalNeededContainers.stream())
                .collect(Collectors.toList());

        // Try allocating on (already allocated) VMs
        for (Map.Entry<VmInstance, List<ContainerType>> e : repairedAllocation.entrySet()) {
            var vm = e.getKey();
            var presentContainers = e.getValue();

            var remainingContainers = tryToFitContainersOnVm(vm, presentContainers, containersToAllocate);
            containersToAllocate = remainingContainers;
        }


        var abandonedVms = previousAllocation.getRunningVms().stream()
                .filter(vm -> !repairedAllocation.containsKey(vm))
                .collect(Collectors.toList());
        var otherVms = model.getVms().values().stream()
                .filter(vm -> !repairedAllocation.containsKey(vm))
                .filter(vm -> !abandonedVms.contains(vm))
                .collect(Collectors.toList());
        var nonAllocatedVms = Stream.concat(abandonedVms.stream(), otherVms.stream())
                .collect(Collectors.collectingAndThen(Collectors.toList(), collected -> {
                    Collections.shuffle(collected);
                    return collected;
                }));


        // Try allocating on any other / abandoned VMs
        for (var vm: nonAllocatedVms) {
            var allocatedContainers = new ArrayList<ContainerType>();

            var remainingContainers = tryToFitContainersOnVm(vm, allocatedContainers, containersToAllocate);

            if (!allocatedContainers.isEmpty()) {
                repairedAllocation.put(vm, allocatedContainers);
            }
            containersToAllocate = remainingContainers;
        }

//        log.debug("Remaining excess containers after reparation: {} (moved {})", excessContainers.size(), excessBeforeMoving);
//        if (!validator.isAllocationValid(new Allocation(model, repairedAllocation), previousAllocation, measures.getTotalServiceLoad())) {
//            log.warn("Invalid!");
//        }

        var gt = mapping.serviceRowSquareEncoder(repairedAllocation);
        var pt = Phenotype.<DistributedIntegerGene, Float>of(gt, generation);

        return pt;
    }



    private List<ContainerType> tryToFitContainersOnVm(final VmInstance vm, final List<ContainerType> allocatedContainers, final List<ContainerType> containersToFit) {
        var presentServices = allocatedContainers.stream()
                .map(ContainerType::getService)
                .collect(Collectors.toList());
        var previousContainers = previousAllocation.getAllocatedContainersOnVm(vm).stream()
                .map(Allocation.AllocationTuple::getContainer)
                .collect(Collectors.toList());

        var remainingContainersToMove = new ArrayList<ContainerType>();
        for (int i = 0; i < containersToFit.size(); i++) {
            var c = containersToFit.get(i);

            // Only 1 container instance per service can be allocated on the same VM
            if (presentServices.contains(c.getService())) {
                remainingContainersToMove.add(c);
                continue;
            }

            var containersToTest = new ArrayList<>(allocatedContainers);
            containersToTest.add(c);
            var containerFits = !validator.isVmOverallocated(vm, containersToTest, previousContainers);
            if (containerFits) {
                allocatedContainers.add(c);
                presentServices.add(c.getService());
            } else {
                remainingContainersToMove.add(c);
            }
        }

        return remainingContainersToMove;
    }

}
