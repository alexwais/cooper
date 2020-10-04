package at.alexwais.cooper.exec;

import at.alexwais.cooper.ConsoleTable;
import at.alexwais.cooper.cloudsim.CloudSimRunner;
import at.alexwais.cooper.csp.CloudProvider;
import at.alexwais.cooper.csp.Listener;
import at.alexwais.cooper.csp.Scheduler;
import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.genetic.FitnessFunction;
import at.alexwais.cooper.genetic.GeneticAlgorithm;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

@Slf4j
public class CooperExecution {

    private final FakeMonitor monitor = new FakeMonitor();
    private final CloudProvider cloudProvider = new CloudSimRunner();

    private final Model model;
    private final State state;

    private final Validator validator;
    private final FitnessFunction fitnessFunction;

    private final long MAX_RUNTIME = 300L;

    public CooperExecution(Model model) {
        this.model = model;
        this.state = new State(model);
        this.validator = new Validator(model);
        this.fitnessFunction = new FitnessFunction(model, validator);
    }


    public void run() {
        cloudProvider.registerListener(new SchedulingListener());
        cloudProvider.run();
    }

    private void tearDown() {
        log.info(" *** Total Genetic Fitness: {}", totalGeneticFitness);
        log.info(" *** Total Greedy Fitness: {}", totalGreedyFitness);
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
            var optimizationResult = optimize();
            var executionPlan = plan(optimizationResult);
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
        state.getIncomingServiceLoad().clear();
        state.getDownstreamServiceLoad().clear();
        state.getServiceLoad().clear();

        state.getIncomingServiceLoad().putAll(monitor.getCurrentServiceLoad());
        var overallIncomingLoad = state.getIncomingServiceLoad().values().stream()
                .reduce(0L, Long::sum);

        var affinityGraph = new SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class);
        model.getServices().values().forEach(s -> affinityGraph.addVertex(s.getName()));
        model.getServices().values().forEach(s1 -> {
            model.getServices().values().forEach(s2 -> {
                if (!s1.equals(s2)) {
                    affinityGraph.addEdge(s1.getName(), s2.getName(), new DefaultWeightedEdge());
                    var edge = affinityGraph.getEdge(s1.getName(), s2.getName());
                    affinityGraph.setEdgeWeight(edge, 0);
                }
            });
        });

        model.getDownstreamRequestMultiplier().forEach((s1, innerMap) -> {
            innerMap.forEach((s2, r) -> {
                var incomingLoad = state.getIncomingServiceLoad().get(s1);
                var downstreamLoad = ((Float) (incomingLoad * r)).longValue();

                var oldValue = state.getDownstreamServiceLoad().getOrDefault(s2, 0L);
                var newValue = oldValue + downstreamLoad;
                state.getDownstreamServiceLoad().put(s2, newValue);

                if (r > 0) {
                    var aff = (double) downstreamLoad / (double) overallIncomingLoad;
                    var edge = affinityGraph.getEdge(s1, s2);
                    var oldEdgeValue = affinityGraph.getEdgeWeight(edge);
                    var newEdgeValue = oldEdgeValue + aff;
                    affinityGraph.setEdgeWeight(edge, newEdgeValue);
                }
            });
        });

