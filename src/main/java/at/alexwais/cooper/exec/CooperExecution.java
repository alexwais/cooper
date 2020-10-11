package at.alexwais.cooper.exec;

import at.alexwais.cooper.ConsoleTable;
import at.alexwais.cooper.cloudsim.CloudSimRunner;
import at.alexwais.cooper.csp.CloudProvider;
import at.alexwais.cooper.csp.Listener;
import at.alexwais.cooper.csp.Scheduler;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

@Slf4j
public class CooperExecution {

    private final CloudProvider cloudProvider = new CloudSimRunner();

    private final Model model;
    private final State currentState;

    private final Validator validator;

    private final SimulatedMonitor monitor;
    private final Planner planner;


    private final long MAX_RUNTIME = 300L;

    public CooperExecution(Model model) {
        this.model = model;
        this.currentState = new State(model);
        this.validator = new Validator(model);

        this.monitor = new SimulatedMonitor(model);
        this.planner = new Planner(model, validator);
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

            // MAPE-K (Monitor - Analyze(Optimize) - Plan - Execute)
            monitor();
            analyze();
            var executionPlan = planner.plan(currentState);
            execute(scheduler, executionPlan);

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
        var affinityGraph = new SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class);
        model.getServices().values().forEach(s -> affinityGraph.addVertex(s.getName()));
        model.getServices().values().forEach(s1 -> {
            model.getServices().values().forEach(s2 -> {
                if (!s1.equals(s2)) {
                    affinityGraph.addEdge(s1.getName(), s2.getName(), new DefaultWeightedEdge());

                    var interactionEdge1 = currentState.getInteractionGraph().getEdge(s1.getName(), s2.getName());
                    var interactionEdge2 = currentState.getInteractionGraph().getEdge(s2.getName(), s1.getName());

                    var interaction1 = (int) currentState.getInteractionGraph().getEdgeWeight(interactionEdge1);
                    var interaction2 = (int) currentState.getInteractionGraph().getEdgeWeight(interactionEdge2);

                    var bidirectionalInteraction = interaction1 + interaction2;
                    var affinity = (double) bidirectionalInteraction / (double) currentState.getTotalSystemLoad();

                    var edge = affinityGraph.getEdge(s1.getName(), s2.getName());
                    affinityGraph.setEdgeWeight(edge, affinity);
                }
            });
        });
        currentState.setServiceAffinity(affinityGraph);
    }

    private void execute(Scheduler scheduler, ExecutionPlan plan) {
        plan.getVmsToLaunch().forEach(vmId -> {
            var vm = model.getVms().get(vmId);
            var providerId = scheduler.launchVm(vm.getType().getLabel(), "DC-1");
            currentState.getLeasedProviderVms().put(vmId, providerId);
        });
        plan.getContainersToStart().forEach(a -> {
            var vmId = a.getKey();
            var containerType = a.getValue();
            var providerVmId = currentState.getLeasedProviderVms().get(vmId);
            if (providerVmId == null) {
                log.info("null");
            }

            var providerId = scheduler.launchContainer(1, containerType.getMemory().toMegabytes(), providerVmId);
            currentState.allocateContainerInstance(vmId, containerType, providerId);
        });
        plan.getContainersToStop().forEach(a -> {
            var vmId = a.getKey();
            var containerType = a.getValue();
            var runningContainers = currentState.getRunningContainersByVm(vmId);
            var container = runningContainers.stream()
                    .filter(c -> c.getConfiguration().equals(containerType))
                    .findAny()
                    .orElseThrow();

            var providerContainerId = currentState.getRunningProviderContainers().get(container);
            scheduler.terminateContainer(providerContainerId);
            currentState.deallocateContainerInstance(container);
        });
        // TODO delay termination of vms/containers?
        plan.getVmsToTerminate().forEach(vmId -> {
            var vm = model.getVms().get(vmId);
            var providerId = currentState.getLeasedProviderVms().get(vmId);
            scheduler.terminateVm(providerId);
            currentState.releaseVm(vm.getId());
        });

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
