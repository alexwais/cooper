package at.alexwais.cooper.scheduler;

import at.alexwais.cooper.ConsoleTable;
import at.alexwais.cooper.cloudsim.CloudSimRunner;
import at.alexwais.cooper.csp.CloudProvider;
import at.alexwais.cooper.csp.Listener;
import at.alexwais.cooper.csp.Scheduler;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import at.alexwais.cooper.scheduler.mapek.Analyzer;
import at.alexwais.cooper.scheduler.mapek.Executor;
import at.alexwais.cooper.scheduler.mapek.Planner;
import at.alexwais.cooper.scheduler.mapek.SimulatedMonitor;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SchedulingLoop {

    private final CloudProvider cloudProvider = new CloudSimRunner();
    private final Validator validator;

    // MAPE-K (Monitor - Analyze - Plan - Execute)
    private final SimulatedMonitor monitor;
    private final Analyzer analyzer;
    private final Planner planner;
    private final Executor executor;

    // Knowledge Base
    private final Model model;
    private final State currentState;


    private final long MAX_RUNTIME = 300L;

    public SchedulingLoop(Model model) {
        this.model = model;
        this.currentState = new State(model);
        this.validator = new Validator(model);

        this.monitor = new SimulatedMonitor(model);
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

        var averageRuntime = planner.getGeneticOptimizations().stream()
                .mapToInt(o -> o.getRuntimeInMilliseconds().intValue())
                .average().getAsDouble();

        log.info(" *** Total Genetic Cost: {}", totalGeneticCost);
        log.info(" *** Total Greedy Cost: {}", totalGreedyCost);
        log.info(" ***");
        log.info(" *** Total Genetic Fitness: {}", totalGeneticFitness);
        log.info(" *** Total Greedy Fitness: {}", totalGreedyFitness);
        log.info(" ***");
        log.info(" *** Avg. Runtime: {}s", averageRuntime / 1000d);
    }

    class SchedulingListener implements Listener {
        @Override
        public void cycleElapsed(long clock, Scheduler scheduler) {
            log.info("\n\n########### Cooper Scheduling Cycle ########### {} ", clock);

            if (clock >= MAX_RUNTIME) {
                scheduler.abort();
            }


            monitor();
            analyze();
            var executionPlan = planner.plan(currentState);
            executor.execute(scheduler, executionPlan, currentState);

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
        var measuredLoad = monitor.getCurrentLoad();

        currentState.setExternalServiceLoad(measuredLoad.getExternalServiceLoad());
        currentState.setInternalServiceLoad(measuredLoad.getInternalServiceLoad());
        currentState.setTotalServiceLoad(measuredLoad.getTotalServiceLoad());
        currentState.setTotalSystemLoad(measuredLoad.getTotalSystemLoad());
        currentState.setInteractionGraph(measuredLoad.getInteractionGraph());
    }

    private void analyze() {
        var analysisResult = analyzer.analyze(currentState);
        currentState.setServiceAffinity(analysisResult.getAffinityGraph());
    }


    private void printServiceLoad() {
        System.out.println();
        System.out.println("Service Load:");
        System.out.println();
        var serviceCapacity = currentState.getServiceCapacity();
        var table = new ConsoleTable("Service", "Load", "Capacity");
        currentState.getTotalServiceLoad().forEach((key, value) -> table.addRow(key, value, serviceCapacity.get(key)));
        table.print();
    }

    private void printAllocationStatus() {
        System.out.println();
        System.out.println("VM Allocation:");
        System.out.println();
        var table = new ConsoleTable("ID", "Type", "Free CPU", "Free Memory", "Containers");
        currentState.getLeasedVms().forEach(vm -> {
            var containerList = currentState.getRunningContainersByVm().get(vm.getId());
            var allocatedContainerTypes = containerList != null ? containerList.stream().map(c -> c.getService().getName() + ":" + c.getConfiguration().getLabel()).collect(Collectors.joining(", ")) : "-";
            var capacity = currentState.getFreeCapacity(vm.getId());
            table.addRow(vm.getId(), vm.getType().getLabel(), capacity.getLeft(), capacity.getRight(), allocatedContainerTypes);
        });
        table.print();
    }

}
