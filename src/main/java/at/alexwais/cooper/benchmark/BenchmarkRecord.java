package at.alexwais.cooper.benchmark;


import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.AnalysisResult;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;


@AllArgsConstructor
@JsonPropertyOrder({"t", "cost", "opt", "fitness", "neutralFitness", "runtime"})
public class BenchmarkRecord {

    @Getter
    private int t;

    public float getCost() {
        return currentAllocation.getTotalCost();
    }

    public boolean getOpt() {
        return optimizationResult != null;
    }

    public Float getFitness() {
        return optimizationResult == null ? null : optimizationResult.getFitness();
    }

    public Float getNeutralFitness() {
        return optimizationResult == null ? null : optimizationResult.getNeutralFitness();
    }

    public Integer getRuntime() {
        return optimizationResult == null ? null : optimizationResult.getRuntimeInMilliseconds().intValue();
    }


    @Getter
    @JsonIgnore
    private SystemMeasures measures;

    private AnalysisResult analysisResult;

    @Getter
    @JsonIgnore
    private OptimizationResult optimizationResult;

    @Getter
    @JsonIgnore
    private Allocation currentAllocation;

}
