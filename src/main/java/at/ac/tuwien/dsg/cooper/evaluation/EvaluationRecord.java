package at.ac.tuwien.dsg.cooper.evaluation;


import at.ac.tuwien.dsg.cooper.scheduler.dto.Allocation;
import at.ac.tuwien.dsg.cooper.scheduler.dto.OptResult;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;


@RequiredArgsConstructor
@JsonPropertyOrder({"t", "cost", "accCost", "accLatency", "latency", "interactionCalls", "opt", "imageDownloads", "fitness", "neutralFitness",  "vmCount",  "peakContainerCount", "runtime"})
public class EvaluationRecord {

    @Getter
    @JsonIgnore
    private final int seconds;

    @Getter
    @Setter
    private Double latency = null;

    @Getter
    @Setter
    private Float cost = 0f;

    @Getter
    @Setter
    private Float accCost = 0f;

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

    public Float getRuntime() {
        return currentOptResult == null ? null : currentOptResult.getRuntimeInMilliseconds().floatValue() / 1000f;
    }

    public Float getAccLatency() {
        var totalLatency = records.stream().map(r -> r.getLatency() * r.getInteractionCalls()).reduce(Double::sum).get().floatValue();
        var totalCalls = records.stream().map(EvaluationRecord::getInteractionCalls).reduce(Integer::sum).get();
        return totalLatency / totalCalls;
    }

    @JsonIgnore
    @Setter
    private List<EvaluationRecord> records;

    @Getter
    @JsonIgnore
    private final SystemMeasures measures;

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
