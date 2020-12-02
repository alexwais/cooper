package at.alexwais.cooper.benchmark;

import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.scheduler.Model;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AggregatingInteractionNode extends InteractionNode {

    private List<? extends InteractionNode> childNodes;

    public AggregatingInteractionNode(Model model, List<? extends InteractionNode> children) {
        super(model);
        this.childNodes = children;
    }


    public Result initialize() {
        var overflowPerService = new HashMap<Service, Double>();

        for (var childNode : childNodes) {
            var result = childNode.initialize();

            for (var serviceAndOverflow : result.getOverflowPerService().entrySet()) {
                var service = serviceAndOverflow.getKey();
                var overflow = serviceAndOverflow.getValue();

                if (overflow > 0) {
                    var prevCalls = overflowPerService.getOrDefault(service, 0d);
                    overflowPerService.put(service, overflow + prevCalls);
                }
            }
        }

        return this.processOverflow(overflowPerService);
    }

    public InteractionNode.Result processOverflow(Map<Service, Double> loadPerService) {
        var processingHappened = false;

        var loadToProcess = loadPerService;

        var hasOverflowProcessed = false;
        do {
//            var returnedOverflow = new HashMap<Service, Double>();
            hasOverflowProcessed = false;

            for (var childNode : childNodes) {
                var result = childNode.processOverflow(loadToProcess);
                loadToProcess = result.getOverflowPerService();
                if (result.isHasProcessed()) {
                    hasOverflowProcessed = true;
                }

//                for (var serviceAndOverflow : result.getOverflowPerService().entrySet()) {
//                    var service = serviceAndOverflow.getKey();
//                    var overflow = serviceAndOverflow.getValue();
//
//                    if (overflow > 0) {
//                        var prevCalls = returnedOverflow.getOrDefault(service, 0d);
//                        returnedOverflow.put(service, overflow + prevCalls);
//                    }
//                }
            }

            if (hasOverflowProcessed) {
                processingHappened = true;
            }

//            loadToProcess = returnedOverflow;
        } while (hasOverflowProcessed); // if nothing could be processed anymore: no change, stop processing

        return new Result(loadToProcess, processingHappened);
    }

}
