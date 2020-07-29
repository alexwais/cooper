package at.alexwais.cooper.csp;

public interface CloudProvider {

    void registerListener(Listener listener);

    void run();

}
