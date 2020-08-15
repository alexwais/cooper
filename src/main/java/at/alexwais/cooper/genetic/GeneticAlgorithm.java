package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerConfiguration;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.exec.Model;
import at.alexwais.cooper.exec.OptimizationResult;
import at.alexwais.cooper.exec.State;
import io.jenetics.*;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStart;
import io.jenetics.util.ISeq;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class GeneticAlgorithm {

    private final Model model;
    private final State state;
    private final Mapping mapping;
    //    private final List<Genotype<BitGene>> initialPopulation;
    private final FitnessFunction fitnessFunction;
    private final Codec<Map<VmInstance, List<ContainerConfiguration>>, BitGene> vmRowCodec;
    private final Codec<Map<VmInstance, List<ContainerConfiguration>>, BitGene> containerRowCodec;
    private final Codec<Map<VmInstance, List<ContainerConfiguration>>, BitGene> flatCodec;
    private final Codec<Map<VmInstance, List<ContainerConfiguration>>, BitGene> codec;

    private static final int ENCODING_STRATEGY = 1;

    public GeneticAlgorithm(List<OptimizationResult> initialPopulation, Model model, State state) {
        this.model = model;
        this.state = state;
        this.mapping = new Mapping(model, state);
//        this.initialPopulation = initialPopulation.stream()
//                .map(mapping::optimizationResultToGenotype)
//                .collect(Collectors.toList());

        this.fitnessFunction = new FitnessFunction(model, state, mapping);

        flatCodec = Codec.of(mapping.flatGenotypeFactory(), mapping::flatDecoder);
        containerRowCodec = Codec.of(mapping.containerRowGenotypeFactory(), mapping::containerRowSquareDecoder);
        vmRowCodec = Codec.of(mapping.vmRowGenotypeFactory(), mapping::vmRowSquareDecoder);

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
                .builder(fitnessFunction::eval, containerRowCodec)
                .minimizing()
                .populationSize(400)
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
                        new MultiPointCrossover<>(0.2, 1),
                        new Mutator<>(0.03)
                )
                .maximalPhenotypeAge(100)
                .build();

        Engine<BitGene, Float> engine2 = Engine
                .builder(fitnessFunction::eval, vmRowCodec)
                .minimizing()
                .populationSize(500)
                .offspringFraction(0.5)
                .survivorsSelector(
                        new EliteSelector<>(1,
                                new RouletteWheelSelector<BitGene, Float>()
                        )
                )
                .offspringSelector(
                        new TournamentSelector<>(3)
                )
                .alterers(
                        new SinglePointCrossover(0.9),
                        new Mutator<>(0.02)
                )
                .maximalPhenotypeAge(100)
                .build();


        var stream1Result = new ArrayDeque<>(engine1.stream()
                .limit(200)
                .map(this::mapEvolutionStart)
                .collect(Collectors.toList()));

        var phenotype = engine2.stream(stream1Result::pop)
                .limit(200)
                .collect(EvolutionResult.toBestPhenotype());
        var decodedGenotype = vmRowCodec.decode(phenotype.genotype());

        //        var phenotype = engine1.stream()
//                .limit(500)
//                .collect(EvolutionResult.toBestPhenotype());
//        var decodedGenotype = containerRowCodec.decode(phenotype.genotype());


        //        log.info("Result fitness: {}", phenotype.fitness());
        return mapping.genotypeToOptimizationResult(decodedGenotype);
    }

    private EvolutionStart<BitGene, Float> mapEvolutionStart(EvolutionResult<BitGene, Float> evo) {
        ISeq<Phenotype<BitGene, Float>> mappedPopulation = evo.population().stream()
                .map(phenotype -> (Phenotype<BitGene, Float>) Phenotype.of(mapping.mapContainerToVmGenotype(phenotype.genotype()), phenotype.generation()))
                .collect(ISeq.toISeq());

        return EvolutionStart.of(mappedPopulation, evo.generation());
    }

}