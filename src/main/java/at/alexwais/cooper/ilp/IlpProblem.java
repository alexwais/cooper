package at.alexwais.cooper.ilp;

import at.alexwais.cooper.domain.Allocation;
import at.alexwais.cooper.domain.ContainerType;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.State;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.cplex.IloCplex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;


@Slf4j
public class IlpProblem {

    private final Model model;
    private final State state;
    private final IloCplex cplex;
    private final Map<String, IloIntVar> decisionVariables = new HashMap<>();
    private final Map<String, IloIntVar> vmAllocationVariables = new HashMap<>();
    private final Map<String, IloIntVar> vmGracePeriodVariables = new HashMap<>();
    private final Map<String, IloIntVar> colocationVariables = new HashMap<>();

    private final Map<String, IloIntVar> cpuRequirementVariables = new HashMap<>();
    private final Map<String, IloIntVar> memRequirementVariables = new HashMap<>();


    public IlpProblem(Model model, State state) {
        this.model = model;
        this.state = state;

        try {
            this.cplex = new IloCplex();
            buildVariables();
            buildTerms();
        } catch (IloException e) {
            throw new RuntimeException(e);
        }
    }

    public ArrayList<Allocation.AllocationTuple> solve(IloCplex.ParameterSet parameters) {
        try {
            cplex.setParameterSet(parameters);
            return runSolver();
        } catch (IloException e) {
            throw new RuntimeException(e);
        }
    }


    private ArrayList<Allocation.AllocationTuple> runSolver() throws IloException {
        var stopWatch = new StopWatch();
        stopWatch.start();
        cplex.solve();
        stopWatch.stop();

        var allocationTuples = new ArrayList<Allocation.AllocationTuple>();
        for (var k : model.getVms().values()) {
            for (var c : model.getContainerTypes()) {
                var decisionVariable = getDecisionVariable(c, k);
                var isAllocated = cplex.getValue(decisionVariable);
                if (isAllocated > 0) {
                    allocationTuples.add(new Allocation.AllocationTuple(k, c, true));
                }
            }
        }
        return allocationTuples;
    }


    private String decisionVariableIdentifier(ContainerType c, VmInstance k) {
        return "x_" + c.getLabel() + "," + k.getId();
    }

    private String colocationVariableIdentifier(ContainerType c1, VmInstance k1, ContainerType c2, VmInstance k2) {
        return "coloc_" + c1.getLabel() + "," + k1.getId() + "," + c2.getLabel() + "," + k2.getId();
    }

    private String cpuResourceVariableIdentifier(Service s, VmInstance k) {
        return "mu_cpu_" + s.getName() + "," + k.getId();
    }

    private String memResourceVariableIdentifier(Service s, VmInstance k) {
        return "mu_mem_" + s.getName() + "," + k.getId();
    }


    private IloIntVar getDecisionVariable(ContainerType c, VmInstance k) {
        var identifier = decisionVariableIdentifier(c, k);
        return decisionVariables.get(identifier);
    }

    private IloIntVar getColocationVariable(ContainerType c1, VmInstance k1, ContainerType c2, VmInstance k2) {
        var identifier = colocationVariableIdentifier(c1, k1, c2, k2);
        return colocationVariables.get(identifier);
    }

    private IloIntVar getResourceVariable(Service s, VmInstance k, ResourceType type) {
        if (type == ResourceType.CPU) {
            var identifier = cpuResourceVariableIdentifier(s, k);
            return cpuRequirementVariables.get(identifier);
        } else {
            var identifier = memResourceVariableIdentifier(s, k);
            return memRequirementVariables.get(identifier);
        }
    }

    enum ResourceType {
        CPU,
        MEMORY,
    }

    private IloIntVar getVmAllocationVariable(VmInstance k) {
        return vmAllocationVariables.get("y_" + k.getId());
    }

    private IloIntVar getVmGracePeriodVariable(VmInstance k) {
        return vmGracePeriodVariables.get("g_" + k.getId());
    }


