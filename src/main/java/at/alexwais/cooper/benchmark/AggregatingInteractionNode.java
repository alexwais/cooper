package at.alexwais.cooper.benchmark;

import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.scheduler.Model;
import java.util.List;
import java.util.Map;

public class AggregatingInteractionNode extends InteractionNode {

    private List<? extends InteractionNode> childNodes;

    public AggregatingInteractionNode(String label, int latency, Model model, List<? extends InteractionNode> children) {
        super(label, latency, model);
        this.childNodes = children;
    }


    public Result initialize() {
        var overflowPool = new ServiceLoadMap();
        var processedLoad = new ServiceLoadMap();
        var internalProcessedLoad = new ServiceLoadMap();

        // process initial load on child nodes
        for (var childNode : childNodes) {
            var result = childNode.initialize();
            overflowPool.add(result.getInducedOverflow());
            processedLoad.add(result.getProcessedLoad());
            internalProcessedLoad.add(result.getInternalProcessedLoad());
        }
        this.totalProcessedLoad.add(internalProcessedLoad);
        this.totalProcessedLoad.add(processedLoad);

        // process induced overflow and count processed load as internal interaction
        var result = this.process(overflowPool);
        var processedOverflow = result.getProcessedLoad();
        var internalProcessedOverflow = result.getInternalProcessedLoad();
        var additionalOverflow = result.getInducedOverflow();

        overflowPool.deduct(processedOverflow); // TODO record latency
        this.totalOverflow.add(overflowPool); // record remaining overflow before adding additional overflow
        overflowPool.add(additionalOverflow);
        internalProcessedLoad.add(result.getProcessedLoad());
        internalProcessedLoad.add(internalProcessedOverflow);

        return new Result(overflowPool, processedLoad, internalProcessedLoad, result.isHasProcessed());
    }

    public Result process(final Map<Service, Double> loadPerService) {
        var remainingLoad = new ServiceLoadMap(loadPerService);
        var processedLoad = new ServiceLoadMap();
        var overflowPool = new ServiceLoadMap();
        var internalProcessedLoad = new ServiceLoadMap();

        var hasProcessedAtLeastOnce = false;
        var isProcessing = true;

        while (isProcessing) { // if nothing could be processed anymore: no change, stop processing
            isProcessing = false;
            for (var childNode : childNodes) {
                var overflowResult = childNode.process(overflowPool);
                var processedOverflow = overflowResult.getProcessedLoad();
                var internalProcessedOverflow = overflowResult.getInternalProcessedLoad();
                var inducedOverflowByOverflow = overflowResult.getInducedOverflow();
                internalProcessedLoad.add(processedOverflow);
                internalProcessedLoad.add(internalProcessedOverflow);
                overflowPool.deduct(processedOverflow); // TODO record latency
                overflowPool.add(inducedOverflowByOverflow);

                var loadResult = childNode.process(remainingLoad); // TODO distribute among nodes by capacity instead of first-fit?
                var processed = loadResult.getProcessedLoad();
                var internalProcessed = loadResult.getInternalProcessedLoad();
                var inducedOverflow = loadResult.getInducedOverflow();

                remainingLoad.deduct(processed);
                processedLoad.add(processed);
                internalProcessedLoad.add(internalProcessed);
                overflowPool.add(inducedOverflow);

                if (loadResult.isHasProcessed()) {
                    isProcessing = true;
                }
            }

            if (isProcessing) {
                hasProcessedAtLeastOnce = true;
            }
        }


        this.totalProcessedLoad.add(processedLoad);
        this.totalProcessedLoad.add(internalProcessedLoad);
        this.totalOverflow.add(overflowPool);
        return new Result(overflowPool, processedLoad, internalProcessedLoad, hasProcessedAtLeastOnce);
    }

}
