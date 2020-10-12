package at.alexwais.cooper.scheduler.mapek;

import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.State;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

public class Analyzer {

    private final Model model;

    public Analyzer(Model model) {
        this.model = model;
    }

    @Data
    @RequiredArgsConstructor
    public class AnalysisResult {
        private final SimpleWeightedGraph<String, DefaultWeightedEdge> affinityGraph;
    }


    public AnalysisResult analyze(State state) {
        var affinityGraph = new SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class);

        model.getServices().values().forEach(s -> affinityGraph.addVertex(s.getName()));
        model.getServices().values().forEach(s1 -> {
            model.getServices().values().forEach(s2 -> {
                if (!s1.equals(s2)) {
                    affinityGraph.addEdge(s1.getName(), s2.getName(), new DefaultWeightedEdge());

                    var interactionEdge1 = state.getInteractionGraph().getEdge(s1.getName(), s2.getName());
                    var interactionEdge2 = state.getInteractionGraph().getEdge(s2.getName(), s1.getName());

                    var interaction1 = (int) state.getInteractionGraph().getEdgeWeight(interactionEdge1);
                    var interaction2 = (int) state.getInteractionGraph().getEdgeWeight(interactionEdge2);

                    var bidirectionalInteraction = interaction1 + interaction2;
                    var affinity = (double) bidirectionalInteraction / (double) state.getTotalSystemLoad();

                    var edge = affinityGraph.getEdge(s1.getName(), s2.getName());
                    affinityGraph.setEdgeWeight(edge, affinity);
                }
            });
        });

        return new AnalysisResult(affinityGraph);
    }

}
