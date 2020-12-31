package at.alexwais.cooper.simulated;

import at.alexwais.cooper.api.CloudController;
import at.alexwais.cooper.csp.Cloud;
import at.alexwais.cooper.csp.Listener;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class SimulatedCloud implements Cloud {

    private final List<Listener> listeners = new ArrayList<>();
    private final CloudController cloudController = new StubCloudController();
    private boolean terminationRequested = false;
    private long clock = 0L;

    private static final double CYCLE_INTERVAL = 30;
    private static final double TERMINATE_TIMEOUT = 43200; // 60*60*12 = 12h


    @Override
    public void registerListener(Listener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void run() {
        while (!terminationRequested && clock < TERMINATE_TIMEOUT) {
            tick();
        }

        if (!terminationRequested && clock < TERMINATE_TIMEOUT) {
            log.warn("UNWANTED TERMINATION BEFORE TIMEOUT");
        }
    }

    private void tick() {
        listeners.forEach(l -> {
                l.cycleElapsed(clock, cloudController);
        });

        clock += CYCLE_INTERVAL;
    }


    private class StubCloudController implements CloudController {
        private long vmIdSequence = 0L;
        private long containerIdSequence = 0L;
        private List<Long> vmList = new ArrayList<>();
        private List<Long> containerList = new ArrayList<>();

        @Override
        public long launchVm(String type, String dataCenter) {
            return vmIdSequence++;
        }

        @Override
        public void terminateVm(long id) {
            vmList.remove(id);
        }

        @Override
        public long launchContainer(int cpuCores, long memory, long vmId) {
            return containerIdSequence++;
        }

        @Override
        public void terminateContainer(long id) {
            containerList.remove(id);
        }

        @Override
        public void abort() {
            terminationRequested = true;
        }
    }

}
