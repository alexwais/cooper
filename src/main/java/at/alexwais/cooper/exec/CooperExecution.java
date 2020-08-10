package at.alexwais.cooper.exec;

import at.alexwais.cooper.ConsoleTable;
import at.alexwais.cooper.cloudsim.CloudSimRunner;
import at.alexwais.cooper.csp.CloudProvider;
import at.alexwais.cooper.csp.Listener;
import at.alexwais.cooper.csp.Scheduler;
import at.alexwais.cooper.domain.ContainerConfiguration;
import at.alexwais.cooper.domain.DataCenter;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.genetic.GeneticAlgorithm;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class CooperExecution {

    private final FakeMonitor monitor = new FakeMonitor();
    private final CloudProvider cloudProvider = new CloudSimRunner();

    private final Model model;
    private final State state;

    public CooperExecution(List<DataCenter> dataCenters, List<Service> services) {
        this.model = new Model(dataCenters, services);
        this.state = new State(model);
    }


    public void run() {
        cloudProvider.registerListener(new SchedulingListener());
        cloudProvider.run();
    }

    class SchedulingListener implements Listener {
        @Override
        public void cycleElapsed(long clock, Scheduler scheduler) {
            log.info("\n\n########### Cooper Scheduling Cycle ########### {} ", clock);

//        if (clock == 100L) scheduler.launchVm("1.micro", "DC-1");
            if (clock > 600L) scheduler.abort();

            // MAPE-K (Monitor - Analyze(Optimize) - Plan - Execute)
            monitor();
            var optimizationResult = optimize();
            var executionPlan = plan(optimizationResult);
            execute(scheduler, executionPlan);

            printServiceLoad();
            printAllocationStatus();
        }

        @Override
        public int getClockInterval() {
            return 30;
        }
    }

    private void monitor() {
        state.getServiceLoad().putAll(monitor.getCurrentServiceLoad());
    }


    private List<OptimizationResult> historicOptResults = new ArrayList<>();

    private OptimizationResult optimize() {
//        return new GreedyOptimizer(model, state).optimize();


        var greedyResult = new GreedyOptimizer(model, state).optimize();
        historicOptResults.add(greedyResult);

        var optimizer = new GeneticAlgorithm(historicOptResults, model, state);
        var result = optimizer.run();
        historicOptResults.add(result);
        return result;
    }

    private ExecutionItems plan(OptimizationResult opt) {
        List<String> vmLaunchList = new ArrayList<>();
        List<String> vmKillList = new ArrayList<>();

        opt.getVmAllocation().forEach((vmId, shouldRun) -> {
            var isRunning = state.getLeasedProviderVms().containsKey(vmId);

            if (!isRunning && shouldRun) {
                vmLaunchList.add(vmId);
            } else if (isRunning && !shouldRun) {
                vmKillList.add(vmId);
            }
        });

        List<Pair<String, ContainerConfiguration>> containerLaunchList = new ArrayList<>();
        List<Pair<String, ContainerConfiguration>> containerKillList = new ArrayList<>();

        opt.getContainerAllocation().stream()
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
            var allocatedContainerTypes = containerList != null ? containerList.stream().map(c -> c.getConfiguration().getLabel()).collect(Collectors.joining(", ")) : "-";
            var capacity = state.getFreeCapacity(vm.getId());
            table.addRow(vm.getId(), vm.getType().getLabel(), capacity.getLeft(), capacity.getRight(), allocatedContainerTypes);
        });
        table.print();
    }

}
