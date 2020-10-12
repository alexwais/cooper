package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.ConsoleTable;
import at.alexwais.cooper.config.DataCenterConfigMap;
import at.alexwais.cooper.config.DataCenterDistanceConfigList;
import at.alexwais.cooper.config.ServiceConfigMap;
import at.alexwais.cooper.domain.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.WeightedPseudograph;

public class Initializer {

    private static final int INSTANCE_COUNT = 20;

    @Getter
    private List<DataCenter> dataCenters = new ArrayList<>();
    @Getter
    private List<Service> services = new ArrayList<>();
    @Getter
    private Map<String, Map<String, Float>> interactionMultiplication = new HashMap<>();
    @Getter
    private WeightedPseudograph<String, DefaultWeightedEdge> dataCenterDistanceGraph;

    public Initializer(DataCenterConfigMap dataCenterConfig, DataCenterDistanceConfigList distanceConfig, ServiceConfigMap serviceConfig) {
        initDataCenters(dataCenterConfig);
        initServices(serviceConfig);
        initDownstreamRequestMultiplier(serviceConfig);
        initDataCenterDistance(distanceConfig);
    }

    private void initDataCenterDistance(DataCenterDistanceConfigList distanceConfig) {
        dataCenterDistanceGraph = new WeightedPseudograph<>(DefaultWeightedEdge.class);
        dataCenters.forEach(dc -> dataCenterDistanceGraph.addVertex(dc.getName()));

        distanceConfig.forEach(distance -> {
            dataCenterDistanceGraph.addEdge(distance.getA(), distance.getB(), new DefaultWeightedEdge());
            var edge = dataCenterDistanceGraph.getEdge(distance.getA(), distance.getB());
            dataCenterDistanceGraph.setEdgeWeight(edge, distance.getLatency());
        });
    }

    private void initDownstreamRequestMultiplier(ServiceConfigMap serviceConfigMap) {
        services.forEach(s1 -> {
            var innerMap = new HashMap<String, Float>();
            interactionMultiplication.put(s1.getName(), innerMap);
            services.forEach(s2 -> {
                var downstreamServices = serviceConfigMap.get(s1.getName()).getDownstreamServices();
                if (downstreamServices == null) {
                    innerMap.put(s2.getName(), 0F);
                } else {
                    var ratio = downstreamServices.getOrDefault(s2.getName(), 0F);
                    innerMap.put(s2.getName(), ratio);
                }
            });
        });
    }

    private void initDataCenters(DataCenterConfigMap configMap) {
        configMap.forEach((key, config) -> {
            var dc = new DataCenter(key);

            var totalInstanceCounter = 1000;
            for (var t : config.getInstanceTypes()) {
                var vmType = new VmType(t.getLabel(), t.getCpuCores(), (int) t.getMemory().toMegabytes(), t.getCost(), dc);
                dc.getVmTypes().add(vmType);

                var instances = new ArrayList<VmInstance>();
                for (int j = 0; j < INSTANCE_COUNT; j++) {
                    var instanceId = dc.getName() + ":i" + totalInstanceCounter++;
                    instances.add(new VmInstance(instanceId, vmType, dc));
                }
                dc.getVmInstances().addAll(instances);
                dc.getVmsByType().put(vmType.getLabel(), instances);
            }

            dataCenters.add(dc);
        });
    }

    private void initServices(ServiceConfigMap configMap) {
        configMap.forEach((key, config) -> {
            var service = new Service(key);

//            var totalInstanceCounter = 1000;
            for (var c : config.getContainerConfigurations()) {
                var containerConfiguration = new ContainerType(c.getLabel(), c.getCpuShares(), c.getMemory(), c.getRpmCapacity(), service);
                service.getContainerTypes().add(containerConfiguration);

//                var containers = new ArrayList<ContainerInstance>();
//                for (int j = 0; j < INSTANCE_COUNT; j++) {
//                    var containerId = service.getName() + ":c" + totalInstanceCounter++;
//                    containers.add(new ContainerInstance(containerId, containerConfiguration, service));
//                }
//                service.getContainers().addAll(containers);
//                service.getContainersByConfiguration().putIfAbsent(containerConfiguration.getLabel(), containers);
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
        var table = new ConsoleTable("Type");
        service.getContainerTypes().forEach(it -> {
            table.addRow(it.getLabel());
        });
        return table;
    }

}