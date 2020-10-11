package at.alexwais.cooper.exec;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.DataCenter;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
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
