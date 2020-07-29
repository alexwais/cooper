package at.alexwais.cooper.exec;

import at.alexwais.cooper.ConsoleTable;
import at.alexwais.cooper.config.DataCenterConfigMap;
import at.alexwais.cooper.config.ServiceConfigMap;
import at.alexwais.cooper.domain.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

public class Initializer {

    private static final int INSTANCE_COUNT = 1_000;

    @Getter
    private List<DataCenter> dataCenters = new ArrayList<>();
    @Getter
    private List<Service> services = new ArrayList<>();

    public Initializer(DataCenterConfigMap dataCenterConfig, ServiceConfigMap serviceConfig) {
        initDataCenters(dataCenterConfig);
        initServices(serviceConfig);
    }


    private void initDataCenters(DataCenterConfigMap configMap) {
        configMap.forEach((key, config) -> {
            var dc = new DataCenter(key);

            var totalInstanceCounter = 1000;
            for (var t : config.getInstanceTypes()) {
                var instanceType = new InstanceType(t.getLabel(), t.getCpuCores(), (int) t.getMemory().toMegabytes(), t.getCost());
                dc.getVmTypes().add(instanceType);

                var instances = new ArrayList<Instance>();
                for (int j = 0; j < INSTANCE_COUNT; j++) {
                    var instanceId = dc.getName() + ":i" + totalInstanceCounter++;
                    instances.add(new Instance(instanceId, instanceType));

//                    dc.getVmsByType().putIfAbsent(instanceType.getLabel(), new ArrayList<>());
                }
                dc.getVmInstances().addAll(instances);
                dc.getVmsByType().put(instanceType.getLabel(), instances);
            }

            dataCenters.add(dc);
        });
    }

    private void initServices(ServiceConfigMap configMap) {
        configMap.forEach((key, config) -> {
            var service = new Service(key);

            var totalInstanceCounter = 1000;
            for (var c : config.getContainerConfigurations()) {
                var containerConfiguration = new ContainerConfiguration(c.getLabel(), c.getCpuShares(), c.getMemory(), c.getRpmCapacity(), service);
                service.getContainerConfigurations().add(containerConfiguration);

                var containers = new ArrayList<ContainerInstance>();
                for (int j = 0; j < INSTANCE_COUNT; j++) {
                    var containerId = service.getName() + ":c" + totalInstanceCounter++;
                    containers.add(new ContainerInstance(containerId, containerConfiguration, service));
//                    service.getContainersByConfiguration().putIfAbsent(containerConfiguration.getLabel(), new ArrayList<>());
                }
                service.getContainers().addAll(containers);
                service.getContainersByConfiguration().putIfAbsent(containerConfiguration.getLabel(), containers);
            }

            services.add(service);
        });
    }

    public void printState() {
        for (DataCenter dc : dataCenters) {
            System.out.println();
            System.out.println(dc.getName() + ":");
            System.out.println();
            var table = buildTable(dc);
            table.print();
        }

        for (Service s : services) {
            System.out.println();
            System.out.println(s.getName() + ":");
            System.out.println();
            var table = buildTable(s);
            table.print();
        }
    }

    private ConsoleTable buildTable(DataCenter dataCenter) {
        var table = new ConsoleTable("Type", "Count");
        dataCenter.getVmTypes().forEach(it -> {
            table.addRow(it.getLabel(), dataCenter.getVmsByType().get(it.getLabel()).size());
        });
        return table;
    }

    private ConsoleTable buildTable(Service service) {
        var table = new ConsoleTable("Type", "Count");
        service.getContainerConfigurations().forEach(it -> {
            table.addRow(it.getLabel(), service.getContainersByConfiguration().get(it.getLabel()).size());
        });
        return table;
    }


}
