package com.infosys.cfootprint.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "emission_factor")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmissionFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "activity_type", nullable = false, unique = true, length = 100)
    private String activityType;

    @Column(name = "factor", nullable = false)
    private Double factor;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;
}
