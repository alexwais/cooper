package at.ac.tuwien.dsg.cooper.benchmark;


import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.AnalysisResult;
import at.ac.tuwien.dsg.cooper.scheduler.dto.OptResult;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;


@RequiredArgsConstructor
@JsonPropertyOrder({"t", "cost", "avgLatency", "opt", "imageDownloads", "fitness", "neutralFitness", "runtime"})
public class BenchmarkRecord {

    @Getter
    private final int t;

    public float getCost() {
        return currentAllocation.getTotalCost();
    }

    @Getter
    @Setter
    private Double avgLatency = null;

    @Getter
    private final Long imageDownloads;

    public boolean getOpt() {
        return currentOptResult != null;
    }

    public Float getFitness() {
        return currentOptResult == null ? null : currentOptResult.getFitness();
    }

    public Float getNeutralFitness() {
        return currentOptResult == null ? null : currentOptResult.getNeutralFitness();
    }

    public Integer getRuntime() {
        return currentOptResult == null ? null : currentOptResult.getRuntimeInMilliseconds().intValue();
    }


    @Getter
    @JsonIgnore
    private final SystemMeasures measures;

    private final AnalysisResult analysisResult;

    @Getter
    @JsonIgnore
    private final OptResult currentOptResult;

    @Getter
    @JsonIgnore
    private final OptResult lastOptResult;

    @Getter
    @JsonIgnore
    private final Allocation currentAllocation;

}
