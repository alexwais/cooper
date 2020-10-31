package at.alexwais.cooper.scheduler.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

@Data
@RequiredArgsConstructor
public class AnalysisResult {

    private final boolean isCurrentAllocationUnderprovisioned;
    private final boolean isCurrentAllocationValid;
    private final SimpleWeightedGraph<String, DefaultWeightedEdge> affinityGraph;

}