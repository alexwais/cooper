package at.alexwais.cooper.scheduler.dto;

import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

@RequiredArgsConstructor
@Getter
public class LoadMeasures {
    private final Map<String, Integer> externalServiceLoad;
    private final Map<String, Integer> internalServiceLoad;
    private final Map<String, Integer> totalServiceLoad;
    private final Integer totalSystemLoad;
    private final SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> interactionGraph;
}