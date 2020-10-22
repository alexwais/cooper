package at.alexwais.cooper.scheduler.mapek;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.genetic.FitnessFunction;
import at.alexwais.cooper.genetic.GeneticAlgorithm;
import at.alexwais.cooper.ilp.IlpOptimizer;
import at.alexwais.cooper.scheduler.GreedyOptimizer;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.State;
import at.alexwais.cooper.scheduler.Validator;
import at.alexwais.cooper.scheduler.dto.ExecutionPlan;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

public class Planner {

    private final Model model;
    private final Validator validator;
    private final FitnessFunction fitnessFunction;
    private final GeneticAlgorithm geneticOptimizer;
    private final IlpOptimizer ilpOptimizer;

    @Getter
    private final List<OptimizationResult> greedyOptimizations = new ArrayList<>();
    @Getter
    private final List<OptimizationResult> geneticOptimizations = new ArrayList<>();

    public Planner(Model model, Validator validator) {
        this.model = model;
        this.validator = validator;
        this.fitnessFunction = new FitnessFunction(model, validator);
        this.geneticOptimizer = new GeneticAlgorithm(model, validator);
        this.ilpOptimizer = new IlpOptimizer(model);
    }

    public ExecutionPlan plan(State state) {
        var greedyOptimizer = new GreedyOptimizer(model, state);
        var greedyResult = greedyOptimizer.optimize(state);
        greedyResult.setFitness(fitnessFunction.eval(greedyResult.getAllocation(), state));

//        var geneticResult = geneticOptimizer.optimize(state);

        var ilpResult = ilpOptimizer.optimize(state);
        ilpResult.setFitness(fitnessFunction.eval(ilpResult.getAllocation(), state));

        greedyOptimizations.add(greedyResult);
        geneticOptimizations.add(ilpResult);

        var optimizationResult = ilpResult;
//        var optimizationResult = geneticResult;
//        if (!validator.isAllocationValid(optimizationResult.getAllocation(), state.getTotalServiceLoad())) {
//            throw new IllegalStateException("Invalid allocation!");
//        }


        List<String> vmLaunchList = new ArrayList<>();
        List<String> vmKillList = new ArrayList<>();

        model.getVms().forEach((vmId, vm) -> {
            var isRunning = state.getLeasedProviderVms().containsKey(vmId);
            var shouldRun = optimizationResult.getAllocation().getAllocatedVms().contains(vm);

            if (!isRunning && shouldRun) {
                vmLaunchList.add(vmId);
            } else if (isRunning && !shouldRun) {
                vmKillList.add(vmId);
            }
        });

        List<Pair<String, ContainerType>> containerLaunchList = new ArrayList<>();
        List<Pair<String, ContainerType>> containerKillList = new ArrayList<>();

        optimizationResult.getAllocation().getTuples().stream()
                .forEach(a -> {
                    var allocate = a.isAllocate();
                    var containersRunningOnVm = state.getRunningContainersByVm(a.getVm().getId());
                    var isContainerTypeRunningOnVm = containersRunningOnVm.stream().anyMatch(c -> c.getConfiguration().equals(a.getContainer()));
                    if (allocate && !isContainerTypeRunningOnVm) {
                        containerLaunchList.add(Pair.of(a.getVm().getId(), a.getContainer()));
                    } else if (!allocate && isContainerTypeRunningOnVm) {
                        containerKillList.add(Pair.of(a.getVm().getId(), a.getContainer()));
                    } else {
                        // nothing to do
                    }
                });

        return new ExecutionPlan(optimizationResult.getAllocation(), vmLaunchList, vmKillList, containerLaunchList, containerKillList);
    }

}
