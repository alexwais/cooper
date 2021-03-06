package at.ac.tuwien.dsg.cooper.genetic;

import at.ac.tuwien.dsg.cooper.domain.ContainerType;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.FirstFitOptimizer;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import at.ac.tuwien.dsg.cooper.scheduler.Validator;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import io.jenetics.Phenotype;
import io.jenetics.engine.Constraint;
import io.jenetics.internal.math.Randoms;
import io.jenetics.util.RandomRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    private static final double REPAIRING_PROBABILITY = 0.2;
    private static final double CONSIDER_PREV_ALLOCATION_PROBABILITY = 0.2;

    private SimpleReparation simpleReparation;
    private FirstFitOptimizer firstFitOptimizer;


    public RepairingConstraint(final Model model, final SystemMeasures measures, final Validator validator, final AllocationCodec mapping, final Allocation previousAllocation) {
        this.model = model;
        this.measures = measures;
        this.validator = validator;
        this.mapping = mapping;
        this.previousAllocation = previousAllocation;

        this.simpleReparation = new SimpleReparation(model, measures, validator, previousAllocation);
        this.firstFitOptimizer = new FirstFitOptimizer(model);
    }


    @Override
    public boolean test(final Phenotype<DistributedIntegerGene, Float> individual) {
        var random = Randoms.nextDouble(0, 1, RandomRegistry.random());
        if (random > REPAIRING_PROBABILITY) return true;

        var genotype = individual.genotype();
        var allocationToTest = new Allocation(model, mapping.serviceRowSquareDecoder(genotype));

        // only consider overallocation...
        var valid = validator.calcOverallocatedVmViolations(allocationToTest, previousAllocation) == 0;
        return valid;
    }

    @Override
    public Phenotype<DistributedIntegerGene, Float> repair(final Phenotype<DistributedIntegerGene, Float> individual, final long generation) {
        var random = Randoms.nextDouble(0, 1, RandomRegistry.random());
        Map<VmInstance, List<ContainerType>> repairedAllocation;

        if (random < CONSIDER_PREV_ALLOCATION_PROBABILITY) {
            var result = firstFitOptimizer.optimize(previousAllocation, measures, Collections.emptyMap());
            repairedAllocation = result.getAllocation().getAllocationMap();
        } else {
            var genotype = individual.genotype();
            var geneticAllocation = new Allocation(model, mapping.serviceRowSquareDecoder(genotype));
            repairedAllocation = simpleReparation.repairGeneticAllocation(geneticAllocation);
        }

        var gt = mapping.serviceRowSquareEncoder(repairedAllocation);
        return Phenotype.of(gt, generation);
    }

}
