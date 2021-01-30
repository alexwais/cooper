package at.ac.tuwien.dsg.cooper.ilp;

import at.ac.tuwien.dsg.cooper.domain.ContainerType;
import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.cplex.IloCplex;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Variables {

    private final Map<String, IloIntVar> decisionVariables = new HashMap<>();
    // helper variables:
    private final Map<String, IloIntVar> vmAllocationVariables = new HashMap<>();
    private final Map<String, IloIntVar> vmGracePeriodVariables = new HashMap<>();
    private final Map<String, ConcurrentAllocationVariable> concurrentAllocationVariables = new HashMap<>();
    private final Map<String, IloIntVar> cpuRequirementVariables = new HashMap<>();
    private final Map<String, IloIntVar> memRequirementVariables = new HashMap<>();


    enum ResourceType {
        CPU,
        MEMORY
    }

    @Getter
    @AllArgsConstructor
    class ConcurrentAllocationVariable {
        private ContainerType c1;
        private VmInstance k1;
        private ContainerType c2;
        private VmInstance k2;
        private IloIntVar decisionVariable;
    }


    public Variables(Model model, IloCplex cplex, boolean colocation) {
        try {
            buildVariables(model, cplex, colocation);
        } catch (IloException e) {
            throw new RuntimeException(e);
        }
    }


    public IloIntVar getDecisionVariable(ContainerType c, VmInstance k) {
        var identifier = decisionVariableIdentifier(c, k);
        return decisionVariables.get(identifier);
    }

    public Collection<ConcurrentAllocationVariable> getConcurrentAllocationVariables() {
        return concurrentAllocationVariables.values();
    }

    public IloIntVar getResourceVariable(Service s, VmInstance k, ResourceType type) {
        if (type == ResourceType.CPU) {
            var identifier = cpuResourceVariableIdentifier(s, k);
            return cpuRequirementVariables.get(identifier);
        } else {
            var identifier = memResourceVariableIdentifier(s, k);
            return memRequirementVariables.get(identifier);
        }
    }

    public IloIntVar getVmAllocationVariable(VmInstance k) {
        return vmAllocationVariables.get("y_" + k.getId());
    }

    public IloIntVar getVmGracePeriodVariable(VmInstance k) {
        return vmGracePeriodVariables.get("g_" + k.getId());
    }


    private void buildVariables(Model model, IloCplex cplex, boolean enableColocation) throws IloException {
        for (var vm : model.getVms().values()) {
            for (var containerType : model.getContainerTypes()) {
                var identifier = decisionVariableIdentifier(containerType, vm);
                var decisionVariable = cplex.boolVar(identifier);
                decisionVariables.put(identifier, decisionVariable);
            }
        }

        for (var vm : model.getVms().values()) {
            var vmAllocationVariable = cplex.boolVar("y_" + vm.getId());
            vmAllocationVariables.put("y_" + vm.getId(), vmAllocationVariable);
            var vmGracePeriodVariable = cplex.boolVar("g_" + vm.getId());
            vmGracePeriodVariables.put("g_" + vm.getId(), vmGracePeriodVariable);
        }

        if (enableColocation) {
            for (var s1 : model.getServices().values()) {
                for (var s2 : model.getServices().values()) {
                    if (s1 == s2) continue;
                    for (var c1 : s1.getContainerTypes()) {
                        for (var c2 : s2.getContainerTypes()) {
                            for (var k1 : model.getVms().values()) {
                                for (var k2 : model.getVms().values()) {
                                    if (k1 == k2) continue; // assume distance on same VM is always 0

                                    // skip equivalents
                                    var symmetricIdentifier = concurrentAllocationVariableIdentifier(c2, k2, c1, k1);
                                    if (concurrentAllocationVariables.containsKey(symmetricIdentifier)) {
                                        continue;
                                    }

                                    var identifier = concurrentAllocationVariableIdentifier(c1, k1, c2, k2);
                                    var decisionVariable = cplex.boolVar(identifier);
                                    var wrapper = new ConcurrentAllocationVariable(c1, k1, c2, k2, decisionVariable);
                                    concurrentAllocationVariables.put(identifier, wrapper);
                                }
                            }
                        }
                    }
                }
            }
        }

        log.info("coloc variables: {}", concurrentAllocationVariables.size());

        for (var service : model.getServices().values()) {
            var maxCpuRequirementOfServiceContainers = service.getContainerTypes().stream()
                    .mapToInt(ContainerType::getCpuShares)
                    .max().getAsInt();
            var maxMemRequirementOfServiceContainers = service.getContainerTypes().stream()
                    .mapToInt(ContainerType::getMemory)
                    .max().getAsInt();

            for (var vm : model.getVms().values()) {
                var cpuIdentifier = cpuResourceVariableIdentifier(service, vm);
                var cpuRequirementVariable = cplex.intVar(0, maxCpuRequirementOfServiceContainers, cpuIdentifier);
                cpuRequirementVariables.put(cpuIdentifier, cpuRequirementVariable);

                var memIdentifier = memResourceVariableIdentifier(service, vm);
                var memRequirementVariable = cplex.intVar(0, maxMemRequirementOfServiceContainers, memIdentifier);
                memRequirementVariables.put(memIdentifier, memRequirementVariable);
            }
        }
    }


    private String decisionVariableIdentifier(ContainerType c, VmInstance k) {
        return "x_" + c.getLabel() + "," + k.getId();
    }

    private String concurrentAllocationVariableIdentifier(ContainerType c1, VmInstance k1, ContainerType c2, VmInstance k2) {
        return "ca_" + c1.getLabel() + "," + k1.getId() + "," + c2.getLabel() + "," + k2.getId();
    }

    private String cpuResourceVariableIdentifier(Service s, VmInstance k) {
        return "mu_cpu_" + s.getName() + "," + k.getId();
    }

    private String memResourceVariableIdentifier(Service s, VmInstance k) {
        return "mu_mem_" + s.getName() + "," + k.getId();
    }

}
