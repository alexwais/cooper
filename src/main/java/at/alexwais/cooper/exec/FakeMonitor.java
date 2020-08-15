package at.alexwais.cooper.exec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;


public class FakeMonitor {

    private Deque<List<Long>> loadRpmFixture = new ArrayDeque<>();

    public FakeMonitor() {
        loadRpmFixture.add(List.of(25000L, 6000L));
        loadRpmFixture.add(List.of(25000L, 6000L));
        loadRpmFixture.add(List.of(25000L, 8000L));
        loadRpmFixture.add(List.of(25000L, 8000L));
        loadRpmFixture.add(List.of(25000L, 12000L));
        loadRpmFixture.add(List.of(25000L, 12000L));
        loadRpmFixture.add(List.of(25000L, 8000L));
        loadRpmFixture.add(List.of(25000L, 8000L));

//        loadRpmFixture.add(List.of(500L, 3000L));
//        loadRpmFixture.add(List.of(1000L, 3000L));
//        loadRpmFixture.add(List.of(1500L, 4000L));
//        loadRpmFixture.add(List.of(3000L, 5000L));
//        loadRpmFixture.add(List.of(6000L, 8000L));
//        loadRpmFixture.add(List.of(8000L, 12000L));
//        loadRpmFixture.add(List.of(8000L, 12000L));
//        loadRpmFixture.add(List.of(15000L, 8000L));
//        loadRpmFixture.add(List.of(20000L, 8000L));
//        loadRpmFixture.add(List.of(25000L, 8000L));
//        loadRpmFixture.add(List.of(25000L, 8000L));
//        loadRpmFixture.add(List.of(17000L, 8000L));
//        loadRpmFixture.add(List.of(14000L, 8000L));
//        loadRpmFixture.add(List.of(14000L, 8000L));
//        loadRpmFixture.add(List.of(9000L, 7000L));
//        loadRpmFixture.add(List.of(7000L, 7000L));
//        loadRpmFixture.add(List.of(6000L, 7000L));
//        loadRpmFixture.add(List.of(5000L, 10000L));
//        loadRpmFixture.add(List.of(4000L, 12000L));
//        loadRpmFixture.add(List.of(3000L, 12000L));
//        loadRpmFixture.add(List.of(2000L, 12000L));
//        loadRpmFixture.add(List.of(1000L, 8000L));
//        loadRpmFixture.add(List.of(1000L, 8000L));
//        loadRpmFixture.add(List.of(1000L, 8000L));
    }

    public Map<String, Long> getCurrentServiceLoad() {
        var list = loadRpmFixture.pop();
        return Map.of("service-a", list.get(0),
                "service-b", list.get(1));
    }

}
