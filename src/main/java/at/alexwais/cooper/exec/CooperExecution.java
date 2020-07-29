package at.alexwais.cooper.exec;

import at.alexwais.cooper.ConsoleTable;
import at.alexwais.cooper.cloudsim.CloudSimRunner;
import at.alexwais.cooper.csp.CloudProvider;
import at.alexwais.cooper.csp.Listener;
import at.alexwais.cooper.csp.Scheduler;
import at.alexwais.cooper.domain.DataCenter;
import at.alexwais.cooper.domain.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

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

    private OptimizationResult optimize() {
        var optimizer = new GreedyOptimizer(model, state);
        return optimizer.optimize();
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

        Map<String, String> containerLaunchMap = new HashMap<>();
        List<String> containerKillList = new ArrayList<>();
        opt.getContainerAllocation().forEach((containerId, vmId) -> {
            var runningOnVm = state.getContainerVmAllocation().get(containerId);
            var isRunning = runningOnVm != null;
            var runsOnRequiredVm = isRunning && vmId.equals(runningOnVm);
            if (!isRunning || !runsOnRequiredVm) {
                containerLaunchMap.put(containerId, vmId);
            }
        });

        state.getContainerVmAllocation().forEach((containerId, vmId) -> {
            var allocatedVm = opt.getContainerAllocation().get(containerId);
            var shouldRun = allocatedVm != null;
            var runsOnRequiredVm = shouldRun && vmId.equals(allocatedVm);
            if (!shouldRun || !runsOnRequiredVm) {
                containerKillList.add(containerId);
            }
        });

        return new ExecutionItems(vmLaunchList, vmKillList, containerLaunchMap, containerKillList);
    }

    private void execute(Scheduler scheduler, ExecutionItems items) {
        items.getVmsToLaunch().forEach(vmId -> {
            var vm = model.getVms().get(vmId);
            var providerId = scheduler.launchVm(vm.getType().getLabel(), "DC-1");
			state.getLeasedProviderVms().put(vmId, providerId);
        });
        items.getVmsToTerminate().forEach(vmId -> {
            var vm = model.getVms().get(vmId);
            var providerId = state.getLeasedProviderVms().get(vmId);
            scheduler.terminateVm(providerId);
			state.getLeasedProviderVms().remove(vmId);
        });

        items.getContainersToStart().forEach((containerId, vmId) -> {
            var container = model.getContainers().get(containerId);
            var providerVmId = state.getLeasedProviderVms().get(vmId);
            var providerId = scheduler.launchContainer(1, container.getConfiguration().getMemory().toMegabytes(), providerVmId);

            state.getRunningProviderContainers().put(containerId, providerId);
            state.allocateContainer(containerId, vmId);
        });
        items.getContainersToStop().forEach(containerId -> {
            var container = model.getContainers().get(containerId);
            var providerContainerId = state.getRunningProviderContainers().get(containerId);
            scheduler.terminateContainer(providerContainerId);

            state.getRunningProviderContainers().remove(containerId);
            state.deallocateContainer(containerId);
        });
    }


    private void printAllocationStatus() {
        System.out.println();
        System.out.println("VM Allocation:");
        System.out.println();
        var table = new ConsoleTable("ID", "Type", "Free CPU", "Free Memory", "# Containers");
        state.getLeasedVms().forEach(vm -> {
            var containerList = state.getVmContainerAllocation().get(vm.getId());
            var numberOfAllocatedContainers = containerList != null ? containerList.size() : 0;
            var capacity = state.getFreeCapacity(vm.getId());
            table.addRow(vm.getId(), vm.getType().getLabel(), capacity.getLeft(), capacity.getRight(), numberOfAllocatedContainers);
        });
        table.print();
    }

}
