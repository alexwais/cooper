package at.ac.tuwien.dsg.cooper.ilp;

import at.ac.tuwien.dsg.cooper.api.Optimizer;
import at.ac.tuwien.dsg.cooper.config.OptimizationConfig;
import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.domain.VmInstance;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.OptResult;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import ilog.cplex.IloCplex;
import java.util.Map;
import java.util.Set;


public class IlpOptimizer implements Optimizer {

    private final Model model;
    private final OptimizationConfig config;

    public IlpOptimizer(Model model, OptimizationConfig config) {
        this.model = model;
        this.config = config;
    }


    public OptResult optimize(Allocation currentAllocation, SystemMeasures systemMeasures, Map<VmInstance, Set<Service>> cachedImages) {
        var problem = new IlpProblem(model, currentAllocation, systemMeasures, cachedImages, config);

        var params = new IloCplex.ParameterSet();
        // https://www.ibm.com/support/pages/node/397111
//        params.setParam(IloCplex.IntParam.Emphasis.MIP, 3);
//        params.setParam(IloCplex.DoubleParam.MIP.Tolerances.MIPGap, 0.0025);
//        params.setParam(IloCplex.DoubleParam.TimeLimit, 20);
//        params.setParam(IloCplex.IntParam.RootAlgorithm, IloCplex.Algorithm.Primal);

        var allocationTuples = problem.solve(params);

        return new OptResult(model, systemMeasures, allocationTuples);
    }

}
