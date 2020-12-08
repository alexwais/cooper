package at.alexwais.cooper.ilp;

import at.alexwais.cooper.config.OptimizationConfig;
import at.alexwais.cooper.domain.Service;
import at.alexwais.cooper.domain.VmInstance;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.Optimizer;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import ilog.cplex.IloCplex;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;


@Slf4j
public class IlpOptimizer implements Optimizer {

    private final Model model;
    private final OptimizationConfig config;

    public IlpOptimizer(Model model, OptimizationConfig config) {
        this.model = model;
        this.config = config;
    }


    public OptimizationResult optimize(Allocation previousAllocation, SystemMeasures systemMeasures, Map<VmInstance, Set<Service>> imageCacheState) {
        var problem = new IlpProblem(model, previousAllocation, systemMeasures, imageCacheState, config);

        var params = new IloCplex.ParameterSet();
        // TODO https://www.ibm.com/support/pages/node/397111 + reference?
//        params.setParam(IloCplex.IntParam.Emphasis.MIP, 3);
//        params.setParam(IloCplex.DoubleParam.MIP.Tolerances.MIPGap, 0.0001);
//        params.setParam(IloCplex.DoubleParam.TimeLimit, 20);
//        params.setParam(IloCplex.IntParam.RootAlgorithm, IloCplex.Algorithm.Primal);


        var stopWatch = new StopWatch();
        stopWatch.start();

        var allocationTuples = problem.solve(params);

        stopWatch.stop();

        return new OptimizationResult(model, systemMeasures, allocationTuples, stopWatch.getTotalTimeMillis());
    }

}
