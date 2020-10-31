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
import java.util.HashMap;
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
    private long gracePeriodUntil = 0;

    private static final long MAX_RUNTIME = 300L; // seconds
    private static final long GRACE_PERIOD = 120L; // seconds


    @Autowired
    public SchedulingLoop(Model model) {
        this.model = model;
        this.currentState = new State(model);
        this.currentState.setCurrentTargetAllocation(new Allocation(model, new HashMap<>()));

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

    class SchedulingListener implements Listener {
        @Override
        public void cycleElapsed(long clock, Scheduler scheduler) {
            currentClock = clock;
            if (currentState.isInGracePeriod() && currentClock >= gracePeriodUntil) {
                currentState.setInGracePeriod(false);
            }

            if (currentState.isInGracePeriod()) {
                log.info("\n\n########### Cooper Scheduling Cycle ########### at {}s (GRACE PERIOD) ", currentClock);
            } else {
                log.info("\n\n########### Cooper Scheduling Cycle ########### at {}s ", currentClock);
            }


            if (clock >= MAX_RUNTIME) {
                scheduler.abort();
            }


            monitor();
            analyze();

            var executionPlan = planner.plan(currentState);

            if (executionPlan.isReallocation()) {
                executor.execute(scheduler, executionPlan, currentState);
                currentState.setCurrentTargetAllocation(executionPlan.getOptimizationResult().getAllocation());
                currentState.setLastOptimizationResult(executionPlan.getOptimizationResult());

                currentState.setInGracePeriod(true);
                gracePeriodUntil = currentClock + GRACE_PERIOD;
            } else {
                log.info("No reallocation performed!");
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
        currentState.setCurrentMeasures(new ActivityMeasures(model, measuredLoad));
    }

    private void analyze() {
        var analysisResult = analyzer.analyze(currentState);
        currentState.setCurrentAnalysisResult(analysisResult);
    }


    private void printServiceLoad() {
        System.out.println();
        System.out.println("Service Load:");
        System.out.println();
        var serviceCapacity = currentState.getServiceCapacity();
        var table = new ConsoleTable("Service", "Load", "Capacity");
        currentState.getCurrentMeasures().getTotalServiceLoad().forEach((key, value) -> table.addRow(key, value, serviceCapacity.get(key)));
        table.print();
    }

    private void printAllocationStatus() {
        System.out.println();
        System.out.println("VM Allocation:");
        System.out.println();
        var table = new ConsoleTable("ID", "Type", "Free CPU", "Free Memory", "Containers");
        currentState.getLeasedVms().forEach(vm -> {
            var containerList = currentState.getRunningContainersByVm().get(vm.getId());
            var allocatedContainerTypes = containerList != null ? containerList.stream().map(c -> c.getConfiguration().getLabel()).collect(Collectors.joining(", ")) : "-";
            var capacity = currentState.getFreeCapacity(vm.getId());
            table.addRow(vm.getId(), vm.getType().getLabel(), capacity.getLeft(), capacity.getRight(), allocatedContainerTypes);
        });
        table.print();
    }

}
