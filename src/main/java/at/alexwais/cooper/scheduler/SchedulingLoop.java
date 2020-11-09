package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.ConsoleTable;
import at.alexwais.cooper.cloudsim.CloudSimRunner;
import at.alexwais.cooper.csp.CloudProvider;
import at.alexwais.cooper.csp.Listener;
import at.alexwais.cooper.csp.Scheduler;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import at.alexwais.cooper.scheduler.mapek.Analyzer;
import at.alexwais.cooper.scheduler.mapek.Executor;
import at.alexwais.cooper.scheduler.mapek.Monitor;
import at.alexwais.cooper.scheduler.mapek.Planner;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SchedulingLoop {

    private final CloudProvider cloudProvider = new CloudSimRunner();
    private final Validator validator;

    // MAPE-K (Monitor - Analyze - Plan - Execute)
    @Autowired
    private Monitor monitor;
    private final Analyzer analyzer;
    private final Planner planner;
    private final Executor executor;

    // Knowledge Base
    private final Model model;
    private final State currentState;

    private long currentClock = 0L; // seconds

    private static final long MAX_RUNTIME = 1260L; // seconds
    private static final long GRACE_PERIOD = 120L; // seconds


    @Autowired
    public SchedulingLoop(Model model) {
        this.model = model;
        this.currentState = new State(model);
        this.currentState.setCurrentTargetAllocation(new Allocation(model));

        this.validator = new Validator(model);

        this.analyzer = new Analyzer(model);
        this.planner = new Planner(model, validator);
        this.executor = new Executor(model);
    }


    public void run() {
        cloudProvider.registerListener(new SchedulingListener());
        cloudProvider.run();
    }

    private void tearDown() {
        var totalGeneticCost = planner.getGeneticOptimizations().stream()
                .map(o -> o.getAllocation().getTotalCost())
                .reduce(0f, Float::sum);
        var totalGreedyCost = planner.getGreedyOptimizations().stream()
                .map(o -> o.getAllocation().getTotalCost())
                .reduce(0f, Float::sum);

        var totalGeneticFitness = planner.getGeneticOptimizations().stream()
                .map(OptimizationResult::getFitness)
                .reduce(0f, Float::sum);
        var totalGreedyFitness = planner.getGreedyOptimizations().stream()
                .map(OptimizationResult::getFitness)
                .reduce(0f, Float::sum);

//        var averageRuntime = planner.getGeneticOptimizations().stream()
//                .mapToInt(o -> o.getRuntimeInMilliseconds().intValue())
//                .average().getAsDouble();

        log.info(" *** Total Genetic Cost: {}", totalGeneticCost);
        log.info(" *** Total Greedy Cost: {}", totalGreedyCost);
        log.info(" ***");
        log.info(" *** Total Genetic Fitness: {}", totalGeneticFitness);
        log.info(" *** Total Greedy Fitness: {}", totalGreedyFitness);
        log.info(" ***");
//        log.info(" *** Avg. Runtime: {}s", averageRuntime / 1000d);
    }


    private Allocation drainedTargetAllocation;

    class SchedulingListener implements Listener {
        @Override
        public void cycleElapsed(long clock, Scheduler scheduler) {
            currentClock = clock;
            log.info("\n\n########### Cooper Scheduling Cycle ########### at {}s ", currentClock);
            if (clock >= MAX_RUNTIME) {
                scheduler.abort();
            }

            // The final (drained) target allocation will be enforced after the current cycle completed
            // A new Optimization may take place immediately after grace period (with containers/VMs still running)
            if (drainedTargetAllocation != null) {
                log.info("Applying final target allocation after draining period...");
                executor.execute(scheduler, drainedTargetAllocation, currentState);
                currentState.setCurrentTargetAllocation(drainedTargetAllocation);
                drainedTargetAllocation = null;
            }

            monitor();
            analyze();

            var executionPlan = planner.plan(currentState);

            if (executionPlan.isReallocation()) {
                executor.execute(scheduler, executionPlan.getTargetAllocation(), currentState);
                currentState.setCurrentTargetAllocation(executionPlan.getTargetAllocation());

                if (executionPlan.getDrainedTargetAllocation() != null) {
                    drainedTargetAllocation = executionPlan.getDrainedTargetAllocation();
                }
                if (executionPlan.getOptimizationResult() != null) {
                    currentState.setLastOptimizationResult(executionPlan.getOptimizationResult());
                }
            } else {
                // log.info("No reallocation performed!");
            }

            printServiceLoad();
            printAllocationStatus();

            if (clock >= MAX_RUNTIME) {
                tearDown();
            }
        }

        @Override
        public int getClockInterval() {
            return 30;
        }
    }

    private void monitor() {
        var measuredLoad = monitor.getCurrentLoad((int) currentClock);
        currentState.setCurrentSystemMeasures(new SystemMeasures(model, measuredLoad, currentState.getCurrentTargetAllocation()));
    }

    private void analyze() {
        var analysisResult = analyzer.analyze(currentState);
        currentState.setCurrentAnalysisResult(analysisResult);
    }


    private void printServiceLoad() {
        System.out.println();
        System.out.println("Service Load:");
        System.out.println();
        var serviceCapacity = currentState.getCurrentTargetAllocation().getServiceCapacity();
        var table = new ConsoleTable("Service", "Load", "Capacity");
        currentState.getCurrentSystemMeasures().getTotalServiceLoad().forEach((key, value) -> table.addRow(key, value, serviceCapacity.getOrDefault(key, 0L)));
        table.print();
    }

    private void printAllocationStatus() {
        System.out.println();
        System.out.println("VM Allocation:");
        System.out.println();
        var table = new ConsoleTable("ID", "Type", "Free CPU", "Free Memory", "Containers");
        currentState.getCurrentTargetAllocation().getRunningVms().forEach(vm -> {
            var containerList = currentState.getProviderState().getRunningContainersByVm(vm);
            var allocatedContainerTypes = containerList != null ? containerList.stream().map(a -> a.getContainer().getLabel()).collect(Collectors.joining(", ")) : "-";
            var capacity = currentState.getFreeCapacity(vm);
            if (capacity.getLeft() < 0 || capacity.getRight() < 0L) {
                throw new IllegalStateException("Capacity is negative");
            }
            table.addRow(vm.getId(), vm.getType().getLabel(), capacity.getLeft(), capacity.getRight(), allocatedContainerTypes);
        });
        table.print();
    }

}
