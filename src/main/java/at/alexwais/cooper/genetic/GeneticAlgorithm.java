package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.Optimizer;
import at.alexwais.cooper.scheduler.Validator;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import io.jenetics.*;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;


@Slf4j
public class GeneticAlgorithm implements Optimizer {

    private final Model model;
//    private final State state;
//    private final Mapping mapping;
     private final Validator validator;
    private final FitnessFunction fitnessFunction;

    // private final RetryConstraint<BitGene, Float> constraint;
//    private final Codec<Map<VmInstance, List<ContainerType>>, DistributedIntegerGene> serviceRowCodec;


    public GeneticAlgorithm(Model model, Validator validator) {
        this.model = model;

//        this.mapping = new Mapping(model, state);
//        this.initialPopulation = initialPopulation.stream()
//                .map(mapping::optimizationResultToGenotype)
//                .collect(Collectors.toList());

        this.validator = validator;
        this.fitnessFunction = new FitnessFunction(model, validator);

//        flatCodec = Codec.of(mapping.flatGenotypeFactory(), mapping::flatDecoder);
//        containerRowCodec = Codec.of(mapping.containerRowGenotypeFactory(), mapping::containerRowSquareDecoder);
//        vmRowCodec = Codec.of(mapping.vmRowGenotypeFactory(), mapping::vmRowSquareDecoder);

//        this.constraint = new RetryConstraint<>(
//                p -> validator.isAllocationValid(containerRowCodec.decode(p.genotype()), state.getServiceLoad()),
//                mapping.containerRowGenotypeFactory(),
//                1
//        );

    }

    public OptimizationResult optimize(Allocation previousAllocation, SystemMeasures systemMeasures) {
        var stopWatch = new StopWatch();
        stopWatch.start();

        var mapping = new Mapping(model, systemMeasures);
        var serviceRowCodec = Codec.of(mapping.serviceRowGenotypeFactory(), mapping::serviceRowSquareDecoder);

        Engine<DistributedIntegerGene, Float> engine1 = Engine
                .builder(allocationMap -> fitnessFunction.eval(new Allocation(model, allocationMap), previousAllocation, systemMeasures), serviceRowCodec)
//                .constraint(constraint)
                .minimizing()
                .populationSize(300)
                .offspringFraction(0.5)
                .selector(
                        new EliteSelector<>(1,
                                new TournamentSelector<DistributedIntegerGene, Float>(3)
                        )
                )
                .offspringSelector(
                        new RouletteWheelSelector<>()
                )
                .alterers(
                        new UniformCrossover<>(0.2, 0.2),
                        new Mutator<>(0.01),
                        new SwapMutator<>(0.05)
                )
                .maximalPhenotypeAge(100)
                .build();

        var phenotype = engine1.stream()
                // ate least 250 generations AND a valid result must be found
                .limit(new ValidatedGenerationLimit(250, mapping, allocationMap ->
                        validator.isAllocationValid(new Allocation(model, allocationMap), previousAllocation, systemMeasures.getTotalServiceLoad())))
                .collect(EvolutionResult.toBestPhenotype());

        log.debug("Result fitness: {}", phenotype.fitness());

        var decodedAllocationMapping = serviceRowCodec.decode(phenotype.genotype());

        stopWatch.stop();
        return new OptimizationResult(model, systemMeasures, decodedAllocationMapping, phenotype.fitness(), stopWatch.getTotalTimeMillis());
    }


    final class ValidatedGenerationLimit implements Predicate<EvolutionResult<DistributedIntegerGene, Float>>  {
        private final long generations;
        private final Function<Map<VmInstance, List<ContainerType>>, Boolean> validator;
        private final Mapping mapping;

        ValidatedGenerationLimit(final long generations, final Mapping mapping, final Function<Map<VmInstance, List<ContainerType>>, Boolean> validator) {
            this.generations = generations;
            this.mapping = mapping;
            this.validator = validator;
        }

        private final AtomicLong _current = new AtomicLong();
        private boolean extraRound = false;

        @Override
        public boolean test(final EvolutionResult<DistributedIntegerGene, Float> evolutionResult) {
            var bestPhenotype = evolutionResult.bestPhenotype();
            var decodedAllocationMapping = mapping.serviceRowSquareDecoder(bestPhenotype.genotype());

            var minimumGenerationNotReached = _current.incrementAndGet() <= generations;
            if (minimumGenerationNotReached) {
                return true;
            }

            var invalidResult = !validator.apply(decodedAllocationMapping);
            if (invalidResult) {
                log.warn("No valid result after {} generations! Continuing...", _current.get());
                extraRound = true;
                return true;
            } else {
                // we need to add an extra generation after a valid phenotype has been found,
                // otherwise the result won't make it collect() step of the stream...
                if (extraRound) {
                    extraRound = false;
                    return true;
                } else {
                    return false;
                }
            }
        }

    }


//    public OptimizationResult run() {
//        Engine<BitGene, Float> engine1 = Engine
//                .builder(g -> fitnessFunction.eval(g, state), containerRowCodec)
//                .minimizing()
//                .populationSize(500)
//                .offspringFraction(0.5)
//                .selector(
//                        new EliteSelector<>(1,
//                                new TournamentSelector<BitGene, Float>(3)
//                        )
//                )
//                .offspringSelector(
//                        new RouletteWheelSelector<>()
//                )
//                .alterers(
//                        new UniformCrossover<>(0.2, 0.2),
//                        new Mutator<>(0.01),
//                        new SwapMutator<>(0.05)
//                )
//                .maximalPhenotypeAge(100)
//                .build();
//
//        var phenotype = engine1.stream()
//                .limit(500)
//                .collect(EvolutionResult.toBestPhenotype());
//
//        log.info("Result fitness: {}", phenotype.fitness());
//
//        return new OptimizationResult(model, containerRowCodec.decode(phenotype.genotype()));
//    }

}
