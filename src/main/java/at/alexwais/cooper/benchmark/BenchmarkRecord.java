package at.alexwais.cooper.benchmark;


import at.alexwais.cooper.scheduler.dto.Allocation;
import at.alexwais.cooper.scheduler.dto.AnalysisResult;
import at.alexwais.cooper.scheduler.dto.OptimizationResult;
import at.alexwais.cooper.scheduler.dto.SystemMeasures;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;


@RequiredArgsConstructor
@JsonPropertyOrder({"t", "cost", "avgLatency", "opt", "fitness", "neutralFitness", "runtime"})
public class BenchmarkRecord {

    @Getter
    private final int t;

    public float getCost() {
        return currentAllocation.getTotalCost();
    }

    @Getter
    @Setter
    private Double avgLatency = null;

    public boolean getOpt() {
        return currentOptimizationResult != null;
    }

    public Float getFitness() {
        return currentOptimizationResult == null ? null : currentOptimizationResult.getFitness();
    }

    public Float getNeutralFitness() {
        return currentOptimizationResult == null ? null : currentOptimizationResult.getNeutralFitness();
    }

    public Integer getRuntime() {
        return currentOptimizationResult == null ? null : currentOptimizationResult.getRuntimeInMilliseconds().intValue();
    }


    @Getter
    @JsonIgnore
    private final SystemMeasures measures;

    private final AnalysisResult analysisResult;

    @Getter
    @JsonIgnore
    private final OptimizationResult currentOptimizationResult;

    @Getter
    @JsonIgnore
    private final OptimizationResult lastOptimizationResult;

    @Getter
    @JsonIgnore
    private final Allocation currentAllocation;

}
