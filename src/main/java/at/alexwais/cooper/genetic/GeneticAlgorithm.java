package at.alexwais.cooper.genetic;

import at.alexwais.cooper.exec.Model;
import at.alexwais.cooper.exec.OptimizationResult;
import at.alexwais.cooper.exec.State;
import io.jenetics.*;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.util.Factory;
import io.jenetics.util.ISeq;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class GeneticAlgorithm {

    private final Model model;
    private final State state;
    private final Mapping mapping;
    private final List<Genotype<BitGene>> initialPopulation;
    private final FitnessFunction fitnessFunction;
    private final Factory<Genotype<BitGene>> genotypeFactory;
    private final Function<Genotype<BitGene>, Boolean[][]> decoder;
    private final Codec<Boolean[][], BitGene> codec;

    public GeneticAlgorithm(List<OptimizationResult> initialPopulation, Model model, State state) {
        this.model = model;
        this.state = state;
        this.mapping = new Mapping(model);
        this.initialPopulation = initialPopulation.stream()
                .map(mapping::optimizationResultToGenotype)
                .collect(Collectors.toList());

        this.fitnessFunction = new FitnessFunction(model, state, mapping);


        decoder = mapping::decoder;

//        decoder = gt -> gt.stream()
//                .map(ch -> ch.stream().map(BitGene::booleanValue).toArray(Boolean[]::new))
//                .toArray(Boolean[][]::new);

        //        genotypeFactory = Genotype.of(
//                BitChromosome.of(mapping.getContainerTypeCount(), 0.001).instances() // a chromosome contains container types allocated to a vm
//                        .limit(mapping.getVmCount()) // number of chromosomes = number of vms
//                        .collect(ISeq.toISeq())
//        );
        genotypeFactory = Genotype.of(
                BitChromosome.of(mapping.getContainerTypeCount() * mapping.getVmCount(), 0.001).instances() // a chromosome contains container types allocated to a vm
                        .limit(1) // number of chromosomes = number of vms
                        .collect(ISeq.toISeq())
        );

        codec = Codec.of(genotypeFactory, decoder);
    }

    public OptimizationResult run() {
//        Factory<Genotype<BitGene>> gtf =
//                Genotype.of(BitChromosome.of(10, 0.5));

        Engine<BitGene, Float> engine = Engine
                .builder(fitnessFunction::eval, codec)
                .minimizing()
                .populationSize(400)
                .offspringFraction(0.6)
                .survivorsSelector(
                        new EliteSelector<>(10,
                                new RouletteWheelSelector<BitGene, Float>())
                )
                .offspringSelector(
                                new TournamentSelector<>(3)
                )
                .alterers(
                        new MultiPointCrossover<>(0.1, 2),
                        new Mutator<>(0.02)
                )
                .maximalPhenotypeAge(100)
                .build();

//        EvolutionStatistics<Integer, ?> stats = EvolutionStatistics.ofNumber();
        var phenotype = engine.stream(initialPopulation)
//                .limit(bySteadyFitness(5))
                .limit(100)
//                .peek(stats)
                .collect(EvolutionResult.toBestPhenotype());

        log.info("Result fitness: {}", phenotype.fitness());
        var genotype = codec.decode(phenotype.genotype());

        return mapping.genotypeToOptimizationResult(genotype);
    }


}
