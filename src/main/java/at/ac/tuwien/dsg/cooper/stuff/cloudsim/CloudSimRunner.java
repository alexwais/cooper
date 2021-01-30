package at.ac.tuwien.dsg.cooper.stuff.cloudsim;

import at.ac.tuwien.dsg.cooper.api.CloudController;
import at.ac.tuwien.dsg.cooper.csp.Cloud;
import at.ac.tuwien.dsg.cooper.csp.Listener;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Bandwidth;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.resources.Ram;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;

@Slf4j
public class CloudSimRunner implements Cloud {

    /**
     * Defines the time (in seconds) to run the simulation for.
     * The clock is increased in the interval defined here.
     */
    private static final double CLOCK_INTERVAL = 1d;
    private static final double CYCLE_INTERVAL = 10;
    private static final double TERMINATE_TIMEOUT = 43200; // 60*60*12 = 12h


    private VmTypeConfiguration vmTypeConfiguration;

    private final CloudSim simulation = new CloudSim();
    private DatacenterBroker broker;

    private Map<String, Datacenter> datacenters = new HashMap<>();
    private Map<Long, Vm> vmList = new HashMap<>();
    private Map<Long, Cloudlet> cloudletList = new HashMap<>();

    private Map<Long, VmType> vmTypes = new HashMap<>();

    private List<Listener> listeners = new ArrayList<>();


    private boolean terminationRequested = false;
    private double prevTick = -1d;
    private Map<Listener, Long> invokedClockTicks = new HashMap<>();
    private CloudController cloudController = new CloudSimCloudController();


    @Override
    public void run() {
        vmTypeConfiguration = new VmTypeConfiguration();

        // Enables just some level of logging.
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        // Creates a Broker that will act on behalf of a cloud user (customer).
        broker = new DatacenterBrokerSimple(simulation);
        broker.setSelectClosestDatacenter(false);

        // Creates a Datacenter with a list of Hosts.
        // Uses a VmAllocationPolicySimple by default to allocate VMs
        // dynamic datacenters?
        datacenters.put("DC-1", createDatacenter(1024, 1024));
        datacenters.put("DC-2", createDatacenter(1024, 1024));

        simulation.startSync();

        while (simulation.isRunning()) {
            // Need to emit a periodic event to keep the simulation alive even if no Cloudlets are running.
            simulation.send(broker, broker, CYCLE_INTERVAL, 42_000, "keep-alive");
            simulation.runFor(CLOCK_INTERVAL);

            tick();

            var timeoutReached = simulation.clock() >= TERMINATE_TIMEOUT;
            if (terminationRequested || timeoutReached) {
                cloudletList.values().forEach(c -> c.getVm().getCloudletScheduler().cloudletCancel(c));
                simulation.terminate();
                log.info("TERMINATED DUE TO REASON: {}", terminationRequested ? "terminationRequested" : "timeoutReached");
            }
        }

        if (!terminationRequested && simulation.clock() < TERMINATE_TIMEOUT) {
            log.warn("UNWANTED TERMINATION BEFORE TIMEOUT");
        }

        new CloudletsTableBuilder(new ArrayList<>(cloudletList.values())).build();
    }


    /**
     * Due to some reason, CloudSim appears to not update (Ram) usage properly.
     * I.e., resource usage is not updated in time, and resources of terminated cloudlets are not deallocated at all.
     */
    private void updateResourceUsage() {
        datacenters.values().stream().map(Datacenter::getHostList).flatMap(Collection::stream)
                .forEach(h -> h.updateProcessing(simulation.clock()));
        vmList.values().forEach(vm -> {
            if (vm.getCloudletScheduler().isEmpty()) {
                vm.getResource(Ram.class).deallocateAllResources();
                vm.getResource(Bandwidth.class).deallocateAllResources();
            }
        });
    }

    private Datacenter createDatacenter(long cores, long gbRam) {
        var hostPes = new ArrayList<Pe>();
        for (int i = 0; i < cores; i++) {
            hostPes.add(new PeSimple(1024));
        }
        long ram = gbRam * 1024; // in Megabytes
        long storage = 100000; // in Megabytes
        long bw = 100000; // in Megabits/s

        // Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        // Uses VmSchedulerSpaceShared by default for VM scheduling
        var host = new HostSimple(ram, bw, storage, hostPes);
        return new DatacenterSimple(simulation, List.of(host));
    }

