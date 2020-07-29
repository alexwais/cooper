package at.alexwais.cooper.exec;

import at.alexwais.cooper.domain.ContainerInstance;
import at.alexwais.cooper.domain.DataCenter;
import at.alexwais.cooper.domain.Instance;
import at.alexwais.cooper.domain.Service;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class Model {

    private final Map<String, DataCenter> dataCenters;
    private final Map<String, Instance> vms;
    private final Map<String, Service> services;
    private final Map<String, ContainerInstance> containers;

    public Model(List<DataCenter> dataCenters, List<Service> services) {
        this.dataCenters = dataCenters.stream()
                .collect(Collectors.toMap(DataCenter::getName, Function.identity()));

        this.vms = dataCenters.stream()
                .flatMap(dc -> dc.getVmInstances().stream())
                .collect(Collectors.toMap(Instance::getId, Function.identity()));

        this.services = services.stream()
                .collect(Collectors.toMap(Service::getName, Function.identity()));
        this.containers = services.stream()
                .flatMap(s -> s.getContainers().stream())
                .collect(Collectors.toMap(ContainerInstance::getId, Function.identity()));
    }

}
