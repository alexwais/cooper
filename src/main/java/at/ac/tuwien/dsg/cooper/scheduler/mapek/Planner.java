package at.ac.tuwien.dsg.cooper.scheduler.mapek;

import at.ac.tuwien.dsg.cooper.api.Optimizer;
import at.ac.tuwien.dsg.cooper.config.OptimizationConfig;
import at.ac.tuwien.dsg.cooper.domain.ContainerType;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.genetic.FitnessFunction;
import at.ac.tuwien.dsg.cooper.genetic.GeneticAlgorithmOptimizer;
import at.ac.tuwien.dsg.cooper.ilp.IlpOptimizer;
import at.ac.tuwien.dsg.cooper.scheduler.*;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.ExecutionPlan;
import at.ac.tuwien.dsg.cooper.scheduler.dto.OptResult;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.StopWatch;


@Slf4j
public class Planner {

    private final Model model;
    private final Validator validator;
    private final OptimizationConfig config;
    private final FitnessFunction fitnessFunction;
    private final Optimizer geneticOptimizer;
    private final Optimizer ilpOptimizer;
    private final Optimizer firstFitOptimizer;

    @Getter
    private final List<OptResult> greedyOptimizations = new ArrayList<>();
    @Getter
    private final List<OptResult> optimizations = new ArrayList<>();


    private static final float LOAD_DRIFT_TOLERANCE = 2.0f; // percentage
    private static final float CAPACITY_DRIFT_TOLERANCE = 2.0f; // percentage
    private static final float FITNESS_DRIFT_TOLERANCE = 5.0f; // percentage


    @Getter
    @RequiredArgsConstructor
    class ReallocationPlan {
        @Setter
        private int step = 0;

        private final OptResult optResult;
        private final List<VmInstance> vmLaunchList;
        private final List<Pair<VmInstance, ContainerType>> containerLaunchList;
        private final Map<VmInstance, List<ContainerType>> replacedContainers; // replaced during vertical container scaling within same vm
//        private final List<String> vmKillList; // last
    }


    public Planner(Model model, Validator validator, OptimizationConfig config) {
        this.model = model;
        this.validator = validator;
        this.config = config;
        this.fitnessFunction = new FitnessFunction(model, validator, config.getGaLatencyWeight());
        this.geneticOptimizer = new GeneticAlgorithmOptimizer(model, config, validator);
        this.ilpOptimizer = new IlpOptimizer(model, config);
        this.firstFitOptimizer = new FirstFitOptimizer(model);
    }

    private ReallocationPlan currentReallocation = null;

    public ExecutionPlan plan(State state) {
        var isInGracePeriod = currentReallocation != null;

        if (isInGracePeriod) {
            var resultingTargetAllocation = applyReallocation(state.getCurrentTargetAllocation(), currentReallocation);
            if (currentReallocation.getStep() == 4) {
                var drainedTargetAllocation = currentReallocation.optResult.getAllocation();
                currentReallocation = null;
                // the final (drained) target allocation will be enforced after the current cycle completed
                return new ExecutionPlan(resultingTargetAllocation, drainedTargetAllocation);
            }
            return new ExecutionPlan(resultingTargetAllocation);
        } else {
            if (!isOptimizationRequired(state)) {
                log.info("Current allocation within drift margins. No optimization triggered.");
                return noOpExecution(state.getCurrentTargetAllocation());
            }

            var optimizationResult = optimize(state.getCurrentTargetAllocation(), state);
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
                    var isExceeding = Math.abs(e.getValue()) > LOAD_DRIFT_TOLERANCE;
                    if (isExceeding) log.debug("Load of {} changed significantly: {}%", e.getKey(), e.getValue());
                    return isExceeding;
                });
        // Capacity drift is actually not possible in our setup (only if resources change du to extrinsic factors, e.g. crashed VMs...)
        var isCapacityDriftExceeded = state.getCurrentAnalysisResult().getCapacityDriftByService().entrySet().stream()
                .anyMatch(e -> {
                    var isExceeding = Math.abs(e.getValue()) > CAPACITY_DRIFT_TOLERANCE;
                    if (isExceeding) log.debug("Capacity of {} changed significantly: {}%", e.getKey(), e.getValue());
                    return isExceeding;
                });
        var isFitnessDriftExceeded = state.getCurrentAnalysisResult().getFitnessDrift() > FITNESS_DRIFT_TOLERANCE;

