package at.alexwais.cooper.csp;

public interface Scheduler {

    long launchVm(String type, String datacenter);

    void terminateVm(long id);

    long launchContainer(int cpuCores, long memory, long vmId);

    void terminateContainer(long id);

    void abort();

}
