package at.alexwais.cooper.ilp;

import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.Optimizer;
import at.alexwais.cooper.scheduler.SystemMeasures;
import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import ilog.cplex.IloCplex;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;


@Slf4j
public class IlpOptimizer implements Optimizer {

    private final Model model;

    public IlpOptimizer(Model model) {
        this.model = model;
    }


    public OptimizationResult optimize(Allocation previousAllocation, SystemMeasures systemMeasures) {
        var stopWatch = new StopWatch();
        stopWatch.start();

        var problem = new IlpProblem(model, previousAllocation, systemMeasures);

        var params = new IloCplex.ParameterSet();
//        params.setParam(IloCplex.DoubleParam.MIP.Tolerances.MIPGap, 0.00000000000000000000000000000001);
//        params.setParam(IloCplex.DoubleParam.TimeLimit, 20);
//        params.setParam(IloCplex.IntParam.RootAlgorithm, IloCplex.Algorithm.Primal);

        var allocationTuples = problem.solve(params);

        stopWatch.stop();

        return new OptimizationResult(model, systemMeasures, allocationTuples);
    }

}