        var mergedLoad = state.getIncomingServiceLoad().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() + state.getDownstreamServiceLoad().get(e.getKey())));
        state.setServiceAffinity(affinityGraph);

        state.getServiceLoad().putAll(mergedLoad);

    }


    private float totalGreedyFitness = 0;
    private float totalGeneticFitness = 0;

    private OptimizationResult optimize() {
        var greedyResult = new GreedyOptimizer(model, state).optimize();
        var greedyFitness = fitnessFunction.eval(greedyResult.getAllocation(), state);
        totalGreedyFitness += greedyFitness;

        var geneticOptimizer = new GeneticAlgorithm(Collections.emptyList(), model, state, validator);
        var geneticResult = geneticOptimizer.run();
        var geneticFitness = fitnessFunction.eval(geneticResult.getAllocation(), state);
        totalGeneticFitness += geneticFitness;

        var result = geneticResult;
        if (!validator.isAllocationValid(result.getAllocation(), state.getServiceLoad())) {
            throw new IllegalStateException("Invalid allocation!");
        }
        return result;
    }

    private ExecutionItems plan(OptimizationResult opt) {
        List<String> vmLaunchList = new ArrayList<>();
        List<String> vmKillList = new ArrayList<>();

        model.getVms().forEach((vmId, vm) -> {
            var isRunning = state.getLeasedProviderVms().containsKey(vmId);
            var shouldRun = opt.getAllocation().containsKey(vm);

            if (!isRunning && shouldRun) {
                vmLaunchList.add(vmId);
            } else if (isRunning && !shouldRun) {
                vmKillList.add(vmId);
            }
        });

//        opt.getVmAllocation().forEach((vmId, shouldRun) -> {
//            var isRunning = state.getLeasedProviderVms().containsKey(vmId);
//
//            if (!isRunning && shouldRun) {
//                vmLaunchList.add(vmId);
//            } else if (isRunning && !shouldRun) {
//                vmKillList.add(vmId);
//            }
//        });

        List<Pair<String, ContainerType>> containerLaunchList = new ArrayList<>();
        List<Pair<String, ContainerType>> containerKillList = new ArrayList<>();

        opt.getTuples().stream()
                .forEach(a -> {
                    var allocate = a.isAllocate();
                    var containersRunningOnVm = state.getRunningContainersByVm(a.getVm().getId());
                    var isContainerTypeRunningOnVm = containersRunningOnVm.stream().anyMatch(c -> c.getConfiguration().equals(a.getType()));
                    if (allocate && !isContainerTypeRunningOnVm) {
                        containerLaunchList.add(Pair.of(a.getVm().getId(), a.getType()));
                    } else if (!allocate && isContainerTypeRunningOnVm) {
                        containerKillList.add(Pair.of(a.getVm().getId(), a.getType()));
                    } else {
                        // nothing to do
                    }
                });

        return new ExecutionItems(vmLaunchList, vmKillList, containerLaunchList, containerKillList);
    }

    private void execute(Scheduler scheduler, ExecutionItems items) {
        items.getVmsToLaunch().forEach(vmId -> {
            var vm = model.getVms().get(vmId);
            var providerId = scheduler.launchVm(vm.getType().getLabel(), "DC-1");
            state.getLeasedProviderVms().put(vmId, providerId);
        });
        items.getContainersToStart().forEach(a -> {
            var vmId = a.getKey();
            var containerType = a.getValue();
            var providerVmId = state.getLeasedProviderVms().get(vmId);
            if (providerVmId == null) {
                log.info("null");
            }

            var providerId = scheduler.launchContainer(1, containerType.getMemory().toMegabytes(), providerVmId);
            state.allocateContainerInstance(vmId, containerType, providerId);
        });
        items.getContainersToStop().forEach(a -> {
            var vmId = a.getKey();
            var containerType = a.getValue();
            var runningContainers = state.getRunningContainersByVm(vmId);
            var container = runningContainers.stream()
                    .filter(c -> c.getConfiguration().equals(containerType))
                    .findAny()
                    .orElseThrow();

            var providerContainerId = state.getRunningProviderContainers().get(container);
            scheduler.terminateContainer(providerContainerId);
            state.deallocateContainerInstance(container);
        });
        // TODO delay termination of vms/containers?
        items.getVmsToTerminate().forEach(vmId -> {
            var vm = model.getVms().get(vmId);
            var providerId = state.getLeasedProviderVms().get(vmId);
            scheduler.terminateVm(providerId);
            state.releaseVm(vm.getId());
        });

    }

    private void printServiceLoad() {
        System.out.println();
        System.out.println("Service Load:");
        System.out.println();
        var serviceCapacity = state.getServiceCapacity();
        var table = new ConsoleTable("Service", "Load", "Capacity");
        state.getServiceLoad().forEach((key, value) -> table.addRow(key, value, serviceCapacity.get(key)));
        table.print();
    }

    private void printAllocationStatus() {
        System.out.println();
        System.out.println("VM Allocation:");
        System.out.println();
        var table = new ConsoleTable("ID", "Type", "Free CPU", "Free Memory", "Containers");
        state.getLeasedVms().forEach(vm -> {
            var containerList = state.getRunningContainersByVm().get(vm.getId());
            var allocatedContainerTypes = containerList != null ? containerList.stream().map(c -> c.getService().getName() + ":" + c.getConfiguration().getLabel()).collect(Collectors.joining(", ")) : "-";
            var capacity = state.getFreeCapacity(vm.getId());
            table.addRow(vm.getId(), vm.getType().getLabel(), capacity.getLeft(), capacity.getRight(), allocatedContainerTypes);
        });
        table.print();
    }

}
