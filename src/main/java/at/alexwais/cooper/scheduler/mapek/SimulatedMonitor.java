package at.alexwais.cooper.scheduler.mapek;

import at.alexwais.cooper.scheduler.Model;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;


public class SimulatedMonitor {

    private Model model;
    private Deque<List<Integer>> externalLoadFixture = new ArrayDeque<>();

    public SimulatedMonitor(Model model) {
        this.model = model;

        externalLoadFixture.add(List.of(25000, 6000));
        externalLoadFixture.add(List.of(25000, 6000));
        externalLoadFixture.add(List.of(25000, 8000));
        externalLoadFixture.add(List.of(25000, 8000));
        externalLoadFixture.add(List.of(25000, 12000));
        externalLoadFixture.add(List.of(25000, 12000));
        externalLoadFixture.add(List.of(25000, 60000));
        externalLoadFixture.add(List.of(25000, 60000));
        externalLoadFixture.add(List.of(1000, 8000));
        externalLoadFixture.add(List.of(1000, 8000));
    }


    @RequiredArgsConstructor
    @Getter
    public class LoadMeasures {
        private final Map<String, Integer> externalServiceLoad;
        private final Map<String, Integer> internalServiceLoad;
        private final Map<String, Integer> totalServiceLoad;
        private final Integer totalSystemLoad;
        private final SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> interactionGraph;
    }


    public LoadMeasures getCurrentLoad() {
        var currentFixture = externalLoadFixture.pop();

        var externalServiceLoad = Map.of(
                "service-a", currentFixture.get(0),
                "service-b", currentFixture.get(1)
        );

        var interactionGraph = computeInteractionGraph(externalServiceLoad);

        var internalServiceLoad = computeInternalServiceLoad(interactionGraph);

        var totalServiceLoad = externalServiceLoad.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() + internalServiceLoad.get(e.getKey())));

        var totalSystemLoad = totalServiceLoad.values().stream().mapToInt(i -> i).sum();

        return new LoadMeasures(externalServiceLoad, internalServiceLoad, totalServiceLoad, totalSystemLoad, interactionGraph);
    }


    private SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> computeInteractionGraph(Map<String, Integer> externalServiceLoad) {
        var interactionGraph = initInteractionGraph();

        var loadRemainder = externalServiceLoad;
        while (!loadRemainder.isEmpty()) {
            loadRemainder = addToInteractionGraphAndReturnRemainingInternalLoad(interactionGraph, loadRemainder);
        }

        return interactionGraph;
    }

    private Map<String, Integer> computeInternalServiceLoad(SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> interactionGraph) {
        var internalServiceLoad = new HashMap<String, Integer>();
        model.getServices().values().forEach(s1 -> {
            model.getServices().values().forEach(s2 -> {
                if (!s1.equals(s2)) { // loops are prohibited
                    var edge = interactionGraph.getEdge(s1.getName(), s2.getName());
                    var edgeLoad = (int) interactionGraph.getEdgeWeight(edge);

                    var oldValue = internalServiceLoad.getOrDefault(s2.getName(), 0);
                    var newValue = oldValue + edgeLoad;
                    internalServiceLoad.put(s2.getName(), newValue);
                }
            });
        });
        return internalServiceLoad;
    }

    private SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> initInteractionGraph() {
        var interactionGraph = new SimpleDirectedWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class);
        model.getServices().values().forEach(s -> interactionGraph.addVertex(s.getName()));

        model.getServices().values().forEach(s1 -> {
            model.getServices().values().forEach(s2 -> {
                if (!s1.equals(s2)) { // loops are prohibited
                    interactionGraph.addEdge(s1.getName(), s2.getName(), new DefaultWeightedEdge());
                    var edge = interactionGraph.getEdge(s1.getName(), s2.getName());
                    interactionGraph.setEdgeWeight(edge, 0);
                }
            });
        });

        return interactionGraph;
    }

    private Map<String, Integer> addToInteractionGraphAndReturnRemainingInternalLoad(
            SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> interactionGraph,
            Map<String, Integer> loadToMultiply) {

        var resultingInternalLoad = new HashMap<String, Integer>();
        model.getInteractionMultiplication().forEach((s1, innerMap) -> {
            innerMap.forEach((s2, r) -> {
                var ingoingLoad = loadToMultiply.getOrDefault(s1, 0);

                var multipliedLoad = ((Float) (ingoingLoad * r)).intValue();

                if (multipliedLoad > 0) {
                    resultingInternalLoad.put(s2, multipliedLoad);

                    var edge = interactionGraph.getEdge(s1, s2);
                    var oldEdgeValue = interactionGraph.getEdgeWeight(edge);
                    var newEdgeValue = oldEdgeValue + multipliedLoad;
                    interactionGraph.setEdgeWeight(edge, newEdgeValue);
                }

            });
        });

        return resultingInternalLoad;
    }

}
