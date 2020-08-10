package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerConfiguration;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.exec.Model;
import at.alexwais.cooper.exec.OptimizationResult;
import io.jenetics.BitChromosome;
import io.jenetics.BitGene;
import io.jenetics.Genotype;
import java.util.*;
import lombok.Getter;

@Getter
public class Mapping {

    private final Model model;
    private int vmCount;
    private int containerTypeCount;
    private final Map<VmInstance, Integer> vmsIndex = new HashMap<>();
    private final Map<ContainerConfiguration, Integer> containerTypesIndex = new HashMap<>();

    public Mapping(Model model) {
        this.model = model;
        this.vmCount = model.getVms().size();
        this.containerTypeCount = model.getContainerTypes().size();

        var vmsIterator = model.getVms().values().iterator();
        var containerTypes = model.getContainerTypes().iterator();

        for (int v = 0; v < vmCount; v++) {
            vmsIndex.put(vmsIterator.next(), v);
        }
        for (int c = 0; c < containerTypeCount; c++) {
            containerTypesIndex.put(containerTypes.next(), c);
        }
    }

    public Map<VmInstance, List<ContainerConfiguration>> genotypeToAllocation(Boolean[][] decodedGenotype) {
        var result = new HashMap<VmInstance, List<ContainerConfiguration>>();

        vmsIndex.forEach((vm, vmi) -> {
            var containerList = new ArrayList<ContainerConfiguration>();
            containerTypesIndex.forEach((type, cti) -> {
                var isAllocated = decodedGenotype[vmi][cti];

                if (isAllocated) {
                    containerList.add(type);
                }
            });
            if (!containerList.isEmpty()) {
                result.put(vm, containerList);
            }
        });

        return result;
    }

    public OptimizationResult genotypeToOptimizationResult(Boolean[][] decodedGenotype) {
        var vmContainerAllocation = this.genotypeToAllocation(decodedGenotype);
        var vmAllocation = new HashMap<String, Boolean>();

        // Map vmContainerAllocation to result data structure
        List<OptimizationResult.AllocationTuple> allocationTuples = new ArrayList<>();
        model.getVms().values().forEach(vm -> {
            var containerList = vmContainerAllocation.getOrDefault(vm, Collections.emptyList());
            vmAllocation.put(vm.getId(), !containerList.isEmpty());
            model.getContainerTypes().forEach(type -> {
                var allocate = containerList.contains(type);
                var tuple = new OptimizationResult.AllocationTuple(vm, type, allocate);
                allocationTuples.add(tuple);
            });
        });

        return new OptimizationResult(vmAllocation, allocationTuples);
    }

    public Genotype<BitGene> optimizationResultToGenotype(OptimizationResult optResult) {
        assert optResult.getContainerAllocation().size() == vmCount * containerTypeCount;

        // Chromosome bit sets are initialized with all bits {@code false}.
        var chromosome = new BitSet(containerTypeCount * vmCount);

//        for (int i = 0; i < vmCount; i++) {
//            chromosomes.add(new BitSet(containerTypeCount));
//        }

        optResult.getContainerAllocation().stream()
                .filter(OptimizationResult.AllocationTuple::isAllocate)
                .forEach(tuple -> {
                    var vmIndex = vmsIndex.get(tuple.getVm());
                    var typeIndex = containerTypesIndex.get(tuple.getType());

                    var offset = containerTypeCount * vmIndex;
                    chromosome.set(offset + typeIndex);
                });

        return Genotype.of(BitChromosome.of(chromosome, containerTypeCount * vmCount));
    }

//    public Genotype<BitGene> optimizationResultToGenotype(OptimizationResult optResult) {
//        assert optResult.getContainerAllocation().size() == vmCount * containerTypeCount;
//
//        // Chromosome bit sets are initialized with all bits {@code false}.
//        var chromosomes = new ArrayList<BitSet>(vmCount);
//        for (int i = 0; i < vmCount; i++) {
//            chromosomes.add(new BitSet(containerTypeCount));
//        }
//
//        optResult.getContainerAllocation().stream()
//                .filter(OptimizationResult.AllocationTuple::isAllocate)
//                .forEach(tuple -> {
//                    var vmIndex = vmsIndex.get(tuple.getVm());
//                    var typeIndex = containerTypesIndex.get(tuple.getType());
//                    var chromosome = chromosomes.get(vmIndex);
//                    chromosome.set(typeIndex);
//                });
//
//        return Genotype.of(
//                chromosomes.stream()
//                        .map(c -> BitChromosome.of(c, containerTypeCount))
//                        .collect(Collectors.toList())
//        );
//    }

    public Boolean[][] decoder(Genotype<BitGene> gt) {
        var chromosome = gt.get(0);
        var matrix = new Boolean[vmCount][containerTypeCount];

        for (int i = 0; i < vmCount; i++) {
            for (int j = 0; j < containerTypeCount; j++) {
                var offset = containerTypeCount * i;
                var elem = offset + j;
                matrix[i][j] = chromosome.get(elem).allele();
            }
        }

        return matrix;
    }

}
