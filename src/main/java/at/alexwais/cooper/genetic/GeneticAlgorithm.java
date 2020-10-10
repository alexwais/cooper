package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.exec.Model;
import at.alexwais.cooper.exec.OptimizationResult;
import at.alexwais.cooper.exec.State;
import at.alexwais.cooper.exec.Validator;
import io.jenetics.*;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class GeneticAlgorithm {

    private final Model model;
    private final State state;
    private final Mapping mapping;
    // private final Validator validator;
    private final FitnessFunction fitnessFunction;

    // private final RetryConstraint<BitGene, Float> constraint;
    private final Codec<Map<VmInstance, List<ContainerType>>, DistributedIntegerGene> serviceRowCodec;


    public GeneticAlgorithm(Model model, State state, Validator validator) {
        this.model = model;
        this.state = state;

        this.mapping = new Mapping(model, state);
//        this.initialPopulation = initialPopulation.stream()
//                .map(mapping::optimizationResultToGenotype)
//                .collect(Collectors.toList());

        this.fitnessFunction = new FitnessFunction(model, validator);

//        flatCodec = Codec.of(mapping.flatGenotypeFactory(), mapping::flatDecoder);
//        containerRowCodec = Codec.of(mapping.containerRowGenotypeFactory(), mapping::containerRowSquareDecoder);
        serviceRowCodec = Codec.of(mapping.serviceRowGenotypeFactory(), mapping::serviceRowSquareDecoder);
//        vmRowCodec = Codec.of(mapping.vmRowGenotypeFactory(), mapping::vmRowSquareDecoder);

//        this.constraint = new RetryConstraint<>(
//                p -> validator.isAllocationValid(containerRowCodec.decode(p.genotype()), state.getServiceLoad()),
//                mapping.containerRowGenotypeFactory(),
//                1
//        );

    }

    public OptimizationResult run() {
        Engine<DistributedIntegerGene, Float> engine1 = Engine
                .builder(g -> fitnessFunction.eval(g, state), serviceRowCodec)
//                .constraint(constraint)
                .minimizing()
                .populationSize(250)
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

        return new OptimizationResult(model, serviceRowCodec.decode(phenotype.genotype()));
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
