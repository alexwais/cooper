package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.exec.Model;
import at.alexwais.cooper.exec.OptimizationResult;
import at.alexwais.cooper.exec.State;
import at.alexwais.cooper.exec.Validator;
import io.jenetics.*;
import io.jenetics.engine.*;
import io.jenetics.util.ISeq;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class GeneticAlgorithm {

    private final Model model;
    private final State state;
    private final Mapping mapping;
    private final Validator validator;
    private final FitnessFunction fitnessFunction;

    private final RetryConstraint<BitGene, Float> constraint;
    private final Codec<Map<VmInstance, List<ContainerType>>, BitGene> vmRowCodec;
    private final Codec<Map<VmInstance, List<ContainerType>>, BitGene> containerRowCodec;
    private final Codec<Map<VmInstance, List<ContainerType>>, BitGene> flatCodec;
    private final Codec<Map<VmInstance, List<ContainerType>>, BitGene> codec;

    private static final int ENCODING_STRATEGY = 1;

    public GeneticAlgorithm(List<OptimizationResult> initialPopulation, Model model, State state, Validator validator) {
        this.model = model;
        this.state = state;
        this.validator = validator;

        this.mapping = new Mapping(model, state);
//        this.initialPopulation = initialPopulation.stream()
//                .map(mapping::optimizationResultToGenotype)
//                .collect(Collectors.toList());

        this.fitnessFunction = new FitnessFunction(model, validator);

        flatCodec = Codec.of(mapping.flatGenotypeFactory(), mapping::flatDecoder);
        containerRowCodec = Codec.of(mapping.containerRowGenotypeFactory(), mapping::containerRowSquareDecoder);
        vmRowCodec = Codec.of(mapping.vmRowGenotypeFactory(), mapping::vmRowSquareDecoder);

        this.constraint = new RetryConstraint<>(
                p -> validator.isAllocationValid(containerRowCodec.decode(p.genotype()), state.getServiceLoad()),
                mapping.containerRowGenotypeFactory(),
                1
        );

        if (ENCODING_STRATEGY == 0) {
            codec = flatCodec;
        } else if (ENCODING_STRATEGY == 1) {
            codec = containerRowCodec;
        } else if (ENCODING_STRATEGY == 2) {
            codec = vmRowCodec;
        }
    }

    public OptimizationResult run() {
        Engine<BitGene, Float> engine1 = Engine
                .builder(g -> fitnessFunction.eval(g ,state), containerRowCodec)
//                .constraint(constraint)
                .minimizing()
                .populationSize(500)
                .offspringFraction(0.5)
                .survivorsSelector(
                        new EliteSelector<>(1,
                                new TournamentSelector<BitGene, Float>(3)
                        )
                )
                .offspringSelector(
                        new RouletteWheelSelector<BitGene, Float>()
                )
                .alterers(
                        new UniformCrossover<>(0.2, 0.2),
                        new Mutator<>(0.01),
                        new SwapMutator<>(0.05)
                )
                .maximalPhenotypeAge(100)
                .build();

        Engine<BitGene, Float> engine2 = Engine
                .builder(g -> fitnessFunction.eval(g ,state), vmRowCodec)
                .minimizing()
                .populationSize(500)
                .offspringFraction(0.4)
                .survivorsSelector(
                        new EliteSelector<>(1,
                                new RouletteWheelSelector<BitGene, Float>()
                        )
                )
                .offspringSelector(
                        new TournamentSelector<>(3)
                )
                .alterers(
                        new UniformCrossover(0.5, 0.2),
                        new SwapMutator<>(0.05)
                )
                .maximalPhenotypeAge(100)
                .build();


//        var stream1Result = new ArrayDeque<>(engine1.stream()
//                .limit(200)
//                .map(this::mapEvolutionStart)
//                .collect(Collectors.toList()));
//
//        var phenotype = engine2.stream(stream1Result::pop)
//                .limit(200)
//                .collect(EvolutionResult.toBestPhenotype());
//        var decodedGenotype = vmRowCodec.decode(phenotype.genotype());

        var phenotype = engine1.stream()
                .limit(500)
                .collect(EvolutionResult.toBestPhenotype());

        log.info("Result fitness: {}", phenotype.fitness());


        return new OptimizationResult(model, containerRowCodec.decode(phenotype.genotype()));
    }

    private EvolutionStart<BitGene, Float> mapEvolutionStart(EvolutionResult<BitGene, Float> evo) {
        ISeq<Phenotype<BitGene, Float>> mappedPopulation = evo.population().stream()
                .map(phenotype -> (Phenotype<BitGene, Float>) Phenotype.of(mapping.mapContainerToVmGenotype(phenotype.genotype()), phenotype.generation()))
                .collect(ISeq.toISeq());

        return EvolutionStart.of(mappedPopulation, evo.generation());
    }

}
