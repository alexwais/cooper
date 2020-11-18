package at.alexwais.cooper.scheduler.dto;

import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.scheduler.Model;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

@Getter
@RequiredArgsConstructor
public class SystemMeasures {

    private final Model model;

    private final Map<String, Integer> externalServiceLoad;
    private final Map<String, Integer> internalServiceLoad;
    private final Map<String, Integer> totalServiceLoad;
    private final Integer totalSystemLoad;
    private final SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> interactionGraph;
    private final Allocation currentAllocation;

    @Setter
    private SimpleWeightedGraph<String, DefaultWeightedEdge> affinityGraph;


    public SystemMeasures(Model model, MonitoringResult toClone, Allocation currentAllocation) {
        this(model,
                toClone.getExternalServiceLoad(),
                toClone.getInternalServiceLoad(),
                toClone.getTotalServiceLoad(),
                toClone.getTotalSystemLoad(),
                toClone.getInteractionGraph(),
                currentAllocation);
    }


    public double getAffinityBetween(Service serviceA,
                                     Service serviceB) {
        var isSameService = serviceA.equals(serviceB);
        if (isSameService) {
            // No affinity between same service possible, graph contains no loops
            return 0;
        }
        var edge = affinityGraph.getEdge(serviceA.getName(), serviceB.getName());
        var affinity = affinityGraph.getEdgeWeight(edge);
        return affinity;
    }

}