    private void buildVariables() throws IloException {
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


        for (var service1 : model.getServices().values()) {
            for (var service2 : model.getServices().values()) {
                if (service1 == service2) continue;

                for (var containerType1 : service1.getContainerTypes()) {
                    for (var containerType2 : service2.getContainerTypes()) {
                        for (var vm1 : model.getVms().values()) {
                            for (var vm2 : model.getVms().values()) {
                                var identifier = colocationVariableIdentifier(containerType1, vm1, containerType2, vm2);
                                var colocationVariable = cplex.boolVar(identifier);
                                colocationVariables.put(identifier, colocationVariable);
                            }
                        }
                    }
                }

            }
        }


        for (var service : model.getServices().values()) {
            for (var vm : model.getVms().values()) {
                var cpuIdentifier = cpuResourceVariableIdentifier(service, vm);
                var cpuRequirementVariable = cplex.intVar(0, 100_000, cpuIdentifier); // TODO use possible maximum from vm types
                cpuRequirementVariables.put(cpuIdentifier, cpuRequirementVariable);

                var memIdentifier = memResourceVariableIdentifier(service, vm);
                var memRequirementVariable = cplex.intVar(0, 100_000, memIdentifier);
                memRequirementVariables.put(memIdentifier, memRequirementVariable);
            }
        }
    }

