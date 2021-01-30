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
@JsonPropertyOrder({"t", "cost", "avgLatency", "interactionCalls", "opt", "imageDownloads", "fitness", "neutralFitness",  "vmCount",  "peakContainerCount", "runtime"})
public class BenchmarkRecord {

    @Getter
    @JsonIgnore
    private final int seconds;

    @Getter
    @Setter
    private Double avgLatency = null;

    @Getter
    @Setter
    private Float cost = 0f; // accumulated

    @Getter
    @Setter
    private Integer interactionCalls = null;

    @Getter
    @Setter
    private Integer peakContainerCount = null;

    @Getter
    private final Long imageDownloads;

    public int getT() { // in minutes
        return seconds / 60;
    }

    public boolean getOpt() {
        return currentOptResult != null;
    }

    public Float getFitness() {
        return currentOptResult == null ? null : currentOptResult.getFitness();
    }

    public Float getNeutralFitness() {
        return currentOptResult == null ? null : currentOptResult.getNeutralFitness();
    }

    public Integer getVmCount() {
        return currentAllocation.getRunningVms().size();
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
