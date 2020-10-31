package at.alexwais.cooper.scheduler.mapek;

import at.alexwais.cooper.genetic.FitnessFunction;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.State;
import at.alexwais.cooper.scheduler.Validator;
import at.alexwais.cooper.scheduler.dto.AnalysisResult;
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
        var isCurrentAllocationUnderprovisioned = isUnderprovisioned(state);
        var isCurrentAllocationValid = validator.isAllocationValid(state.getCurrentTargetAllocation(), state.getCurrentMeasures().getTotalServiceLoad());
        var affinityGraph = buildAffinityGraph(state);
        state.getCurrentMeasures().setAffinityGraph(affinityGraph);


        Float fitnessChangePercentage = null;
        if (state.getLastOptimizationResult() != null) {
            var previousFitness = state.getLastOptimizationResult().getFitness();
            var currentFitness = fitnessFunction.eval(state.getCurrentTargetAllocation(), state);
            var diff = currentFitness - previousFitness;
            fitnessChangePercentage = (diff / previousFitness) * 100;
            log.debug("change: {}%", fitnessChangePercentage);
        }

        // TODO determine change in load/capacity


        return new AnalysisResult(isCurrentAllocationUnderprovisioned, isCurrentAllocationValid, affinityGraph);
    }


    private boolean isUnderprovisioned(State state) {
        var underprovisionedServices = state.getCurrentMeasures().getTotalServiceLoad().entrySet().stream()
                .filter(e -> isServiceUnderprovisioned(e.getKey(), e.getValue(), state))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!underprovisionedServices.isEmpty()) {
            log.warn("Underprovisioned services: {}", underprovisionedServices);
        }

        return !underprovisionedServices.isEmpty();
    }

    private boolean isServiceUnderprovisioned(String serviceName, Integer load, State state) {
        var capacity = state.getCurrentTargetAllocation().getServiceCapacity().getOrDefault(serviceName, 0L);
        return capacity < load;
    }

    private SimpleWeightedGraph<String, DefaultWeightedEdge> buildAffinityGraph(State state) {
        var interactionGraph = state.getCurrentMeasures().getInteractionGraph();

        var affinityGraph = new SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class);

        model.getServices().values().forEach(s -> affinityGraph.addVertex(s.getName()));
        model.getServices().values().forEach(s1 -> {
            model.getServices().values().forEach(s2 -> {
                if (!s1.equals(s2)) {
                    affinityGraph.addEdge(s1.getName(), s2.getName(), new DefaultWeightedEdge());

                    var interactionEdge1 = interactionGraph.getEdge(s1.getName(), s2.getName());
                    var interactionEdge2 = interactionGraph.getEdge(s2.getName(), s1.getName());

                    var interaction1 = (int) interactionGraph.getEdgeWeight(interactionEdge1);
                    var interaction2 = (int) interactionGraph.getEdgeWeight(interactionEdge2);

                    var bidirectionalInteraction = interaction1 + interaction2;
                    var affinity = (double) bidirectionalInteraction / (double) state.getCurrentMeasures().getTotalSystemLoad();

                    var edge = affinityGraph.getEdge(s1.getName(), s2.getName());
                    affinityGraph.setEdgeWeight(edge, affinity);
                }
            });
        });

        return affinityGraph;
    }

}
