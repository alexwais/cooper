package at.alexwais.cooper.exec;

import java.util.*;


public class Optimizer {

    private Deque<List<String>> fixture = new ArrayDeque<>();

    public Optimizer() {
        fixture.add(List.of("VM-1"));
        fixture.add(List.of("VM-1", "VM-2"));
        fixture.add(List.of("VM-1", "VM-2", "VM-3"));
        fixture.add(List.of("VM-3", "VM-100"));
        fixture.add(List.of("VM-3", "VM-100", "VM-999"));
        fixture.add(List.of("VM-3", "VM-999"));
        fixture.add(List.of("VM-3", "VM-999"));
        fixture.add(List.of("VM-3", "VM-999"));
        fixture.add(List.of("VM-3", "VM-999"));
        fixture.add(List.of("VM-3", "VM-999"));
        fixture.add(List.of("VM-3", "VM-999"));
        fixture.add(List.of("VM-3", "VM-999"));
        fixture.add(List.of("VM-3", "VM-999"));
    }


    public OptimizationResult optimize(OptimizationInput input) {
        Map<String, Boolean> result = new HashMap<>();

        input.getRunningVms().keySet()
                .forEach(vmId -> result.put(vmId, false));

        if (!fixture.isEmpty()) {
            var currentFixture = fixture.pop();
            currentFixture.forEach(vmId -> result.put(vmId, true));
        }

        return new OptimizationResult(result);
    }

}
