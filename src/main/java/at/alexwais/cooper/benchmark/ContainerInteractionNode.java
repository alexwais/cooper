package at.alexwais.cooper.benchmark;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.scheduler.Model;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ContainerInteractionNode extends InteractionNode {

    private Service service;
    private double freeCapacity;
    private double initialLoad;

    public ContainerInteractionNode(String label, Model model, ContainerType container, double initialLoad) {
        super(label, 0, model);
        this.initialLoad = initialLoad;
        this.service = container.getService();
        this.freeCapacity = container.getRpmCapacity().doubleValue();
    }


    public Result initialize() {
        var result = this.process(Map.of(service, initialLoad));
        assert result.getProcessedLoad().get(service) == initialLoad;
        return result;
    }

    public Result process(Map<Service, Double> loadToProcess) {
        var requestedLoad = loadToProcess.get(service);
        var isLoadPresent = requestedLoad != null && requestedLoad > 0;

        if (freeCapacity > 0 && isLoadPresent) {
            var processableLoad = Math.min(freeCapacity, requestedLoad);
            var processedLoad = new HashMap<Service, Double>();
            var inducedLoad = new HashMap<Service, Double>();

            processedLoad.put(service, processableLoad);
            freeCapacity -= processableLoad;

            var downstreamServices = model.getServices().values().stream()
                    .filter(s -> !s.equals(service))
                    .collect(Collectors.toList());

            for (var toService : downstreamServices) {
                var multiplier = model.getInteractionMultiplication().get(service.getName())
                        .get(toService.getName());
                var inducedCalls = processableLoad * multiplier;
                if (inducedCalls > 0) {
                    inducedLoad.put(toService, inducedCalls);
                }
            }

            this.totalProcessedLoad.add(processedLoad);
            this.totalOverflow.add(inducedLoad);
            return new Result(inducedLoad, processedLoad, Map.of(), true);
        } else {
            return new Result(Map.of(), Map.of(), Map.of(), false);
        }
    }

}
