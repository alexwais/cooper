package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.domain.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.WeightedPseudograph;

@Getter
public class Model {

    private final Map<String, DataCenter> dataCenters;
    private final List<VmType> vmTypes;
    private final Map<String, VmInstance> vms;
    private final Map<String, Service> services;
    private final List<ContainerType> containerTypes;
    private final Map<String, Map<String, Float>> interactionMultiplication;
    private WeightedPseudograph<String, DefaultWeightedEdge> dataCenterDistanceGraph;

    public Model(List<DataCenter> dataCenters,
                 List<Service> services,
                 Map<String, Map<String, Float>> interactionMultiplication,
                 WeightedPseudograph<String, DefaultWeightedEdge> dataCenterDistanceGraph) {
        this.dataCenters = dataCenters.stream()
                .collect(Collectors.toMap(DataCenter::getName, Function.identity()));

        this.vmTypes = dataCenters.stream()
                .flatMap(dc -> dc.getVmTypes().stream())
                .collect(Collectors.toList());

        this.vms = dataCenters.stream()
                .flatMap(dc -> dc.getVmInstances().stream())
                .collect(Collectors.toMap(VmInstance::getId, Function.identity()));

        this.services = services.stream()
                .collect(Collectors.toMap(Service::getName, Function.identity()));

        this.containerTypes = services.stream()
                .flatMap(s -> s.getContainerTypes().stream())
                .collect(Collectors.toList());

        this.interactionMultiplication = interactionMultiplication;

        this.dataCenterDistanceGraph = dataCenterDistanceGraph;
    }

}
