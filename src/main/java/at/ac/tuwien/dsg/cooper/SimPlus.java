package at.ac.tuwien.dsg.cooper;

import java.util.ArrayList;
import java.util.List;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;

public class SimPlus {


    public static void run() {
        //Enables just some level of logging.
//Make sure to import org.cloudsimplus.util.Log;
//Log.setLevel(ch.qos.logback.classic.Level.WARN);

//Creates a CloudSim object to initialize the simulation.
        CloudSim cloudsim = new CloudSim();

        /*Creates a Broker that will act on behalf of a cloud user (customer).*/
        DatacenterBroker broker0 = new DatacenterBrokerSimple(cloudsim);

//Creates a list of Hosts, each host with a specific list of CPU cores (PEs).
        List<Host> hostList = new ArrayList<>();
        List<Pe> hostPes = new ArrayList<>();
//Uses a PeProvisionerSimple by default to provision PEs for VMs
        hostPes.add(new PeSimple(1024));
        hostPes.add(new PeSimple(1024));
        long ram = 16000; //in Megabytes
        long storage = 100000; //in Megabytes
        long bw = 100000; //in Megabits/s

//Uses ResourceProvisionerSimple by default for RAM and BW provisioning
//Uses VmSchedulerSpaceShared by default for VM scheduling
        Host host0 = new HostSimple(ram, bw, storage, hostPes);
        hostList.add(host0);

//Creates a Datacenter with a list of Hosts.
//Uses a VmAllocationPolicySimple by default to allocate VMs
        Datacenter dc0 = new DatacenterSimple(cloudsim, hostList);

        //Creates VMs to run applications.
        List<Vm> vmList = new ArrayList<>(1);
        //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
        Vm vm0 = new VmSimple(1024, 1);
        vm0.setRam(4000).setBw(1000).setSize(1000);
        Vm vm1 = new VmSimple(1024, 1);
        vm1.setRam(4000).setBw(1000).setSize(1000);
        vmList.add(vm0);
//        vmList.add(vm1);

//Creates Cloudlets that represent applications to be run inside a VM.
        List<Cloudlet> cloudletList = new ArrayList<>();
//UtilizationModel defining the Cloudlets use only 50% of any resource all the time
//        UtilizationModelDynamic utilizationModel = new UtilizationModelDynamic(UtilizationModel.Unit.ABSOLUTE);
        Cloudlet cloudlet0 = new CloudletSimple(10000, 1);
        cloudlet0.setUtilizationModelCpu(new UtilizationModelDynamic(UtilizationModel.Unit.ABSOLUTE, 512));
        cloudlet0.setUtilizationModelRam(new UtilizationModelDynamic(UtilizationModel.Unit.ABSOLUTE, 2048));
        Cloudlet cloudlet1 = new CloudletSimple(10000, 2);
        cloudlet1.setUtilizationModelCpu(new UtilizationModelDynamic(UtilizationModel.Unit.ABSOLUTE, 1000));
        cloudlet1.setUtilizationModelRam(new UtilizationModelDynamic(UtilizationModel.Unit.ABSOLUTE, 1000));
        cloudletList.add(cloudlet0);
        cloudletList.add(cloudlet1);

        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

/*Starts the simulation and waits all cloudlets to be executed, automatically
stopping when there is no more events to process.*/
        cloudsim.startSync();

        double clock = 0d;


        cloudsim.runFor(5d);
        printVmCpuUtilization(cloudsim, vmList);
//        vmList.add(vm1);
//        broker0.submitCloudlet(new CloudletSimple(1234, 1));

//        cloudsim.terminateAt(30d);

        while(cloudsim.isRunning()){
            cloudsim.runFor(10d);
            if (clock != cloudsim.clock()) {
                printVmCpuUtilization(cloudsim, vmList);
                clock = cloudsim.clock();
            }
            if (clock > 30d) {
                cloudlet1.getVm().getCloudletScheduler().cloudletCancel(cloudlet1);
                cloudsim.terminate();
            }
        }
//        cloudsim.pause();
//
//        broker0.submitCloudlet(new CloudletSimple(1234, 1, utilizationModel));
//
//        cloudsim.resume();
//
//        cloudsim.runFor(500000000);

        //cloudsim.terminate();

/*Prints results when the simulation is over
(you can use your own code here to print what you want from this cloudlet list).*/
        new CloudletsTableBuilder(broker0.getCloudletSubmittedList()).build();
    }


    private static void printVmCpuUtilization(CloudSim simulation, List<Vm> vmList) {
        /*To avoid printing to much data, just prints if the simulation clock
         * has changed, it's multiple of the interval to increase clock
         * and there is some VM already running. */

        System.out.printf("\t\tVM utilization for Time %.0f:%n", simulation.clock());
        for (final Vm vm : vmList) {
            System.out.printf(" Vm %5d |", vm.getId());
        }

        for (final Vm vm : vmList) {
            System.out.printf(" %7.0f%% |", vm.getCpuPercentUtilization()*100);
        }
        for (final Vm vm : vmList) {
            System.out.printf(" %7dm |", vm.getCurrentRequestedRam());
        }
        for (final Vm vm : vmList) {
            System.out.printf(" %7d |", vm.getCloudletScheduler().getCloudletList().size());
        }

        System.out.printf("%n%n");
    }

}
