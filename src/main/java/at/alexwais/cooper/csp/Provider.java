package at.alexwais.cooper.csp;

public interface Provider {

    void registerSchedulingListener(Listener listener);

    void run();

}
