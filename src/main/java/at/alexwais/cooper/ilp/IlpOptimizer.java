package at.alexwais.cooper.ilp;

import at.alexwais.cooper.config.OptimizationConfig;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.Optimizer;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import ilog.cplex.IloCplex;
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


    public OptimizationResult optimize(Allocation previousAllocation, SystemMeasures systemMeasures) {
        var problem = new IlpProblem(model, previousAllocation, systemMeasures, config);

        var params = new IloCplex.ParameterSet();
//        params.setParam(IloCplex.DoubleParam.MIP.Tolerances.MIPGap, 0.00000000000000000000000000000001);
//        params.setParam(IloCplex.DoubleParam.TimeLimit, 20);
//        params.setParam(IloCplex.IntParam.RootAlgorithm, IloCplex.Algorithm.Primal);


        var stopWatch = new StopWatch();
        stopWatch.start();

        var allocationTuples = problem.solve(params);

        stopWatch.stop();

        return new OptimizationResult(model, systemMeasures, allocationTuples, stopWatch.getTotalTimeMillis());
    }

}
