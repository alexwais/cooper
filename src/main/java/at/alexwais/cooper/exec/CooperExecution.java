package at.alexwais.cooper.exec;

import at.alexwais.cooper.cloudsim.CloudSimRunner;
import at.alexwais.cooper.csp.Listener;
import at.alexwais.cooper.csp.Provider;
import at.alexwais.cooper.csp.Scheduler;
import at.alexwais.cooper.domain.Instance;
import at.alexwais.cooper.domain.InstanceType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CooperExecution {

	private Provider provider = new CloudSimRunner();
	private Optimizer optimizer = new Optimizer();

	private Map<String, Instance> vms = new HashMap<>();
	private Map<String, Long> providerVms = new HashMap<>();

	private InstanceType type = new InstanceType("2.medium", 2, 2048, 42f);


	public void run() {
		for (int i = 0; i < 1000; i++) {
			var vm = new Instance("VM-" + i, type);
			vms.put(vm.getId(), vm);
		}

		provider.registerListener(new SchedulingListener());
		provider.run();
	}


	class SchedulingListener implements Listener {
		@Override
		public void cycleElapsed(long clock, Scheduler scheduler) {
			log.info("\n\n########### Cooper Scheduling Cycle ########### {} ", clock);

//        if (clock == 100L) scheduler.launchVm("1.micro", "DC-1");
			if (clock > 300L) scheduler.abort();

			execute(scheduler, plan(optimize()));
		}

		@Override
		public int getClockInterval() {
			return 30;
		}
	}

	private OptimizationResult optimize() {
		var runningVms = vms.keySet().stream()
				.collect(Collectors.toMap(Function.identity(), vmId -> providerVms.containsKey(vmId)));
		var input = new OptimizationInput(runningVms);

		return optimizer.optimize(input);
	}

	private ExecutionItems plan(OptimizationResult opt) {
		List<String> launchList = new ArrayList<>();
		List<String> killList = new ArrayList<>();

		opt.getVmAllocation().forEach((vmId, shouldRun) -> {
			var providerId = providerVms.get(vmId);
			var isRunning = providerId != null;

			if (!isRunning && shouldRun) {
				launchList.add(vmId);
			} else if (isRunning && !shouldRun) {
				killList.add(vmId);
			}
		});

		return new ExecutionItems(launchList, killList);
	}

	private void execute(Scheduler scheduler, ExecutionItems items) {
		items.getVmsToLaunch().forEach(vmId -> {
			var vm = vms.get(vmId);
			var providerId = scheduler.launchVm(vm.getType().getLabel(), "DC-1");
			providerVms.put(vmId, providerId);
		});

		items.getVmsToTerminate().forEach(vmId -> {
			var vm = vms.get(vmId);
			var providerId = providerVms.get(vmId);
			scheduler.terminateVm(providerId);
			providerVms.remove(vmId);
		});
	}

}
