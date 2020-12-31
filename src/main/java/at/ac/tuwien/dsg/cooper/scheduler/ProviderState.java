package at.ac.tuwien.dsg.cooper.scheduler;

import static at.ac.tuwien.dsg.cooper.scheduler.MapUtils.putToMapList;
import static at.ac.tuwien.dsg.cooper.scheduler.MapUtils.removeFromMapList;

import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

public class ProviderState {

    private final Map<VmInstance, List<Allocation.AllocationTuple>> runningContainersByVm = new HashMap<>();

    @Getter
    private final Map<VmInstance, Long> leasedProviderVms = new HashMap<>();
    @Getter
    private final Map<Allocation.AllocationTuple, Long> runningProviderContainers = new HashMap<>();


    public List<Allocation.AllocationTuple> getRunningContainersByVm(VmInstance vm) {
        return this.runningContainersByVm.getOrDefault(vm, Collections.emptyList());
    }

    public void allocateContainerInstance(Allocation.AllocationTuple containerInstance, long providerId) {
        var isDuplicate = getRunningContainersByVm(containerInstance.getVm()).contains(containerInstance);

        if (isDuplicate)
            throw new IllegalStateException("Container of type " + containerInstance.getContainer().getLabel() + " already allocated on VM " + containerInstance.getVm());

        putToMapList(runningContainersByVm, containerInstance.getVm(), containerInstance);
        runningProviderContainers.put(containerInstance, providerId);
    }

    public void deallocateContainerInstance(Allocation.AllocationTuple containerInstance) {
        removeFromMapList(runningContainersByVm, containerInstance.getVm(), containerInstance);
        runningProviderContainers.remove(containerInstance);
    }

    public void releaseVm(VmInstance vm) {
        leasedProviderVms.remove(vm);
    }

}
