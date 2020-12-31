package at.alexwais.cooper.scheduler.mapek;

import at.alexwais.cooper.genetic.FitnessFunction;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.State;
import at.alexwais.cooper.scheduler.Validator;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.AnalysisResult;
import at.alexwais.cooper.scheduler.dto.OptResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

@Slf4j
public class Analyzer {

    private final Model model;
    private final Validator validator;
    private final FitnessFunction fitnessFunction;

    public Analyzer(Model model) {
        this.model = model;
        this.validator = new Validator(model);
        this.fitnessFunction = new FitnessFunction(model, validator);
    }

    public AnalysisResult analyze(State state) {
        var lastOptimization = state.getLastOptResult();
        var currentAllocation = state.getCurrentSystemMeasures().getCurrentAllocation();
        var currentMeasures = state.getCurrentSystemMeasures();

        // first, determine affinity
        var affinityGraph = buildAffinityGraph(state);
        currentMeasures.setAffinityGraph(affinityGraph);

        // compare fitness/load/capacity of current allocation vs. last optimization
        var loadDriftByService = computeLoadDrift(currentMeasures, lastOptimization);
        var capacityDriftByService = computeCapacityDrift(currentMeasures, lastOptimization);
        var fitnessChangePercentage = computeFitnessChangePercentage(currentMeasures, lastOptimization);

        var isCurrentAllocationUnderprovisioned = isUnderprovisioned(state.getCurrentSystemMeasures());
        var isCurrentAllocationValid = validator.isAllocationValidNeutral(currentAllocation, currentMeasures.getTotalServiceLoad());

        return new AnalysisResult(
                affinityGraph,
                isCurrentAllocationUnderprovisioned,
                isCurrentAllocationValid,
                loadDriftByService,
                capacityDriftByService,
                fitnessChangePercentage);
    }


    private Float computeFitnessChangePercentage(SystemMeasures measures, OptResult lastOptimization) {
        if (lastOptimization == null) {
            return 0f;
        }

        var currentFitness = fitnessFunction.evalNeutral(measures.getCurrentAllocation(), measures);
        var previousFitness = lastOptimization.getNeutralFitness();
        var fitnessChangePercentage = calculatePercentageChange(currentFitness, previousFitness);
        log.debug("Neutral fitness change: {}%", fitnessChangePercentage);
        return fitnessChangePercentage;
    }

    private float calculatePercentageChange(float current, float previous) {
        var diff = current - previous;
        var result = (diff / previous) * 100;
        return result;
    }


    private boolean isUnderprovisioned(SystemMeasures currentMeasures) {
        var underprovisionedServices = currentMeasures.getTotalServiceLoad().entrySet().stream()
                .filter(e -> isServiceUnderprovisioned(e.getKey(), e.getValue(), currentMeasures.getCurrentAllocation()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!underprovisionedServices.isEmpty()) {
            log.warn("Underprovisioned services: {}", underprovisionedServices);
        }

        return !underprovisionedServices.isEmpty();
    }


    private Map<String, Float> computeLoadDrift(SystemMeasures currentMeasures, OptResult lastOptimization) {
        if (lastOptimization == null) {
            return Collections.emptyMap();
        }

        var driftPerService = currentMeasures.getTotalServiceLoad().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    var current = e.getValue();
                    var prev = lastOptimization.getUnderlyingMeasures().getTotalServiceLoad().get(e.getKey());
                    var change = calculatePercentageChange(current, prev);
                    return change;
                }));
        return driftPerService;
    }

    private Map<String, Float> computeCapacityDrift(SystemMeasures currentMeasures, OptResult lastOptimization) {
        if (lastOptimization == null) {
            return Collections.emptyMap();
        }

        var driftPerService = currentMeasures.getCurrentAllocation().getServiceCapacity().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    var current = e.getValue();
                    var prev = lastOptimization.getAllocation().getServiceCapacity().get(e.getKey());
                    var change = calculatePercentageChange(current, prev);
                    return change;
                }));
        return driftPerService;
    }

    private boolean isServiceUnderprovisioned(String serviceName, Integer load, Allocation allocation) {
        var capacity = allocation.getServiceCapacity().getOrDefault(serviceName, 0L);
        return capacity < load;
    }

    private SimpleWeightedGraph<String, DefaultWeightedEdge> buildAffinityGraph(State state) {
        var affinityGraph = new SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class);

        model.getServices().values().forEach(s -> affinityGraph.addVertex(s.getName()));
        model.getServices().values().forEach(s1 -> {
            model.getServices().values().forEach(s2 -> {
                if (!s1.equals(s2)) {
                    affinityGraph.addEdge(s1.getName(), s2.getName(), new DefaultWeightedEdge());

                    var interaction1 = state.getCurrentSystemMeasures().getInteractionBetween(s1, s2);
                    var interaction2 = state.getCurrentSystemMeasures().getInteractionBetween(s2, s1);

                    var bidirectionalInteraction = interaction1 + interaction2;
                    var affinity = (double) bidirectionalInteraction / (double) state.getCurrentSystemMeasures().getTotalSystemLoad();

                    var edge = affinityGraph.getEdge(s1.getName(), s2.getName());
                    affinityGraph.setEdgeWeight(edge, affinity);
                }
            });
        });

        return affinityGraph;
    }

}
