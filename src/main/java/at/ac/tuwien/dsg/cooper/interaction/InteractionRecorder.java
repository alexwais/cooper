package at.ac.tuwien.dsg.cooper.interaction;

import java.util.HashMap;
import java.util.Map;

public class InteractionRecorder {

    // latency to number of calls
    private Map<Integer, Double> recordedInteractionByLatency = new HashMap<>();

    public void record(Integer latency, Double calls) {
      var previousCalls = recordedInteractionByLatency.getOrDefault(latency, 0d);
      recordedInteractionByLatency.put(latency, previousCalls + calls);
    }

    public Double getAverageLatency() {
        var totalLatency = getTotalLatency();
        var totalCalls = getTotalCalls();
        return totalLatency / totalCalls;
    }

    public Double getTotalLatency() {
        return recordedInteractionByLatency.entrySet().stream()
                .map(e -> e.getKey() * e.getValue())
                .mapToDouble(v -> v).sum();
    }

    public Double getTotalCalls() {
        return recordedInteractionByLatency.values().stream().mapToDouble(v -> v).sum();
    }

}
