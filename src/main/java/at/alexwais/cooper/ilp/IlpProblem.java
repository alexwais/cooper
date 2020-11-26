package at.alexwais.cooper.ilp;

import at.alexwais.cooper.config.OptimizationConfig;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;


@Slf4j
public class IlpProblem {

    private final Model model;
    private final Allocation previousAllocation;
    private final SystemMeasures systemMeasures;

    private final OptimizationConfig config;

    private final IloCplex cplex;
    private final Variables variables;


    public IlpProblem(Model model, Allocation previousAllocation, SystemMeasures systemMeasures, OptimizationConfig config) {
        this.model = model;
        this.config = config;
        this.previousAllocation = previousAllocation;
        this.systemMeasures = systemMeasures;

        try {
            this.cplex = new IloCplex();
            variables = new Variables(model, cplex, config.isEnableColocation());
            buildObjectiveFunction();
            buildConstraints();
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
                var decisionVariable = variables.getDecisionVariable(c, k);
                var isAllocated = cplex.getValue(decisionVariable);
                if (isAllocated > 0) {
                    allocationTuples.add(new Allocation.AllocationTuple(k, c, true));
                }
            }
        }
        return allocationTuples;
    }


    private void buildObjectiveFunction() throws IloException {
        var objectiveTerm1 = cplex.linearNumExpr();
        var objectiveTerm2 = cplex.linearNumExpr();

        for (var k : model.getVms().values()) {
            var vmAllocationVariable = variables.getVmAllocationVariable(k);
            objectiveTerm1.addTerm(k.getType().getCost(), vmAllocationVariable);

            var vmGracePeriodVariable = variables.getVmGracePeriodVariable(k);
            objectiveTerm2.addTerm(k.getType().getCost() + 0.01, vmGracePeriodVariable); // TODO constraint for grace period cost? (like for vmAllocationVariable)
        }

        var objectiveTerm3 = cplex.linearNumExpr();
        for (var wrapper : variables.getConcurrentAllocationVariables()) {
            var affinity = systemMeasures.getAffinityBetween(wrapper.getC1().getService(), wrapper.getC2().getService());
            var distance = model.getDistanceBetween(wrapper.getK1(), wrapper.getK2());
            objectiveTerm3.addTerm(wrapper.getDecisionVariable(), affinity * distance);
        }

//        for (var s1 : model.getServices().values()) {
//            for (var s2 : model.getServices().values()) {
//                if (s1 == s2) continue;
//                for (var c1 : s1.getContainerTypes()) {
//                    for (var c2 : s2.getContainerTypes()) {
//                        for (var k1 : model.getVms().values()) {
//                            for (var k2 : model.getVms().values()) {
//                                if (k1 == k2) continue;
//                                var concurrentAllocationVariable = variables.getConcurrentAllocationVariable(c1, k1, c2, k2);
//                                var affinity = state.getAffinityBetween(s1, s2);
//                                var distance = model.getDistanceBetween(k1, k2);
//                                objectiveTerm3.addTerm(concurrentAllocationVariable, affinity * distance);
//                            }
//                        }
//                    }
//                }
//            }
//        }

        var objectiveTerm4 = cplex.linearNumExpr();
        for (var s : model.getServices().values()) {
            var serviceCapacity = cplex.linearNumExpr();
            for (var cs : s.getContainerTypes()) {
                for (var k : model.getVms().values()) {
                    var decisionVariable = variables.getDecisionVariable(cs, k); // x_(cs,k)
                    var containerCapacity = cs.getRpmCapacity(); // Q_cs
                    serviceCapacity.addTerm(decisionVariable, containerCapacity);
                }
            }

            var totalServiceLoad = systemMeasures.getTotalServiceLoad().get(s.getName()); // L_s

            var serviceTerm = cplex.linearNumExpr();
            serviceTerm.add(serviceCapacity);
            serviceTerm.setConstant(-totalServiceLoad);

            objectiveTerm4.add(serviceTerm);
        }

        var objectiveFunction = cplex.sum(
                cplex.prod(objectiveTerm1, 1),
                cplex.prod(objectiveTerm2, 0.2),
                cplex.prod(objectiveTerm4, 0.000001)
        );
        if (config.isEnableColocation()) {
            objectiveFunction = cplex.sum(objectiveFunction, cplex.prod(objectiveTerm3, 0.001));
        }

        cplex.addMinimize(objectiveFunction);
    }


    private void buildConstraints() throws IloException {
        // Constraint 4.6
        for (var s : model.getServices().values()) {
            var leftSide = cplex.linearNumExpr();
            for (var cs : s.getContainerTypes()) {
                for (var k : model.getVms().values()) {
                    var decisionVariable = variables.getDecisionVariable(cs, k); // x_(cs,k)
                    var containerCapacity = cs.getRpmCapacity(); // Q_cs
                    leftSide.addTerm(decisionVariable, containerCapacity);
                }
            }

            var totalServiceLoad = systemMeasures.getTotalServiceLoad().get(s.getName()); // L_s
            cplex.addGe(leftSide, totalServiceLoad);
        }


        // Constraint 4.7
        for (var v : model.getVmTypes()) {
            var vmInstances = v.getDataCenter().getVmsByType().get(v.getLabel());
            for (var kv : vmInstances) {

                var leftSide = cplex.linearNumExpr();
                for (var s : model.getServices().values()) {
                    var cpuRequirementVariable = variables.getResourceVariable(s, kv, Variables.ResourceType.CPU); // mu_{(s,k_v)}^{CPU}
                    leftSide.addTerm(cpuRequirementVariable, 1);
                }

                var rightSide = cplex.linearNumExpr();
                var suppliedResources = v.getCpuUnits();
                var vmAllocationVariable = variables.getVmAllocationVariable(kv);
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
                    var memRequirementVariable = variables.getResourceVariable(s, kv, Variables.ResourceType.MEMORY); // mu_{(s,k_v)}^{MEM}
                    leftSide.addTerm(memRequirementVariable, 1);
                }

                var rightSide = cplex.linearNumExpr();
                var suppliedResources = v.getMemory();
                var vmAllocationVariable = variables.getVmAllocationVariable(kv);
                rightSide.addTerm(suppliedResources, vmAllocationVariable);

                cplex.addLe(leftSide, rightSide);
            }
        }


        // Constraint 4.9
        for (var s : model.getServices().values()) {
            for (var k : model.getVms().values()) {
                var leftSide = cplex.linearNumExpr();
                for (var cs : s.getContainerTypes()) {
                    var decisionVariable = variables.getDecisionVariable(cs, k);
                    leftSide.addTerm(decisionVariable, 1);
                }

                cplex.addLe(leftSide, 1);
            }
        }


        // Constraint 4.10
        for (var s : model.getServices().values()) {
            for (var k : model.getVms().values()) {

                var leftSide = cplex.linearNumExpr();
                var cpuRequirementVariable = variables.getResourceVariable(s, k, Variables.ResourceType.CPU); // mu_{(s,k)}^{CPU}
                leftSide.addTerm(cpuRequirementVariable, 1);

                var rightSide = cplex.linearNumExpr();
                for (var cs : s.getContainerTypes()) {
                    var decisionVariable = variables.getDecisionVariable(cs, k);
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
                var cpuRequirementVariable = variables.getResourceVariable(s, k, Variables.ResourceType.CPU); // mu_{(s,k)}^{CPU}
                leftSide.addTerm(cpuRequirementVariable, 1);

                var rightSideSum = 0;
                for (var cs : s.getContainerTypes()) {
                    var isPreviouslyAllocated = previousAllocation.isAllocated(cs, k); // z_{cs,k}
                    var cpuUsage = cs.getCpuShares(); // U_{c_s}^{CPU}
                    if (isPreviouslyAllocated) {
                        rightSideSum += cpuUsage;
                    }
                }

                // rightSide = sum * (1 - g(k)) = (sum - sum * g(k))
                var rightSide = cplex.linearNumExpr();
                var enteringGracePeriod = variables.getVmGracePeriodVariable(k);
                rightSide.setConstant(rightSideSum);
                rightSide.addTerm(enteringGracePeriod, -rightSideSum);

                cplex.addGe(leftSide, rightSide);
            }
        }

        // Constraint 4.12
        for (var s : model.getServices().values()) {
            for (var k : model.getVms().values()) {

                var leftSide = cplex.linearNumExpr();
                var memRequirementVariable = variables.getResourceVariable(s, k, Variables.ResourceType.MEMORY); // mu_{(s,k)}^{MEM}
                leftSide.addTerm(memRequirementVariable, 1);

                var rightSide = cplex.linearNumExpr();
                for (var cs : s.getContainerTypes()) {
                    var decisionVariable = variables.getDecisionVariable(cs, k);
                    var memUsage = cs.getMemory(); // U_{c_s}^{MEM}
                    rightSide.addTerm(decisionVariable, memUsage);
                }

                cplex.addGe(leftSide, rightSide);
            }
        }
        // Constraint 4.13
        for (var s : model.getServices().values()) {
            for (var k : model.getVms().values()) {

                var leftSide = cplex.linearNumExpr();
                var memRequirementVariable = variables.getResourceVariable(s, k, Variables.ResourceType.MEMORY); // mu_{(s,k)}^{MEM}
                leftSide.addTerm(memRequirementVariable, 1);

                var rightSideSum = 0;
                for (var cs : s.getContainerTypes()) {
                    var isPreviouslyAllocated = previousAllocation.isAllocated(cs, k); // z_{cs,k}
                    var memUsage = cs.getMemory(); // U_{c_s}^{MEM}
                    if (isPreviouslyAllocated) {
                        rightSideSum += memUsage;
                    }
                }

                // rightSide = sum * (1 - g(k)) = (sum - sum * g(k))
                var rightSide = cplex.linearNumExpr();
                var enteringGracePeriod = variables.getVmGracePeriodVariable(k);
                rightSide.setConstant(rightSideSum);
                rightSide.addTerm(enteringGracePeriod, -rightSideSum);

                cplex.addGe(leftSide, rightSide);
            }
        }


        // Constraint 4.14
        for (var k : model.getVms().values()) {
            var leftSide = cplex.linearNumExpr();
            for (var c : model.getContainerTypes()) {
                var decisionVariable = variables.getDecisionVariable(c, k);
                leftSide.addTerm(1, decisionVariable);
            }

            var rightSide = cplex.linearNumExpr();
            var vmAllocatedVariable = variables.getVmAllocationVariable(k);
            rightSide.addTerm(vmAllocatedVariable, 1_000);

            cplex.addLe(leftSide, rightSide);
        }

        // new constraint TODO
        for (var k : model.getVms().values()) {
            var sum = cplex.linearNumExpr();
            for (var c : model.getContainerTypes()) {
                var decisionVariable = variables.getDecisionVariable(c, k);
                sum.addTerm(1, decisionVariable);
            }

            var vmAllocatedVariable = variables.getVmAllocationVariable(k);
            var leftSide = cplex.linearNumExpr();
            leftSide.add(sum);
            leftSide.addTerm(-1, vmAllocatedVariable);

            cplex.addGe(leftSide, 0);
        }


        // Constraint 4.15
        for (var k : model.getVms().values()) {
            var leftSide = variables.getVmGracePeriodVariable(k);

            var rightSide = cplex.linearNumExpr();
            var isPreviouslyLeased = previousAllocation.getUsedVms().contains(k); // beta_k
            var vmAllocatedVariable = variables.getVmAllocationVariable(k);

            if (isPreviouslyLeased) {
                rightSide.setConstant(1);
                rightSide.addTerm(vmAllocatedVariable, -1);
            } else {
                rightSide.setConstant(0);
            }

            cplex.addEq(leftSide, rightSide);
        }


        if (config.isEnableColocation()) {
            for (var wrapper : variables.getConcurrentAllocationVariables()) {
                var decisionVariable1 = variables.getDecisionVariable(wrapper.getC1(), wrapper.getK1());
                var decisionVariable2 = variables.getDecisionVariable(wrapper.getC2(), wrapper.getK2());

                var leftSide = wrapper.getDecisionVariable();

                var rightSide = cplex.linearNumExpr();
                rightSide.addTerm(decisionVariable1, 1);
                rightSide.addTerm(decisionVariable2, 1);
                rightSide.setConstant(-1);

                cplex.addGe(leftSide, rightSide);
            }

//            for (var s1 : model.getServices().values()) {
//                for (var s2 : model.getServices().values()) {
//                    if (s1 == s2) continue;
//                    for (var c1 : s1.getContainerTypes()) {
//                        for (var c2 : s2.getContainerTypes()) {
//                            for (var k1 : model.getVms().values()) {
//                                for (var k2 : model.getVms().values()) {
//                                    if (k1 == k2) continue;
//
//                                    var decisionVariable1 = variables.getDecisionVariable(c1, k1);
//                                    var decisionVariable2 = variables.getDecisionVariable(c2, k2);
//
//                                    var leftSide = variables.getConcurrentAllocationVariable(c1, k1, c2, k2);
//
//                                    var rightSide = cplex.linearNumExpr();
//                                    rightSide.addTerm(decisionVariable1, 1);
//                                    rightSide.addTerm(decisionVariable2, 1);
//                                    rightSide.setConstant(-1);
//
//                                    cplex.addGe(leftSide, rightSide);
//                                }
//                            }
//                        }
//                    }
//                }
//            }
        }

    }

}
