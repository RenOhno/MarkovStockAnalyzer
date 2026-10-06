package com.example.markovstockanalyzer.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.io.Serializable;

@Embeddable
public class TransitionProbabilityId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "analysis_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long analysisId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "from_state", nullable = false, columnDefinition = "char(4)", length = 4)
    private String fromState;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "to_state", nullable = false, columnDefinition = "char(4)", length = 4)
    private String toState;

    public TransitionProbabilityId() {}
    public TransitionProbabilityId(Long analysisId, String fromState, String toState) {
        this.analysisId = analysisId;
        this.fromState = fromState;
        this.toState = toState;
    }

    public Long getAnalysisId() { return analysisId; }
    public void setAnalysisId(Long analysisId) { this.analysisId = analysisId; }
    public String getFromState() { return fromState; }
    public void setFromState(String fromState) { this.fromState = fromState; }
    public String getToState() { return toState; }
    public void setToState(String toState) { this.toState = toState; }

    @Override public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof TransitionProbabilityId that)) { return false; }
        return Objects.equals(analysisId, that.analysisId) && Objects.equals(fromState, that.fromState) && Objects.equals(toState, that.toState);
    }
    @Override public int hashCode() { return Objects.hash(analysisId, fromState, toState); }
}
