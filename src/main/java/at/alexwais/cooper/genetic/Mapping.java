package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerConfiguration;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.exec.Model;
import at.alexwais.cooper.exec.OptimizationResult;
import at.alexwais.cooper.exec.State;
import io.jenetics.BitChromosome;
import io.jenetics.BitGene;
import io.jenetics.Genotype;
import io.jenetics.util.ISeq;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class Mapping {

    private final Model model;
    private final State state;
    private int vmCount;
    private int containerTypeCount;
    private final Map<VmInstance, Integer> vmsIndex = new HashMap<>();
    private final Map<ContainerConfiguration, Integer> containerTypesIndex = new HashMap<>();

    public Mapping(Model model, State state) {
        this.model = model;
        this.state = state;
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

    public Map<VmInstance, List<ContainerConfiguration>> matrixToAllocation(Boolean[][] encodedGenotype, Boolean rowsAreContainers) {
        var result = new HashMap<VmInstance, List<ContainerConfiguration>>();

        vmsIndex.forEach((vm, vmi) -> {
            var containerList = new ArrayList<ContainerConfiguration>();
            containerTypesIndex.forEach((type, cti) -> {

                var isAllocated = rowsAreContainers ? encodedGenotype[cti][vmi] : encodedGenotype[vmi][cti];

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

    public OptimizationResult genotypeToOptimizationResult(Map<VmInstance, List<ContainerConfiguration>> genotype) {
        var vmAllocation = new HashMap<String, Boolean>();

        // Map vmContainerAllocation to result data structure
        List<OptimizationResult.AllocationTuple> allocationTuples = new ArrayList<>();
        model.getVms().values().forEach(vm -> {
            var containerList = genotype.getOrDefault(vm, Collections.emptyList());
            vmAllocation.put(vm.getId(), !containerList.isEmpty());
            model.getContainerTypes().forEach(type -> {
                var allocate = containerList.contains(type);
                var tuple = new OptimizationResult.AllocationTuple(vm, type, allocate);
                allocationTuples.add(tuple);
            });
        });

        return new OptimizationResult(vmAllocation, allocationTuples);
    }


    public Genotype<BitGene> mapContainerToVmGenotype(Genotype<BitGene> containerRowGenotype) {
        var allocation = containerRowSquareDecoder(containerRowGenotype);
        return ofDecodedGenotype(allocation);

        //                .map(r -> r.population().stream().map(p -> mapping.containerRowSquareDecoder(p.genotype())).collect(Collectors.toList()))
//                .map(m -> m.stream().map(mapping::ofDecodedGenotype).collect(Collectors.toList()));
    }

    public Genotype<BitGene> ofDecodedGenotype(Map<VmInstance, List<ContainerConfiguration>> decodedGenotype) {
        var chromosomes = new ArrayList<BitSet>();
        for (int i = 0; i < vmCount; i++) {
            chromosomes.add(new BitSet(containerTypeCount));
        }

        decodedGenotype.forEach((vm, types) -> {
            var vmIndex = vmsIndex.get(vm);
            var chromosome = chromosomes.get(vmIndex);
            types.forEach(t -> {
                var typeIndex = containerTypesIndex.get(t);
                chromosome.set(typeIndex);
            });
        });

        var wrappedChromosomes = chromosomes.stream()
                .map(c -> BitChromosome.of(c, containerTypeCount))
                .collect(Collectors.toList());
        return Genotype.of(wrappedChromosomes);
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

    public Map<VmInstance, List<ContainerConfiguration>> flatDecoder(Genotype<BitGene> gt) {
        var chromosome = gt.get(0);
        var matrix = new Boolean[vmCount][containerTypeCount];

        for (int i = 0; i < vmCount; i++) {
            for (int j = 0; j < containerTypeCount; j++) {
                var offset = containerTypeCount * i;
                var elem = offset + j;
                matrix[i][j] = chromosome.get(elem).allele();
            }
        }

        return matrixToAllocation(matrix, false);
    }

    public Map<VmInstance, List<ContainerConfiguration>> containerRowSquareDecoder(Genotype<BitGene> gt) {
        var matrix = gt.stream()
                .map(ch -> ch.stream().map(BitGene::booleanValue).toArray(Boolean[]::new))
                .toArray(Boolean[][]::new);

        return matrixToAllocation(matrix, true);
    }

    public Map<VmInstance, List<ContainerConfiguration>> vmRowSquareDecoder(Genotype<BitGene> gt) {
        var matrix = gt.stream()
                .map(ch -> ch.stream().map(BitGene::booleanValue).toArray(Boolean[]::new))
                .toArray(Boolean[][]::new);

        return matrixToAllocation(matrix, false);
    }

    public Genotype<BitGene> flatGenotypeFactory() {
        return Genotype.of(
                BitChromosome.of(containerTypeCount * vmCount, 0.01).instances() // a chromosome contains container types allocated to a vm
                        .limit(1) // number of chromosomes = number of vms
                        .collect(ISeq.toISeq())
        );
    }

    public Genotype<BitGene> vmRowGenotypeFactory() {
        var p = shareOfContainersToVmCapacity();
        return Genotype.of(
                model.getVms().values().stream()
                        .map(vm -> BitChromosome.of(containerTypeCount, p))
                        .collect(ISeq.toISeq())
        );
    }

    public Genotype<BitGene> containerRowGenotypeFactory() {
        return Genotype.of(
                model.getContainerTypes().stream()
                        .map(c -> BitChromosome.of(vmCount, shareOfServiceLoadToOverallCapacity(c)))
                        .collect(ISeq.toISeq())
        );
    }


    private double shareOfServiceLoadToOverallCapacity(ContainerConfiguration containerType) {
        var service = containerType.getService();

        var overallServiceLoad = state.getServiceLoad().get(service.getName());
        var overallCapacityForService = service.getContainerConfigurations().stream()
                .map(t -> t.getRpmCapacity() * vmCount) // a container type can be allocated once on a VM
                .reduce(0L, Long::sum);
        var loadToCapacityRatio = (double) overallServiceLoad / (double) overallCapacityForService; // TODO weighted by container type capacity?

//        var containerTypeCapacity = containerType.getRpmCapacity() * vmCount;
//        var shareOfContainerTypeInOverallCapacity = containerTypeCapacity / (double) overallCapacityForService;

//        var result =  shareOfContainerTypeInOverallCapacity * loadToCapacityRatio;
        return loadToCapacityRatio;
    }

    private double shareOfContainersToVmCapacity() {
        var overallLoad = state.getServiceLoad().values().stream()
                .reduce(0L, Long::sum);
        var overallCapacity = model.getContainerTypes().stream()
                .map(t -> t.getRpmCapacity() * vmCount) // a container type can be allocated once on a VM
                .reduce(0L, Long::sum);
        var loadRatio = (double) overallLoad / (double) overallCapacity;
        return loadRatio;

//        var containerTypes = model.getContainerTypes();
//        var vmCapacity = vm.getType().getMemory();
//        var containersCapacity = containerTypes.stream()
//                .map(c -> c.getMemory().toMegabytes())
//                .reduce(0L, Long::sum);
//        var containerCapacityRatio = (double) vmCapacity / (double) containersCapacity;
//        var result = loadRatio * containerCapacityRatio;
//        return result;
    }

}
