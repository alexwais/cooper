package at.alexwais.cooper.benchmark;

import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.scheduler.Model;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class InteractionNode {

    @Getter
    private final String label;
    @Getter
    private final int internalLatency;

    protected final Model model;

    @Getter
    protected long recordedInteraction = 0L;

    protected final ServiceLoadMap totalProcessedLoad = new ServiceLoadMap();
    protected final ServiceLoadMap totalOverflow = new ServiceLoadMap();


    public abstract Result initialize();

    public abstract Result process(Map<Service, Double> loadPerService);


    @AllArgsConstructor
    @Getter
    public class Result {
        private Map<Service, Double> inducedOverflow;
        private Map<Service, Double> processedLoad;
        private Map<Service, Double> internalProcessedLoad;
        private boolean hasProcessed;
    }

}
