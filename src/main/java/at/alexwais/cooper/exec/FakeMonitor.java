package at.alexwais.cooper.exec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;


public class FakeMonitor {

    private Deque<Map<String, Long>> loadRpmFixture = new ArrayDeque<>();

    public FakeMonitor() {
        loadRpmFixture.add(Map.of("sample-service", 500L));
        loadRpmFixture.add(Map.of("sample-service", 1000L));
        loadRpmFixture.add(Map.of("sample-service", 1500L));
        loadRpmFixture.add(Map.of("sample-service", 2000L));
        loadRpmFixture.add(Map.of("sample-service", 3000L));
        loadRpmFixture.add(Map.of("sample-service", 5000L));
        loadRpmFixture.add(Map.of("sample-service", 7000L));
        loadRpmFixture.add(Map.of("sample-service", 10000L));
        loadRpmFixture.add(Map.of("sample-service", 15000L));
        loadRpmFixture.add(Map.of("sample-service", 10000L));
        loadRpmFixture.add(Map.of("sample-service", 8000L));
        loadRpmFixture.add(Map.of("sample-service", 7000L));
        loadRpmFixture.add(Map.of("sample-service", 6000L));
        loadRpmFixture.add(Map.of("sample-service", 5000L));
        loadRpmFixture.add(Map.of("sample-service", 4000L));
        loadRpmFixture.add(Map.of("sample-service", 3000L));
        loadRpmFixture.add(Map.of("sample-service", 2000L));
        loadRpmFixture.add(Map.of("sample-service", 1000L));
        loadRpmFixture.add(Map.of("sample-service", 1000L));
        loadRpmFixture.add(Map.of("sample-service", 1000L));
        loadRpmFixture.add(Map.of("sample-service", 1000L));
        loadRpmFixture.add(Map.of("sample-service", 1000L));
        loadRpmFixture.add(Map.of("sample-service", 1000L));
    }

    public Map<String, Long> getCurrentServiceLoad() {
        return loadRpmFixture.pop();
    }

}
