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

    public ContainerInteractionNode(Model model, ContainerType container, double initialLoad) {
        super(model);
        this.initialLoad = initialLoad;
        this.service = container.getService();
        this.freeCapacity = container.getRpmCapacity().doubleValue();
    }


    public Result initialize() {
        return this.processOverflow(Map.of(service, initialLoad));
    }

    public Result processOverflow(Map<Service, Double> loadToProcess) {
        var requestedLoad = loadToProcess.get(service);
        var isLoadPresent = requestedLoad != null && requestedLoad > 0;

        if (freeCapacity > 0 && isLoadPresent) {
            var processedLoad = Math.min(freeCapacity, requestedLoad);
            var inducedLoad = new HashMap<Service, Double>();

            var downstreamServices = model.getServices().values().stream()
                    .filter(s -> !s.equals(service))
                    .collect(Collectors.toList());

            for (var toService : downstreamServices) {
                var multiplier = model.getInteractionMultiplication().get(service.getName())
                        .get(toService.getName());
                var inducedCalls = processedLoad * multiplier;
                if (inducedCalls > 0) {
                    inducedLoad.put(toService, inducedCalls);
                }
            }

            freeCapacity -= processedLoad;

            var remainingLoad = new HashMap<Service, Double>();
            remainingLoad.put(service, requestedLoad - processedLoad);

            inducedLoad.entrySet().forEach(e -> {
                var service = e.getKey();
                var prevCalls = loadToProcess.getOrDefault(service, 0d);
                remainingLoad.put(e.getKey(), e.getValue() + prevCalls);
            });

            loadToProcess.entrySet()
                    .forEach(e -> remainingLoad.putIfAbsent(e.getKey(), e.getValue()));

            return new Result(remainingLoad, true);
        } else {
            return new Result(loadToProcess, false);
        }
    }

}