    private void buildTerms() throws IloException {

        // Objective function

        var objectiveTerm1 = cplex.linearNumExpr();
        var objectiveTerm2 = cplex.linearNumExpr();

        for (var k : model.getVms().values()) {
            var vmAllocationVariable = getVmAllocationVariable(k);
            objectiveTerm1.addTerm(k.getType().getCost(), vmAllocationVariable);

            var vmGracePeriodVariable = getVmGracePeriodVariable(k);
            objectiveTerm2.addTerm(k.getType().getCost(), vmGracePeriodVariable);
        }

        var objectiveTerm3 = cplex.linearNumExpr();
        for (var s1 : model.getServices().values()) {
            for (var s2 : model.getServices().values()) {
                if (s1 == s2) continue;
                for (var c1 : s1.getContainerTypes()) {
                    for (var c2 : s2.getContainerTypes()) {
                        for (var k1 : model.getVms().values()) {
                            for (var k2 : model.getVms().values()) {
                                var colocatedVariable = getColocationVariable(c1, k1, c2, k2);
                                objectiveTerm3.addTerm(colocatedVariable, state.getAffinityBetween(s1, s2));
                            }
                        }
                    }
                }
            }
        }

        var objectiveTerm4 = cplex.linearNumExpr();
        for (var s : model.getServices().values()) {
            var serviceCapacity = cplex.linearNumExpr();
            for (var cs : s.getContainerTypes()) {
                for (var k : model.getVms().values()) {
                    var decisionVariable = getDecisionVariable(cs, k); // x_(cs,k)
                    var containerCapacity = cs.getRpmCapacity(); // Q_cs
                    serviceCapacity.addTerm(decisionVariable, containerCapacity);
                }
            }

            var totalServiceLoad = state.getTotalServiceLoad().get(s.getName()); // L_s

            var serviceTerm = cplex.linearNumExpr();
            serviceTerm.add(serviceCapacity);
            serviceTerm.setConstant(-totalServiceLoad);

            objectiveTerm4.add(serviceTerm);
        }

        cplex.addMinimize(cplex.sum(
                cplex.prod(objectiveTerm1, 1),
                cplex.prod(objectiveTerm2, 0.2),
                cplex.prod(objectiveTerm3, 0.01),
                cplex.prod(objectiveTerm4, 0.000001)
        ));

//        cplex.addMinimize(objectiveTerm1);


        // Constraint 4.6
        for (var s : model.getServices().values()) {
            var leftSide = cplex.linearNumExpr();
            for (var cs : s.getContainerTypes()) {
                for (var k : model.getVms().values()) {
                    var decisionVariable = getDecisionVariable(cs, k); // x_(cs,k)
                    var containerCapacity = cs.getRpmCapacity(); // Q_cs
                    leftSide.addTerm(decisionVariable, containerCapacity);
                }
            }

            var totalServiceLoad = state.getTotalServiceLoad().get(s.getName()); // L_s
            cplex.addGe(leftSide, totalServiceLoad);
        }


        // Constraint 4.7
        for (var v : model.getVmTypes()) {
            var vmInstances = v.getDataCenter().getVmsByType().get(v.getLabel());
            for (var kv : vmInstances) {

                var leftSide = cplex.linearNumExpr();
                for (var s : model.getServices().values()) {
                    var cpuRequirementVariable = getResourceVariable(s, kv, ResourceType.CPU); // mu_{(s,k_v)}^{CPU}
                    leftSide.addTerm(cpuRequirementVariable, 1);
                }

                var rightSide = cplex.linearNumExpr();
                var suppliedResources = v.getCpuCores() * 1024; // TODO
                var vmAllocationVariable = getVmAllocationVariable(kv);
                rightSide.addTerm(suppliedResources, vmAllocationVariable);

                cplex.addLe(leftSide, rightSide);
            }
        }

        // Constraint 4.8
        for (var v : model.getVmTypes()) {
            var vmInstances = v.getDataCenter().getVmsByType().get(v.getLabel());
            for (var kv : vmInstances) {

                var leftSide = cplex.linearNumExpr();
                for (var s : model.getServices().values()) {
                    var memRequirementVariable = getResourceVariable(s, kv, ResourceType.MEMORY); // mu_{(s,k_v)}^{MEM}
                    leftSide.addTerm(memRequirementVariable, 1);
                }

                var rightSide = cplex.linearNumExpr();
                var suppliedResources = v.getMemory();
                var vmAllocationVariable = getVmAllocationVariable(kv);
                rightSide.addTerm(suppliedResources, vmAllocationVariable);

                cplex.addLe(leftSide, rightSide);
            }
        }


        // Constraint 4.9
        for (var s : model.getServices().values()) {
            for (var k : model.getVms().values()) {
                var leftSide = cplex.linearNumExpr();
                for (var cs : s.getContainerTypes()) {
                    var decisionVariable = getDecisionVariable(cs, k);
                    leftSide.addTerm(decisionVariable, 1);
                }

                cplex.addLe(leftSide, 1);
            }
        }


        // Constraint 4.10
        for (var s : model.getServices().values()) {
            for (var k : model.getVms().values()) {

                var leftSide = cplex.linearNumExpr();
                var cpuRequirementVariable = getResourceVariable(s, k, ResourceType.CPU); // mu_{(s,k)}^{CPU}
                leftSide.addTerm(cpuRequirementVariable, 1);

                var rightSide = cplex.linearNumExpr();
                for (var cs : s.getContainerTypes()) {
                    var decisionVariable = getDecisionVariable(cs, k);
                    var cpuUsage = cs.getCpuShares(); // U_{c_s}^{CPU}
                    rightSide.addTerm(decisionVariable, cpuUsage);
                }

                cplex.addGe(leftSide, rightSide);
            }
        }
        // Constraint 4.11
        for (var s : model.getServices().values()) {
            for (var k : model.getVms().values()) {

                var leftSide = cplex.linearNumExpr();
                var cpuRequirementVariable = getResourceVariable(s, k, ResourceType.CPU); // mu_{(s,k)}^{CPU}
                leftSide.addTerm(cpuRequirementVariable, 1);

                var rightSideSum = 0;
                for (var cs : s.getContainerTypes()) {
                    var isPreviouslyAllocated = state.getCurrentTargetAllocation().isAllocated(cs, k); // z_{cs,k}
                    var cpuUsage = cs.getCpuShares(); // U_{c_s}^{CPU}
                    if (isPreviouslyAllocated) {
                        rightSideSum += cpuUsage;
                    }
                }

                // rightSide = sum * (1 - g(k)) = (sum - sum * g(k))
                var rightSide = cplex.linearNumExpr();
                var enteringGracePeriod = getVmGracePeriodVariable(k);
                rightSide.setConstant(rightSideSum);
                rightSide.addTerm(enteringGracePeriod, -rightSideSum);

                cplex.addGe(leftSide, rightSide);
            }
        }

        // Constraint 4.12
        for (var s : model.getServices().values()) {
            for (var k : model.getVms().values()) {

                var leftSide = cplex.linearNumExpr();
                var memRequirementVariable = getResourceVariable(s, k, ResourceType.MEMORY); // mu_{(s,k)}^{MEM}
                leftSide.addTerm(memRequirementVariable, 1);

                var rightSide = cplex.linearNumExpr();
                for (var cs : s.getContainerTypes()) {
                    var decisionVariable = getDecisionVariable(cs, k);
                    var memUsage = cs.getMemory().toMegabytes(); // U_{c_s}^{MEM}
                    rightSide.addTerm(decisionVariable, memUsage);
                }

                cplex.addGe(leftSide, rightSide);
            }
        }
        // Constraint 4.13
        for (var s : model.getServices().values()) {
            for (var k : model.getVms().values()) {

                var leftSide = cplex.linearNumExpr();
                var memRequirementVariable = getResourceVariable(s, k, ResourceType.MEMORY); // mu_{(s,k)}^{MEM}
                leftSide.addTerm(memRequirementVariable, 1);

                var rightSideSum = 0;
                for (var cs : s.getContainerTypes()) {
                    var isPreviouslyAllocated = state.getCurrentTargetAllocation().isAllocated(cs, k); // z_{cs,k}
                    var memUsage = cs.getMemory().toMegabytes(); // U_{c_s}^{MEM}
                    if (isPreviouslyAllocated) {
                        rightSideSum += memUsage;
                    }
                }

                // rightSide = sum * (1 - g(k)) = (sum - sum * g(k))
                var rightSide = cplex.linearNumExpr();
                var enteringGracePeriod = getVmGracePeriodVariable(k);
                rightSide.setConstant(rightSideSum);
                rightSide.addTerm(enteringGracePeriod, -rightSideSum);

                cplex.addGe(leftSide, rightSide);
            }
        }


        // Constraint 4.14
        for (var k : model.getVms().values()) {
            var leftSide = cplex.linearNumExpr();
            for (var c : model.getContainerTypes()) {
                var decisionVariable = getDecisionVariable(c, k);
                leftSide.addTerm(1, decisionVariable);
            }

            var rightSide = cplex.linearNumExpr();
            var vmAllocatedVariable = getVmAllocationVariable(k);
            rightSide.addTerm(vmAllocatedVariable, 1_000);

            cplex.addLe(leftSide, rightSide);
        }


        // Constraint 4.15
        for (var k : model.getVms().values()) {
            var leftSide = getVmGracePeriodVariable(k);

            var rightSide = cplex.linearNumExpr();
            var isPreviouslyLeased = state.getLeasedVms().contains(k); // beta_k
            var vmAllocatedVariable = getVmAllocationVariable(k);

            if (isPreviouslyLeased) {
                rightSide.setConstant(1);
                rightSide.addTerm(vmAllocatedVariable, -1);
            } else {
                rightSide.setConstant(0);
            }

            cplex.addEq(leftSide, rightSide);
        }


        // coloc >= x1 + x2 - 1
        // colo >= 0
        // colo <= 1
//        for (var c1 : model.getContainerTypes()) {
//            for (var k1 : model.getVms().values()) {
//                for (var c2 : model.getContainerTypes()) {
//                    for (var k2 : model.getVms().values()) {
//                        var decisionVariable1 = getDecisionVariable(c1, k1);
//                        var decisionVariable2 = getDecisionVariable(c2, k2);
//
//                        var leftSide = getColocationVariable(c1, k1, c2, k2);
//
//                        var rightSide = cplex.linearNumExpr();
//                        rightSide.addTerm(decisionVariable1, 1);
//                        rightSide.addTerm(decisionVariable2, 1);
//                        rightSide.setConstant(-1);
//
//                        cplex.addGe(leftSide, rightSide);
//                    }
//                }
//            }
//        }


        for (var s1 : model.getServices().values()) {
            for (var s2 : model.getServices().values()) {
                if (s1 == s2) continue;

                for (var c1 : s1.getContainerTypes()) {
                    for (var c2 : s2.getContainerTypes()) {
                        for (var k1 : model.getVms().values()) {
                            for (var k2 : model.getVms().values()) {
                                var decisionVariable1 = getDecisionVariable(c1, k1);
                                var decisionVariable2 = getDecisionVariable(c2, k2);

                                var leftSide = getColocationVariable(c1, k1, c2, k2);

                                var rightSide = cplex.linearNumExpr();
                                rightSide.addTerm(decisionVariable1, 1);
                                rightSide.addTerm(decisionVariable2, 1);
                                rightSide.setConstant(-1);

                                cplex.addGe(leftSide, rightSide);
                            }
                        }
                    }
                }

            }
        }

    }

}
