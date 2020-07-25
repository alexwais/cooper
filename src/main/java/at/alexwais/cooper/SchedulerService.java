package at.alexwais.cooper;

import at.alexwais.cooper.domain.DataCenter;
import at.alexwais.cooper.domain.Instance;
import at.alexwais.cooper.domain.InstanceType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SchedulerService {

    private static final int INSTANCE_COUNT = 2_000;

    private Map<String, DataCenter> dataCenters;

    public SchedulerService(DataCenterConfigMap configMap) {
        init(configMap);
    }


    private void init(DataCenterConfigMap configMap) {
        dataCenters = new HashMap<>();

        for (Map.Entry<String, DataCenterConfig> e : configMap.entrySet()) {
            var dc = new DataCenter(e.getKey());

            var totalInstanceCounter = 1000;
            for (InstanceTypeConfig i : e.getValue().getInstances()) {
                var instanceType = new InstanceType(i.getLabel(), i.getCpuCores(), (int) i.getMemory().toMegabytes(), i.getCost());
                dc.getVmTypes().add(instanceType);

                var instances = new ArrayList<Instance>();
                for (int j = 0; j < INSTANCE_COUNT; j++) {
                    var instanceId = dc.getName() + ":i" + totalInstanceCounter++;
                    instances.add(new Instance(instanceId, instanceType));
//                    dc.getVms().putIfAbsent(instanceType.getLabel(), new ArrayList<>());
                }
//                dc.getVms().put(instanceType.getLabel(), instances);
            }

            dataCenters.put(dc.getName(), dc);
        }
    }

    public void printState() {
        for (DataCenter dc : dataCenters.values()) {
            System.out.println();
            System.out.println(dc.getName() + ":");
            System.out.println();
            var table = buildTable(dc);
            table.print();
        }
    }

    private ConsoleTable buildTable(DataCenter dataCenter) {
        var table = new ConsoleTable("Type", "Count");
        dataCenter.getVmTypes().forEach(it -> {
//            table.addRow(it.getLabel(), dataCenter.getVms().get(it.getLabel()).size());
        });
        return table;
    }


}
