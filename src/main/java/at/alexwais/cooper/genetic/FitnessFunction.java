package at.alexwais.cooper.genetic;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.exec.MapUtils;
import at.alexwais.cooper.exec.Model;
import at.alexwais.cooper.exec.State;
import at.alexwais.cooper.exec.Validator;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FitnessFunction {

    private final Model model;
    private final Validator validator;

    public float eval(Map<VmInstance, List<ContainerType>> allocation, State state) {
        var violations = validator.violations(allocation, state.getServiceLoad());

        var allocatedContainers = allocation.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        var allocatedContainersByService = new HashMap<String, List<ContainerType>>();
        allocatedContainers.forEach(c -> {
            MapUtils.putToMapList(allocatedContainersByService, c.getService().getName(), c);
        });

        var wastedMemory = 0;
        var containersOnSameVm = 0;
        for (Map.Entry<VmInstance, List<ContainerType>> e : allocation.entrySet()) {
            var vm = e.getKey();
            var allocatedTypes = e.getValue();
            var availableMemory = vm.getType().getMemory();
            var requiredMemory = allocatedTypes.stream().map(t -> t.getMemory().toMegabytes()).reduce(0L, Long::sum);
            wastedMemory += Math.abs(availableMemory - requiredMemory);
            if (allocatedTypes.size() > 1) {
                containersOnSameVm += allocatedTypes.size();
            }
        }

        var capacityPerService = new HashMap<String, Long>();
        var overCapacity = 0;
        for (Service s : model.getServices().values()) {
            var rpmCap = allocatedContainersByService.getOrDefault(s.getName(), Collections.emptyList()).stream()
                    .map(ContainerType::getRpmCapacity)
                    .reduce(0L, Long::sum);
            capacityPerService.put(s.getName(), rpmCap);

            var serviceLoad = state.getServiceLoad().get(s.getName());
            var overCap = Math.abs(rpmCap - serviceLoad);
            overCapacity += overCap;
        }


        var allocatedVmCount = allocation.size();

        var fitness = allocatedVmCount * 100 - containersOnSameVm * 1000 + overCapacity + wastedMemory * 100 + violations * 10_000_000f;
        return fitness;
    }

}
