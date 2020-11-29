package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.ConsoleTable;
import at.alexwais.cooper.benchmark.BenchmarkRecord;
import at.alexwais.cooper.benchmark.BenchmarkService;
import at.alexwais.cooper.cloudsim.CloudSimRunner;
import at.alexwais.cooper.config.OptimizationConfig;
import at.alexwais.cooper.csp.CloudProvider;
import at.alexwais.cooper.csp.Listener;
import at.alexwais.cooper.csp.Scheduler;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.ExecutionPlan;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import at.alexwais.cooper.scheduler.mapek.Analyzer;
import at.alexwais.cooper.scheduler.mapek.Executor;
import at.alexwais.cooper.scheduler.mapek.Monitor;
import at.alexwais.cooper.scheduler.mapek.Planner;
import at.alexwais.cooper.scheduler.simulated.EndOfScenarioException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SchedulingCycle {

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

    @Autowired
    private BenchmarkService benchmarkService;

    private long currentClock = 0L; // seconds
    private Allocation drainedTargetAllocation;

    @Autowired
    public SchedulingCycle(Model model, OptimizationConfig config) {
        this.model = model;
        this.currentState = new State(model);
        this.currentState.setCurrentTargetAllocation(new Allocation(model));

        this.validator = new Validator(model);

        this.analyzer = new Analyzer(model);
        this.planner = new Planner(model, validator, config);
        this.executor = new Executor(model);
    }


    public void run() {
        var listener = new SchedulingListener();
        cloudProvider.registerListener(listener);
        cloudProvider.run();
    }


    class SchedulingListener implements Listener {
        @Override
        public void cycleElapsed(long clock, Scheduler scheduler) {
            currentClock = clock;

            logCycleTimestamp();

            // The final (drained) target allocation will be enforced after the current cycle completed.
            // A new Optimization may take place immediately after grace period (with containers/VMs still running).
            if (drainedTargetAllocation != null) {
                log.info("Applying final target allocation after draining period...");
                executor.execute(scheduler, drainedTargetAllocation, currentState);
                currentState.setCurrentTargetAllocation(drainedTargetAllocation);
                drainedTargetAllocation = null;
                log.info("Final target allocation after grace period:");
                printAllocationStatus();
            }

            try {
                monitor();
            } catch (EndOfScenarioException e) {
                scheduler.abort();
                tearDown();
                try {
                    benchmarkService.print();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }

            analyze();

            printServiceLoad();

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
            }

            printAllocationStatus();
            benchmark(executionPlan);
        }

        @Override
        public int getClockInterval() {
            return 30;
        }
    }

    private void monitor() throws EndOfScenarioException {
        var measuredLoad = monitor.getCurrentLoad((int) currentClock);
        currentState.setCurrentSystemMeasures(new SystemMeasures(model, measuredLoad, currentState.getCurrentTargetAllocation()));
    }

    private void analyze() {
        var analysisResult = analyzer.analyze(currentState);
        currentState.setCurrentAnalysisResult(analysisResult);
    }

    private void benchmark(ExecutionPlan executionPlan) {
        var isEvenMinute = currentClock % 120 == 0;
        if (isEvenMinute) {
            var minute = currentClock / 60;
            var record = new BenchmarkRecord(
                    (int) minute,
                    currentState.getCurrentSystemMeasures(),
                    currentState.getCurrentAnalysisResult(),
                    executionPlan.getOptimizationResult(),
                    currentState.getCurrentTargetAllocation()
            );
            benchmarkService.addRecord(record);
        }
    }

    private void tearDown() {
        var totalGeneticCost = planner.getOptimizations().stream()
                .map(o -> o.getAllocation().getTotalCost())
                .reduce(0f, Float::sum);
//        var totalGreedyCost = planner.getGreedyOptimizations().stream()
//                .map(o -> o.getAllocation().getTotalCost())
//                .reduce(0f, Float::sum);

        var totalGeneticFitness = planner.getOptimizations().stream()
                .map(OptimizationResult::getFitness)
                .reduce(0f, Float::sum);
//        var totalGreedyFitness = planner.getGreedyOptimizations().stream()
//                .map(OptimizationResult::getFitness)
//                .reduce(0f, Float::sum);

//        var averageRuntime = planner.getGeneticOptimizations().stream()
//                .mapToInt(o -> o.getRuntimeInMilliseconds().intValue())
//                .average().getAsDouble();

        log.info(" *** Total Cost: {}", totalGeneticCost);
//        log.info(" *** Total Greedy Cost: {}", totalGreedyCost);
        log.info(" *** Total Fitness: {}", totalGeneticFitness);
//        log.info(" *** Total Greedy Fitness: {}", totalGreedyFitness);
//        log.info(" *** Avg. Runtime: {}s", averageRuntime / 1000d);
    }

    private void logCycleTimestamp() {
        var secondsPerMinute = new BigDecimal(60);
        var plainSeconds = new BigDecimal(currentClock);
        var minutes = plainSeconds.divide(secondsPerMinute, RoundingMode.FLOOR);
        var seconds = plainSeconds.remainder(secondsPerMinute);
        var elapsedTime = MessageFormat.format("{0,number,#00}:{1,number,#00}", minutes, seconds);

        log.info("\n\n########### Cooper Scheduling Cycle @ {} ###########", elapsedTime);
    }

    private void printServiceLoad() {
        System.out.println();
        System.out.println("Service Load before adaption:");
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
