package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.Allocation;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.State;
import at.alexwais.cooper.scheduler.Validator;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import io.jenetics.*;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;


@Slf4j
public class GeneticAlgorithm {

    private final Model model;
//    private final State state;
//    private final Mapping mapping;
    // private final Validator validator;
    private final FitnessFunction fitnessFunction;

    // private final RetryConstraint<BitGene, Float> constraint;
//    private final Codec<Map<VmInstance, List<ContainerType>>, DistributedIntegerGene> serviceRowCodec;


    public GeneticAlgorithm(Model model, Validator validator) {
        this.model = model;

//        this.mapping = new Mapping(model, state);
//        this.initialPopulation = initialPopulation.stream()
//                .map(mapping::optimizationResultToGenotype)
//                .collect(Collectors.toList());

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

    public OptimizationResult run(State state) {
        var stopWatch = new StopWatch();
        stopWatch.start();

        var mapping = new Mapping(model, state);
        var serviceRowCodec = Codec.of(mapping.serviceRowGenotypeFactory(), mapping::serviceRowSquareDecoder);

        Engine<DistributedIntegerGene, Float> engine1 = Engine
                .builder(allocationMap -> fitnessFunction.eval(new Allocation(model, allocationMap), state), serviceRowCodec)
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
                .limit(250)
                .collect(EvolutionResult.toBestPhenotype());

        log.info("Result fitness: {}", phenotype.fitness());

        var decodedAllocationMapping = serviceRowCodec.decode(phenotype.genotype());

        stopWatch.stop();
        return new OptimizationResult(model, decodedAllocationMapping, phenotype.fitness(), stopWatch.getTotalTimeMillis());
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