    private void tick() {
        var currentClock = simulation.clock();
        if (currentClock == prevTick) return;

        prevTick = currentClock;

        updateResourceUsage();

        listeners.forEach(l -> {
            // ensure full cycle is completed:
            var clockTick = (long) Math.floor(currentClock);
            var matchesCycleInterval = clockTick % l.getClockInterval() == 0;

            // ensure listener is invoked exactly once per cycle:
            var notInvokedBefore = invokedClockTicks.getOrDefault(l, -1L) != clockTick;

            if (matchesCycleInterval && notInvokedBefore) {
                invokedClockTicks.put(l, clockTick);
                l.cycleElapsed(clockTick, cloudController);
            }
        });
    }

    private Vm launchVm(String type, String datacenter) {
        // dynamic vm types?
        var vmType = vmTypeConfiguration.getVmTypes().get(type);
        var vm = new VmSimple(1024, vmType.getCpu());
        vm.setRam(vmType.getRam()).setBw(1000).setSize(1000);

        var dc = datacenters.get(datacenter);
        broker.setDatacenterMapper((d, v) -> dc);

        broker.submitVm(vm);
        vmList.put(vm.getId(), vm);
        vmTypes.put(vm.getId(), vmType);
        log.info("Launched VM <{}> in datacenter {}", vm.getId(), datacenter);
        return vm;
    }

    private class CloudSimCloudController implements CloudController {
        @Override
        public long launchVm(String type, String datacenter) {
            return CloudSimRunner.this.launchVm(type, datacenter).getId();
        }

        @Override
        public void terminateVm(long id) {
            var vm = vmList.get(id);
            // kill cloudlets properly to cancel statu?
//            vm.getCloudletScheduler().getCloudletList()
//                    .forEach(c -> vm.getCloudletScheduler().cloudletCancel(c));

            var cloudlets = broker.destroyVm(vm);

            log.info("Terminated VM: {}", id);
            if (!cloudlets.isEmpty()) {
                log.warn("Terminated VM had {} cloudlets running!", cloudlets.size());
            }
        }

        @Override
        public long launchContainer(int cpuCores, long memory, long vmId) {
            var vm = vmList.get(vmId);
            if (vm == null) throw new IllegalArgumentException("Vm with id=" + vmId + "does not exist!");

            // validate available resources!?
            var cloudlet = new CloudletSimple(100000000, cpuCores);
            cloudlet.setUtilizationModelCpu(new UtilizationModelDynamic(UtilizationModel.Unit.ABSOLUTE, 1024));
            cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(UtilizationModel.Unit.ABSOLUTE, memory));

            cloudlet.setVm(vm);
            broker.submitCloudlet(cloudlet);
            cloudletList.put(cloudlet.getId(), cloudlet);
            log.info("Launched Cloudlet <{}> on VM {}", cloudlet.getId(), vmId);
            return cloudlet.getId();
        }

        @Override
        public void terminateContainer(long id) {
            var cloudlet = cloudletList.get(id);
            cloudlet.getVm().getCloudletScheduler().cloudletCancel(cloudlet);
            log.info("Terminated Cloudlet: {}", id);
        }

        @Override
        public void abort() {
            terminationRequested = true;
        }
    }


    public void printVmList() {
        System.out.printf("\t\tVM utilization for Time %.0f:%n", simulation.clock());
        for (final Vm vm : broker.getVmExecList()) {
            System.out.printf(" Vm %4d |", vm.getId());
            System.out.printf(" %9s |", vmTypes.get(vm.getId()).getName());
//            System.out.printf(" Stoptime: %5.0f |", vm.getStopTime());
            System.out.printf(" %3.0f%% |", vm.getCpuPercentUtilization()*100);
            System.out.printf(" %5d / %5d (%3.0f%%) |", vm.getRam().getAllocatedResource(), vm.getRam().getCapacity(), vm.getRam().getPercentUtilization() * 100);
            System.out.printf(" %4d |", vm.getCloudletScheduler().getCloudletList().size());
            System.out.printf("%n");
        }
    }

    public double getClock() {
        return simulation.clock();
    }


    @Override
    public void registerListener(Listener listener) {
        this.listeners.add(listener);
    }

}
