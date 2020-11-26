package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import io.jenetics.Genotype;
import io.jenetics.util.ISeq;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;

@Getter
public class ChromosomeMapping {

    private final Model model;
    private final SystemMeasures systemMeasures;
    private int vmCount;
    private int containerTypeCount;
    private int serviceCount;
    private final Map<VmInstance, Integer> vmsIndex = new HashMap<>();
    private final Map<Service, Integer> servicesIndex = new HashMap<>();

    private static final double LOAD_SHARE_BUFFER = 1d;

    public ChromosomeMapping(Model model, SystemMeasures systemMeasures) {
        this.model = model;
        this.systemMeasures = systemMeasures;
        this.vmCount = model.getVms().size();
        this.containerTypeCount = model.getContainerTypes().size();
        this.serviceCount = model.getServices().size();

        var vmsIterator = model.getVms().values().iterator();
        var serviceTypes = model.getServices().values().iterator();

        for (int v = 0; v < vmCount; v++) {
            vmsIndex.put(vmsIterator.next(), v);
        }
        for (int s = 0; s < serviceCount; s++) {
            servicesIndex.put(serviceTypes.next(), s);
        }
    }

    public Map<VmInstance, List<ContainerType>> serviceRowSquareDecoder(Genotype<DistributedIntegerGene> gt) {
        var matrix = gt.stream()
                .map(ch -> ch.stream().map(DistributedIntegerGene::intValue).toArray(Integer[]::new))
                .toArray(Integer[][]::new);

        var result = new HashMap<VmInstance, List<ContainerType>>();

        vmsIndex.forEach((vm, vmi) -> {
            var containerList = new ArrayList<ContainerType>();
            servicesIndex.forEach((service, si) -> {
                var geneValue = matrix[si][vmi];
                if (geneValue != 0) {
                    var zeroBasedIndex = geneValue - 1;
                    var containerType = service.getContainerTypes().get(zeroBasedIndex);
                    containerList.add(containerType);
                }
            });
            if (!containerList.isEmpty()) {
                result.put(vm, containerList);
            }
        });

        return result;
    }

    public Genotype<DistributedIntegerGene> serviceRowSquareEncoder(Map<VmInstance, List<ContainerType>> vmContainerMap) {
        var serviceVmMatrix = new int[servicesIndex.size()][vmsIndex.size()];

        for (var entry : vmContainerMap.entrySet()) {
            var vm = entry.getKey();
            var containers = entry.getValue();
            var vmi = vmsIndex.get(vm);

            containers.forEach(c -> {
                var service = c.getService();
                var si = servicesIndex.get(service);

                var zeroBasedContainerIndex = service.getContainerTypes().indexOf(c);
                var oneBasedContainerIndex = zeroBasedContainerIndex + 1;
                serviceVmMatrix[si][vmi] = oneBasedContainerIndex;
            });
        }

        return Genotype.of(
                model.getServices().values().stream()
                        .map(s -> chromosome(s, serviceVmMatrix[servicesIndex.get(s)]))
                        .collect(ISeq.toISeq())
        );
    }

    public Genotype<DistributedIntegerGene> serviceRowGenotypeFactory() {
        return Genotype.of(
                model.getServices().values().stream()
                        .map(s -> chromosome(s, vmCount))
                        .collect(ISeq.toISeq())
        );
    }


    private DistributedIntegerChromosome chromosome(Service service, int length) {
        var containerDistribution = computeContainerDistribution(service);

        return DistributedIntegerChromosome.of(containerDistribution, length);
    }

    private DistributedIntegerChromosome chromosome(Service service, int[] geneValues) {
        var containerDistribution = computeContainerDistribution(service);

        var genes = Arrays.stream(geneValues)
                .mapToObj(v -> DistributedIntegerGene.of(v, containerDistribution))
                .toArray(DistributedIntegerGene[]::new);
        return DistributedIntegerChromosome.of(genes, containerDistribution);
    }

    private EnumeratedIntegerDistribution computeContainerDistribution(Service service) {
        var containersOfService = service.getContainerTypes();
        var containerCount = containersOfService.size();
        var serviceShare = shareOfServiceLoadToOverallCapacity(service) * LOAD_SHARE_BUFFER;
        var containerShare = serviceShare / containerCount;

        // 1-based container index to probability
        var probabilityMap = containersOfService.stream()
                .collect(Collectors.toMap(c -> containersOfService.indexOf(c) + 1, c -> containerShare));

        var totalServiceProbability = probabilityMap.values().stream()
                .reduce(0d, Double::sum);
        var noAllocationProbability = 1 - totalServiceProbability;
        probabilityMap.put(0, noAllocationProbability);

        var indexArray = probabilityMap.keySet().stream().mapToInt(i -> i).toArray();
        var probabilityArray = probabilityMap.values().stream().mapToDouble(d -> d).toArray();
        var containerDistribution = new EnumeratedIntegerDistribution(indexArray, probabilityArray);
        return containerDistribution;
    }

    private double shareOfServiceLoadToOverallCapacity(Service service) {
        var overallServiceLoad = systemMeasures.getTotalServiceLoad().get(service.getName());
        var containerTypeCount = service.getContainerTypes().size();
        var overallCapacityForService = service.getContainerTypes().stream()
                // one container type per service can be allocated once on a VM
                // TODO test with/without containerTypeCount
                .map(t -> t.getRpmCapacity() * vmCount / containerTypeCount)
                .reduce(0L, Long::sum);
        var loadToCapacityRatio = (double) overallServiceLoad / (double) overallCapacityForService; // TODO weighted by container type capacity?

        return loadToCapacityRatio;
    }

}
