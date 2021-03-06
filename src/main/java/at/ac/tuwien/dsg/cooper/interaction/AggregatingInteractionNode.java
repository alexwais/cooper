package at.ac.tuwien.dsg.cooper.interaction;

import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AggregatingInteractionNode extends InteractionNode {

    private List<? extends InteractionNode> childNodes;
    private Map<InteractionNode, Map<InteractionNode, Integer>> distanceGraph;
    private InteractionRecorder interactionRecorder;

    public AggregatingInteractionNode(String label,
                                      Model model,
                                      List<? extends InteractionNode> children,
                                      Map<InteractionNode, Map<InteractionNode, Integer>> distanceGraph,
                                      InteractionRecorder interactionRecorder) {
        super(label, model);
        this.childNodes = children;
        this.distanceGraph = distanceGraph;
        this.interactionRecorder = interactionRecorder;
    }

    // overflow by source nodes
    private Map<InteractionNode, ServiceLoadMap> overflowPool = new HashMap<>();

    public Result initialize() {
        var processedLoad = new ServiceLoadMap();
        var internalProcessedLoad = new ServiceLoadMap();

        // process initial load on child nodes
        for (var childNode : childNodes) {
            var result = childNode.initialize();

            overflowPool.put(childNode, new ServiceLoadMap(result.getInducedOverflow()));

            processedLoad.add(result.getProcessedLoad());
            internalProcessedLoad.add(result.getInternalProcessedLoad());
        }
        this.totalProcessedLoad.add(internalProcessedLoad);
        this.totalProcessedLoad.add(processedLoad);

        // process induced overflow and count processed load as internal interaction
        var result = this.process(Map.of());
        assert result.getProcessedLoad().isEmpty();
        var internalProcessedOverflow = result.getInternalProcessedLoad();
        var overflow = result.getInducedOverflow();

        internalProcessedLoad.add(result.getProcessedLoad());
        internalProcessedLoad.add(internalProcessedOverflow);

        return new Result(overflow, processedLoad, internalProcessedLoad, result.isHasProcessed());
    }

    public Result process(final Map<Service, Double> loadPerService) {
        var remainingLoad = new ServiceLoadMap(loadPerService);
        var processedLoad = new ServiceLoadMap();
        var internalProcessedLoad = new ServiceLoadMap();

        var hasProcessedAtLeastOnce = false;
        var isProcessing = true;

        while (isProcessing) { // if nothing could be processed anymore: no change, stop processing
            isProcessing = false;
            for (var targetNode : childNodes) {

                var clonedPool = new HashMap<>(overflowPool);
                for (var sourceNode : clonedPool.entrySet()) {
                    var sourcePool = sourceNode.getValue();
                    var overflowResult = targetNode.process(sourcePool);
                    var processedOverflow = overflowResult.getProcessedLoad();
                    internalProcessedLoad.add(processedOverflow);
                    internalProcessedLoad.add(overflowResult.getInternalProcessedLoad());

                    var distanceLatency = distanceGraph.get(sourceNode.getKey()).get(targetNode);
                    var processedOverflowSum = processedOverflow.values().stream().mapToDouble(v -> v).sum();
                    interactionRecorder.record(distanceLatency, processedOverflowSum);

                    sourcePool.deduct(processedOverflow);
                    addToPool(targetNode, overflowResult.getInducedOverflow());

                    if (overflowResult.isHasProcessed()) {
                        isProcessing = true;
                    }
                }

                var loadResult = targetNode.process(remainingLoad);
                var processed = loadResult.getProcessedLoad();
                remainingLoad.deduct(processed);
                processedLoad.add(processed);
                internalProcessedLoad.add(loadResult.getInternalProcessedLoad());
                addToPool(targetNode, loadResult.getInducedOverflow());

                if (loadResult.isHasProcessed()) {
                    isProcessing = true;
                }
            }

            if (isProcessing) {
                hasProcessedAtLeastOnce = true;
            }
        }

        var remainingOverflow = new ServiceLoadMap();
        overflowPool.values().forEach(remainingOverflow::add);
        overflowPool.clear();

        this.totalProcessedLoad.add(processedLoad);
        this.totalProcessedLoad.add(internalProcessedLoad);
        this.totalOverflow.add(remainingOverflow);
        return new Result(remainingOverflow, processedLoad, internalProcessedLoad, hasProcessedAtLeastOnce);
    }

    private void addToPool(InteractionNode sourceNode, Map<Service, Double> loadMap) {
        var sourceNodePool = overflowPool.getOrDefault(sourceNode, new ServiceLoadMap());
        sourceNodePool.add(loadMap);
        overflowPool.put(sourceNode, sourceNodePool);
    }

}
