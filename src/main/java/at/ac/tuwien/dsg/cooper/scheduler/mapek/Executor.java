package at.ac.tuwien.dsg.cooper.scheduler.mapek;

import at.ac.tuwien.dsg.cooper.api.CloudController;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import at.ac.tuwien.dsg.cooper.scheduler.State;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Executor {

    private final Model model;

    public Executor(Model model) {
        this.model = model;
    }


    public void execute(CloudController cloudController, Allocation newTargetAllocation, State state) {
        var providerState = state.getProviderState();
        List<VmInstance> vmsToLaunch = new ArrayList<>();
        List<VmInstance> vmsToKill = new ArrayList<>();

        model.getVms().values().forEach(vm -> {
            var isRunning = state.getCurrentTargetAllocation().getRunningVms().contains(vm);
            var shouldRun = newTargetAllocation.getRunningVms().contains(vm);

            if (!isRunning && shouldRun) {
                vmsToLaunch.add(vm);
            } else if (isRunning && !shouldRun) {
                vmsToKill.add(vm);
            }
        });

        List<Allocation.AllocationTuple> containersToStart = new ArrayList<>();
        List<Allocation.AllocationTuple> containersToStop = new ArrayList<>();

        newTargetAllocation.getTuples()
                .forEach(a -> {
                    var allocate = a.isAllocate();
                    var containersRunningOnVm = state.getCurrentTargetAllocation().getAllocatedContainersOnVm(a.getVm());
                    var isAlreadyRunningOnVm = containersRunningOnVm.stream().anyMatch(a2 -> a2.isSameAllocation(a));

                    if (allocate && !isAlreadyRunningOnVm) {
                        containersToStart.add(a);
                    } else if (!allocate && isAlreadyRunningOnVm) {
                        containersToStop.add(a);
                    } else {
                        // nothing to do
                    }
                });

        vmsToLaunch.forEach(vm -> {
            var providerId = cloudController.launchVm(vm.getType().getLabel(), "DC-1");
            providerState.getLeasedProviderVms().put(vm, providerId);
        });

        containersToStop.forEach(a -> {
            var containersRunningOnVm = state.getCurrentTargetAllocation().getAllocatedContainersOnVm(a.getVm());
            var runningContainer = containersRunningOnVm.stream()
                    .filter(a2 -> a2.isSameAllocation(a))
                    .findAny()
                    .orElseThrow();

            var providerContainerId = providerState.getRunningProviderContainers().get(runningContainer);
            cloudController.terminateContainer(providerContainerId);
            providerState.deallocateContainerInstance(runningContainer);
        });

        containersToStart.forEach(a -> {
            var providerVmId = providerState.getLeasedProviderVms().get(a.getVm());
            var providerId = cloudController.launchContainer(1, a.getContainer().getMemory(), providerVmId);
            providerState.allocateContainerInstance(a, providerId);
            state.updateCacheState(a.getVm(), a.getContainer().getService());
        });

        vmsToKill.forEach(vm -> {
            var providerId = providerState.getLeasedProviderVms().get(vm);
            state.resetCacheState(vm);
            cloudController.terminateVm(providerId);
            providerState.releaseVm(vm);
        });
    }

}
