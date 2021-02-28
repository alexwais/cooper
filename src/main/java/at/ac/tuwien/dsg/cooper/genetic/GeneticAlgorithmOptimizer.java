package at.ac.tuwien.dsg.cooper.genetic;

import at.ac.tuwien.dsg.cooper.api.Optimizer;
import at.ac.tuwien.dsg.cooper.config.OptimizationConfig;
import at.ac.tuwien.dsg.cooper.domain.ContainerType;
import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import at.ac.tuwien.dsg.cooper.scheduler.Validator;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.OptResult;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class GeneticAlgorithmOptimizer implements Optimizer {

    private final Model model;
    private final OptimizationConfig config;
    //    private final State state;
//    private final Mapping mapping;
    private final Validator validator;
    private final FitnessFunction fitnessFunctionInstance;

//     private final RetryConstraint<DistributedIntegerGene, Float> constraint;
//    private final Codec<Map<VmInstance, List<ContainerType>>, DistributedIntegerGene> serviceRowCodec;


    public GeneticAlgorithmOptimizer(Model model, OptimizationConfig config, Validator validator) {
        this.model = model;
        this.config = config;

//        this.mapping = new Mapping(model, state);
//        this.initialPopulation = initialPopulation.stream()
//                .map(mapping::optimizationResultToGenotype)
//                .collect(Collectors.toList());

        this.validator = validator;
        this.fitnessFunctionInstance = new FitnessFunction(model, validator, config.getGaLatencyWeight());

//        flatCodec = Codec.of(mapping.flatGenotypeFactory(), mapping::flatDecoder);
//        containerRowCodec = Codec.of(mapping.containerRowGenotypeFactory(), mapping::containerRowSquareDecoder);
//        vmRowCodec = Codec.of(mapping.vmRowGenotypeFactory(), mapping::vmRowSquareDecoder);


    }

    public OptResult optimize(Allocation previousAllocation, SystemMeasures systemMeasures, Map<VmInstance, Set<Service>> imageCacheState) {
        var codec = new AllocationCodec(model, systemMeasures);

        var repairingConstraint = new RepairingConstraint(model, systemMeasures, validator, codec, previousAllocation);

        var fitnessFunction = new Function<Map<VmInstance, List<ContainerType>>, Float>() {
            @Override
            public Float apply(Map<VmInstance, List<ContainerType>> allocationMap) {
                return fitnessFunctionInstance.eval(
                        new Allocation(model, allocationMap),
                        previousAllocation,
                        systemMeasures,
                        imageCacheState,
                        config.getStrategy() == OptimizationConfig.OptimizationAlgorithm.GA_NC
                );
            }
        };

        var bestPhenotype = Engine
                .builder(fitnessFunction, codec)
                .minimizing()
                .constraint(repairingConstraint)
                .populationSize(25)
                .survivorsFraction(0.5)
                .maximalPhenotypeAge(60)
                .survivorsSelector(
                        new EliteSelector<>(1,
                                new TournamentSelector<DistributedIntegerGene, Float>(3)
                        )
                )
                .offspringSelector(
                        new RouletteWheelSelector<>()
                )
                .alterers(
                        new UniformCrossover<>(0.05, 0.05),
                        new SwapMutator<>(0.05),
                        new Mutator<>(0.05)
                )
                .build()
                .stream()
                // at least 250 generations AND a valid result must be found
//                .limit(new ValidatedGenerationLimit(250, mapping, allocationMap ->
//                        validator.isAllocationValid(new Allocation(model, allocationMap), previousAllocation, systemMeasures.getTotalServiceLoad())))
                .limit(120)
//                .peek(c -> log.info("generation {}", c.generation()))
                .collect(EvolutionResult.toBestPhenotype());

        log.debug("Result fitness: {}", bestPhenotype.fitness());

        var decodedAllocationMapping = codec.decode(bestPhenotype.genotype());

        return new OptResult(model, systemMeasures, decodedAllocationMapping);
    }

}
