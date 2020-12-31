package at.ac.tuwien.dsg.cooper.scheduler.dto;

import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

@Data
@RequiredArgsConstructor
public class AnalysisResult {

    private final SimpleWeightedGraph<String, DefaultWeightedEdge> affinityGraph;

    private final boolean isCurrentAllocationUnderprovisioned;
    private final boolean isCurrentAllocationValid;
    private final Map<String, Float> loadDriftByService;
    private final Map<String, Float> capacityDriftByService;
    private final Float fitnessDrift;
}