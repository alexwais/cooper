package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerConfiguration;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.exec.MapUtils;
import at.alexwais.cooper.exec.Model;
import at.alexwais.cooper.exec.State;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FitnessFunction {

    private final Model model;
    private final State state;
    private final Mapping mapping;

    public float eval(Boolean[][] decodedGenotype) {
        var violations = 0f;

        var allocation = mapping.genotypeToAllocation(decodedGenotype);
        var allocatedContainers = allocation.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        var allocatedContainersByService = new HashMap<String, List<ContainerConfiguration>>();
        allocatedContainers.forEach(c -> {
            MapUtils.putToMapList(allocatedContainersByService, c.getService().getName(), c);
        });

        var wastedMemory = 0;
        for (Map.Entry<VmInstance, List<ContainerConfiguration>> e : allocation.entrySet()) {
            var vm = e.getKey();
            var types = e.getValue();
            var availableMemory = vm.getType().getMemory();
            var requiredMemory = types.stream().map(t -> t.getMemory().toMegabytes()).reduce(0L, Long::sum);
            if (availableMemory < requiredMemory) {
                violations++;
            }
            wastedMemory += Math.abs(availableMemory - requiredMemory);
        }

        var capacityPerService = new HashMap<String, Long>();
        var overCapacity = 0;
        for (Service s : model.getServices().values()) {
            var rpmCap = allocatedContainersByService.getOrDefault(s.getName(), Collections.emptyList()).stream()
                    .map(ContainerConfiguration::getRpmCapacity)
                    .reduce(0L, Long::sum);
            capacityPerService.put(s.getName(), rpmCap);

            var serviceLoad = state.getServiceLoad().get(s.getName());
            var overCap = Math.abs(rpmCap - serviceLoad);
            overCapacity += overCap;
        }

        for (Map.Entry<String, Long> entry : state.getServiceLoad().entrySet()) {
            String serviceName = entry.getKey();
            Long load = entry.getValue();
            var capacity = capacityPerService.get(serviceName);
            if (capacity < load) {
                violations++;
            }
        }

        var allocatedVmCount = allocation.size();
        var allocatedContainerCount = allocatedContainers.size();

        var fitness = allocatedVmCount + overCapacity + wastedMemory * 100 + violations * 10_000_000_000f;
        return fitness;
    }

}
