package at.alexwais.cooper.scheduler.mapek;

import at.alexwais.cooper.csp.Scheduler;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.State;
import at.alexwais.cooper.scheduler.dto.ExecutionPlan;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Executor {

    private final Model model;

    public Executor(Model model) {
        this.model = model;
    }


    public void execute(Scheduler scheduler, ExecutionPlan plan, State state) {
        plan.getVmsToLaunch().forEach(vmId -> {
            var vm = model.getVms().get(vmId);
            var providerId = scheduler.launchVm(vm.getType().getLabel(), "DC-1");
            state.getLeasedProviderVms().put(vmId, providerId);
        });
        plan.getContainersToStart().forEach(a -> {
            var vmId = a.getKey();
            var containerType = a.getValue();
            var providerVmId = state.getLeasedProviderVms().get(vmId);
            if (providerVmId == null) {
                log.info("null");
            }

            var providerId = scheduler.launchContainer(1, containerType.getMemory().toMegabytes(), providerVmId);
            state.allocateContainerInstance(vmId, containerType, providerId);
        });
        plan.getContainersToStop().forEach(a -> {
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
        plan.getVmsToTerminate().forEach(vmId -> {
            var vm = model.getVms().get(vmId);
            var providerId = state.getLeasedProviderVms().get(vmId);
            scheduler.terminateVm(providerId);
            state.releaseVm(vm.getId());
        });

    }

}
