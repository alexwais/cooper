package at.alexwais.cooper.scheduler;

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

    private int instanceCount;

    @Getter
    private List<DataCenter> dataCenters = new ArrayList<>();
    @Getter
    private List<Service> services = new ArrayList<>();
    @Getter
    private Map<String, Map<String, Float>> interactionMultiplication = new HashMap<>();
    @Getter
    private WeightedPseudograph<String, DefaultWeightedEdge> dataCenterDistanceGraph;

    public Initializer(int multiplicator, DataCenterConfigMap dataCenterConfig, DataCenterDistanceConfigList distanceConfig, ServiceConfigMap serviceConfig) {
        this.instanceCount = Math.max(multiplicator, 2); // TODO finalize/document
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
            var dc = new DataCenter(config.isOnPremise(), key);

            var totalInstanceCounter = 1000;
            for (var t : config.getInstanceTypes()) {
                var vmType = new VmType(t.getLabel(), t.getCpuCores(), (int) t.getMemory().toMegabytes(), t.getCost(), dc);
                dc.getVmTypes().add(vmType);

                var instanceCount = dc.isOnPremise() ? t.getCount() : this.instanceCount;

                var instances = new ArrayList<VmInstance>();
                for (int j = 0; j < instanceCount; j++) {
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

            for (var c : config.getContainerConfigurations()) {
                var containerConfiguration = new ContainerType(service.getName() + ":" + c.getLabel(), c.getCpuShares(), (int) c.getMemory().toMegabytes(), c.getRpmCapacity(), service);
                service.getContainerTypes().add(containerConfiguration);
            }

            services.add(service);
        });
    }

}
