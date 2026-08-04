package com.infosys.cfootprint.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "goals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "target_reduction_percentage", nullable = false)
    private Double targetReductionPercentage;

    @Column(name = "period_type", nullable = false)
    private String periodType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "baseline_emission", nullable = false)
    private Double baselineEmission;

    @Column(name = "target_emission", nullable = false)
    private Double targetEmission;

    @Column(nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Column(name = "limit_alert_sent", nullable = false)
    @Builder.Default
    private boolean limitAlertSent = false;
}