        if (!state.getCurrentAnalysisResult().isCurrentAllocationValid()) {
            log.warn("Current allocation is invalid!");
        }
        if (state.getCurrentAnalysisResult().isCurrentAllocationUnderprovisioned()) {
            log.warn("Current allocation is under-provisioned!");
        }
        if (isLoadDriftExceeded) {
            log.warn("Load drift detected!");
        }
        if (isCapacityDriftExceeded) {
            log.warn("Capacity drift detected!");
        }
        if (isFitnessDriftExceeded) {
            log.warn("Fitness drift detected!");
        }

        if (state.getCurrentAnalysisResult().isCurrentAllocationValid()
                && !state.getCurrentAnalysisResult().isCurrentAllocationUnderprovisioned()
                && !isLoadDriftExceeded
                && !isCapacityDriftExceeded
                && !isFitnessDriftExceeded) {
           return false;
        }
        return true;
    }


    private OptResult optimize(Allocation previousAllocation, State state) {
        var measures = state.getCurrentSystemMeasures();

        OptResult optResult = null;

        var stopWatch = new StopWatch();
        stopWatch.start();

        switch (config.getStrategy()) {
            case GA:
            case GA_NC:
                optResult = geneticOptimizer.optimize(previousAllocation, measures, state.getImageCacheState());
                break;
            case ILP:
            case ILP_NC:
                optResult = ilpOptimizer.optimize(previousAllocation, measures, state.getImageCacheState());
                break;
            case FF:
                optResult = firstFitOptimizer.optimize(previousAllocation, measures, state.getImageCacheState());
                break;
        }

        stopWatch.stop();
        optResult.setRuntimeInMilliseconds(stopWatch.getTotalTimeMillis());

        if (!validator.isAllocationValid(optResult.getAllocation(), previousAllocation, measures.getTotalServiceLoad())) {
            throw new IllegalStateException("Invalid allocation!");
        }

        var fitness = fitnessFunction.eval(optResult.getAllocation(), previousAllocation, measures, state.getImageCacheState(), false);
        optResult.setFitness(fitness);
        var neutralFitness = fitnessFunction.evalNeutral(optResult.getAllocation(), measures);
        optResult.setNeutralFitness(neutralFitness);

        optimizations.add(optResult);

        return optResult;
    }

    private ReallocationPlan buildReallocationPlan(OptResult optResult, State state) {
        List<VmInstance> vmLaunchList = new ArrayList<>();

        model.getVms().forEach((vmId, vm) -> {
            var isRunning = state.getCurrentTargetAllocation().getRunningVms().contains(vm);
            var shouldRun = optResult.getAllocation().getRunningVms().contains(vm);

            if (!isRunning && shouldRun) {
                vmLaunchList.add(vm);
            } else if (isRunning && !shouldRun) {
                // vmKillList.add(vmId);
            }
        });

        List<Pair<VmInstance, ContainerType>> containerLaunchList = new ArrayList<>();
        Map<VmInstance, List<ContainerType>> scaledContainersToRemove = new HashMap<>();

        optResult.getAllocation().getTuples().stream()
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

        return new ReallocationPlan(optResult, vmLaunchList, containerLaunchList, scaledContainersToRemove);
    }

    private Allocation applyReallocation(Allocation currentTargetAllocation, ReallocationPlan reallocationPlan) {
        reallocationPlan.setStep(reallocationPlan.getStep() + 1);

        log.info("GRACE PERIOD - applying reallocation cycle {}/4", reallocationPlan.getStep());

        switch (reallocationPlan.getStep()) {

            case 1: // t+0s (start new VMs)
                var vmsToRun = new ArrayList<VmInstance>();
                vmsToRun.addAll(currentTargetAllocation.getRunningVms());
                vmsToRun.addAll(reallocationPlan.getVmLaunchList());
                return new Allocation(model, vmsToRun, currentTargetAllocation.getAllocationMap());

            case 2: // t+30s
                return currentTargetAllocation;

            case 3: // t+60s (VM startup time elapsed -> start containers)
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

            case 4: // t+90s (container startup time elapsed -> drain containers)
                // container draining period starts here, so previous allocation is maintained
                return currentTargetAllocation;

            // case 5: // t+60s (container draining period elapsed -> terminate containers & VMs)
                // the optimization target allocation is used which leads to killing of any abandoned containers & VMs
                // the final (drained) target allocation will be enforced during main loop after the current cycle completed
        }

        throw new IllegalStateException("Cannot apply reallocation for step " + reallocationPlan.getStep());
    }

}
