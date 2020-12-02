package at.alexwais.cooper.benchmark;

import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.scheduler.Model;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public abstract class InteractionNode {

    protected Model model;

    public abstract Result initialize();

    public abstract Result processOverflow(Map<Service, Double> loadPerService);


    @AllArgsConstructor
    @Getter
    public class Result {
        private Map<Service, Double> overflowPerService;
        private boolean hasProcessed;
    }

}
