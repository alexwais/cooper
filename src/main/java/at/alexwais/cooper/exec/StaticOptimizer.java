package at.alexwais.cooper.exec;

import java.util.*;


public class StaticOptimizer {

    private Deque<List<String>> fixture = new ArrayDeque<>();

    public StaticOptimizer() {
        fixture.add(List.of("DC-1:i1000"));
        fixture.add(List.of("DC-1:i1000", "DC-1:i2000"));
        fixture.add(List.of("DC-1:i1000", "DC-1:i2000"));
        fixture.add(List.of("DC-1:i2000"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));
        fixture.add(List.of("DC-1:i2000", "DC-1:i2999"));

//        fixture.add(List.of("VM-1", "VM-2"));
//        fixture.add(List.of("VM-1", "VM-2", "VM-3"));
//        fixture.add(List.of("VM-3", "VM-100"));
//        fixture.add(List.of("VM-3", "VM-100", "VM-999"));
//        fixture.add(List.of("VM-3", "VM-999"));
//        fixture.add(List.of("VM-3", "VM-999"));
//        fixture.add(List.of("VM-3", "VM-999"));
//        fixture.add(List.of("VM-3", "VM-999"));
//        fixture.add(List.of("VM-3", "VM-999"));
//        fixture.add(List.of("VM-3", "VM-999"));
//        fixture.add(List.of("VM-3", "VM-999"));
//        fixture.add(List.of("VM-3", "VM-999"));
    }


    public OptimizationResult optimize(State state) {
        Map<String, Boolean> result = new HashMap<>();

        state.getLeasedVms()
                .forEach(vm -> result.put(vm.getId(), false));

        if (!fixture.isEmpty()) {
            var currentFixture = fixture.pop();
            currentFixture.forEach(vmId -> result.put(vmId, true));
        }

        return new OptimizationResult(result, null);
    }

}
