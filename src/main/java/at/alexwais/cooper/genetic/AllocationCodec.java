package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import io.jenetics.Genotype;
import io.jenetics.engine.Codec;
import io.jenetics.util.Factory;
import io.jenetics.util.ISeq;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.Getter;

@Getter
public class AllocationCodec implements Codec<Map<VmInstance, List<ContainerType>>, DistributedIntegerGene> {

    private final Model model;
    private final SystemMeasures systemMeasures;
    private int vmCount;
    private int containerTypeCount;
    private int serviceCount;
    private final Map<VmInstance, Integer> vmsIndex = new HashMap<>();
    private final Map<Service, Integer> servicesIndex = new HashMap<>();


    public AllocationCodec(Model model, SystemMeasures systemMeasures) {
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


    @Override
    public Factory<Genotype<DistributedIntegerGene>> encoding() {
        return this::serviceRowGenotypeFactory;
    }

    @Override
    public Function<Genotype<DistributedIntegerGene>, Map<VmInstance, List<ContainerType>>> decoder() {
        return this::serviceRowSquareDecoder;
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
                        .map(s -> DistributedIntegerChromosome.of(s, serviceVmMatrix[servicesIndex.get(s)], model, systemMeasures))
                        .collect(ISeq.toISeq())
        );
    }

    public Genotype<DistributedIntegerGene> serviceRowGenotypeFactory() {
        return Genotype.of(
                model.getServices().values().stream()
                        .map(s -> DistributedIntegerChromosome.of(s, vmCount, model, systemMeasures))
                        .collect(ISeq.toISeq())
        );
    }

}
