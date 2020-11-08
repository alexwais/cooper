package at.alexwais.cooper.scheduler.mapek;

import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.genetic.FitnessFunction;
import at.alexwais.cooper.genetic.GeneticAlgorithm;
import at.alexwais.cooper.ilp.IlpOptimizer;
import at.alexwais.cooper.scheduler.MapUtils;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.State;
import at.alexwais.cooper.scheduler.Validator;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.ExecutionPlan;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;


@Slf4j
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


    private static final float DRIFT_TOLERANCE = 2.0f; // percentage


    @Getter
    @RequiredArgsConstructor
    class ReallocationPlan {
        @Setter
        private int step = 0;

        private final OptimizationResult optimizationResult;
        private final List<VmInstance> vmLaunchList;
        private final List<Pair<VmInstance, ContainerType>> containerLaunchList;
        private final Map<VmInstance, List<ContainerType>> replacedContainers; // replaced during vertical container scaling within same vm
//        private final List<String> vmKillList; // last
    }


    public Planner(Model model, Validator validator) {
        this.model = model;
        this.validator = validator;
        this.fitnessFunction = new FitnessFunction(model, validator);
        this.geneticOptimizer = new GeneticAlgorithm(model, validator);
        this.ilpOptimizer = new IlpOptimizer(model);
    }

    private ReallocationPlan currentReallocation = null;

    public ExecutionPlan plan(State state) {
        if (currentReallocation != null) {
            var resultingTargetAllocation = applyReallocation(state.getCurrentTargetAllocation(), currentReallocation);
            if (currentReallocation.getStep() >= 4) {
                currentReallocation = null;
            }
            return new ExecutionPlan(resultingTargetAllocation);
        } else {
            if (!isOptimizationRequired(state)) {
                log.info("Current allocation within drift margins. No optimization triggered.");
                return noOpExecution(state.getCurrentTargetAllocation());
            }

            var optimizationResult = optimize(state);
            var reallocationPlan = buildReallocationPlan(optimizationResult, state);
            var resultingTargetAllocation = applyReallocation(state.getCurrentTargetAllocation(), reallocationPlan);

            this.currentReallocation = reallocationPlan;

            return new ExecutionPlan(resultingTargetAllocation, optimizationResult);
        }
    }

    private ExecutionPlan noOpExecution(Allocation currentTargetAllocation) {
        return new ExecutionPlan(false, currentTargetAllocation);
    }


    private boolean isOptimizationRequired(State state) {
        var isLoadDriftExceeded = state.getCurrentAnalysisResult().getLoadDriftByService().entrySet().stream()
                .anyMatch(e -> {
                    var isExceeding = Math.abs(e.getValue()) > DRIFT_TOLERANCE;
                    if (isExceeding) log.debug("Load of {} changed significantly: {}%", e.getKey(), e.getValue());
                    return isExceeding;
                });
        var isCapacityDriftExceeded = state.getCurrentAnalysisResult().getCapacityDriftByService().entrySet().stream()
                .anyMatch(e -> {
                    var isExceeding = Math.abs(e.getValue()) > DRIFT_TOLERANCE;
                    if (isExceeding) log.debug("Capacity of {} changed significantly: {}%", e.getKey(), e.getValue());
                    return isExceeding;
                });

        if (state.getCurrentAnalysisResult().isCurrentAllocationUnderprovisioned()) {
            log.warn("Current allocation is under-provisioned!");
        }
        if (!state.getCurrentAnalysisResult().isCurrentAllocationValid()) {
            log.warn("Current allocation is invalid!");
        }
        if (isLoadDriftExceeded) {
            log.warn("Load drift detected!");
        }
        if (isCapacityDriftExceeded) {
            log.warn("Capacity drift detected!");
        }

        // TODO compare neutral fitness

        if (state.getCurrentAnalysisResult().isCurrentAllocationValid()
                && !state.getCurrentAnalysisResult().isCurrentAllocationUnderprovisioned()
                && !isLoadDriftExceeded
                && !isCapacityDriftExceeded) {
           return false;
        }
        return true;
    }


    private OptimizationResult optimize(State state) {
//        var greedyOptimizer = new GreedyOptimizer(model, state);
//        var greedyResult = greedyOptimizer.optimize(state);
//        greedyResult.setFitness(fitnessFunction.eval(greedyResult.getAllocation(), state.getCurrentSystemMeasures()));

        var geneticResult = geneticOptimizer.optimize(state);

//        var ilpResult = ilpOptimizer.optimize(state);
//        ilpResult.setFitness(fitnessFunction.eval(ilpResult.getAllocation(), state.getCurrentSystemMeasures()));

//        greedyOptimizations.add(greedyResult);
        geneticOptimizations.add(geneticResult);

//        var optimizationResult = ilpResult;
        var optimizationResult = geneticResult;
        if (!validator.isAllocationValid(optimizationResult.getAllocation(), state.getCurrentTargetAllocation(), state.getCurrentSystemMeasures().getTotalServiceLoad())) {
            throw new IllegalStateException("Invalid allocation!");
        }
        return optimizationResult;
    }

    private ReallocationPlan buildReallocationPlan(OptimizationResult optimizationResult, State state) {
        List<VmInstance> vmLaunchList = new ArrayList<>();

        model.getVms().forEach((vmId, vm) -> {
            var isRunning = state.getCurrentTargetAllocation().getRunningVms().contains(vm);
            var shouldRun = optimizationResult.getAllocation().getAllocatedVms().contains(vm);

            if (!isRunning && shouldRun) {
                vmLaunchList.add(vm);
            } else if (isRunning && !shouldRun) {
                // vmKillList.add(vmId);
            }
        });

        List<Pair<VmInstance, ContainerType>> containerLaunchList = new ArrayList<>();
        Map<VmInstance, List<ContainerType>> scaledContainersToRemove = new HashMap<>();

        optimizationResult.getAllocation().getTuples().stream()
                .forEach(a -> {
                    var allocate = a.isAllocate();
                    var containersRunningOnVm = state.getCurrentTargetAllocation().getAllocationMap().getOrDefault(a.getVm(), new ArrayList<>());
                    var isContainerTypeRunningOnVm = containersRunningOnVm.stream().anyMatch(c -> c.equals(a.getContainer()));
                    var previousContainerOfSameService = containersRunningOnVm.stream()
                            .filter(c -> c.getService().equals(a.getContainer().getService()))
                            .findFirst();

                    if (allocate && !isContainerTypeRunningOnVm) {
                        previousContainerOfSameService.ifPresent(c ->
                                // in case fo vertical container scaling within the same VM
                                MapUtils.putToMapList(scaledContainersToRemove, a.getVm(), c)
                        );
                        containerLaunchList.add(Pair.of(a.getVm(), a.getContainer()));
                    } else if (!allocate && isContainerTypeRunningOnVm) {
                        // containerKillList.add(Pair.of(a.getVm(), a.getContainer()));
                    } else {
                        // nothing to do
                    }
                });

        return new ReallocationPlan(optimizationResult, vmLaunchList, containerLaunchList, scaledContainersToRemove);
    }

    private Allocation applyReallocation(Allocation currentTargetAllocation, ReallocationPlan reallocationPlan) {
        reallocationPlan.setStep(reallocationPlan.getStep() + 1);

        if (reallocationPlan.getStep() == 1) {
            var vmsToRun = new ArrayList<VmInstance>();
            vmsToRun.addAll(currentTargetAllocation.getRunningVms());
            vmsToRun.addAll(reallocationPlan.getVmLaunchList());

            return new Allocation(model, vmsToRun, currentTargetAllocation.getAllocationMap());
        } else if (reallocationPlan.getStep() == 2) {
            // remove scaled container instances
            var allocationTuples = currentTargetAllocation.getAllocatedTuples().stream()
                    .filter(a -> {
                        var containersToRemove = reallocationPlan.getReplacedContainers().getOrDefault(a.getVm(), Collections.emptyList());
                        return !containersToRemove.contains(a.getContainer());
                    }).collect(Collectors.toList());

            // add new container instances
            reallocationPlan.getContainerLaunchList().forEach(p -> {
                allocationTuples.add(new Allocation.AllocationTuple(p.getLeft(), p.getRight(), true));
            });

            return new Allocation(model, allocationTuples);
        } else if (reallocationPlan.getStep() == 3) {
            // container draining period starts here, so no previous allocation is maintained
            return currentTargetAllocation;
        } else if (reallocationPlan.getStep() == 4) {
            // the optimization target allocation already leads to killing of any abandoned containers & VMs
            return reallocationPlan.getOptimizationResult().getAllocation();
        }
        throw new IllegalStateException("Cannot apply reallocation for step " + reallocationPlan.getStep());
    }

}
