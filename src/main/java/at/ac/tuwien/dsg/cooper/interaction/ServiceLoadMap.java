package at.ac.tuwien.dsg.cooper.interaction;

import at.ac.tuwien.dsg.cooper.domain.Service;
import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ServiceLoadMap extends HashMap<Service, Double> {

    public ServiceLoadMap(Map<Service, Double> clone) {
        super(clone);
    }

    public void add(Map<Service, Double> other) {
        other.entrySet().stream()
                .forEach(e -> {
                    var service = e.getKey();
                    var additionalLoad = e.getValue();
                    var previousLoad = this.getOrDefault(service, 0d);
                    this.put(service, previousLoad + additionalLoad);
                });
    }

    public void deduct(Map<Service, Double> other) {
        other.entrySet().stream()
                .forEach(e -> {
                    var service = e.getKey();
                    var deductedLoad = e.getValue();
                    var previousLoad = this.get(service);
                    this.put(service, previousLoad - deductedLoad);
                });
    }

}
