package com.infosys.cfootprint.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

@Entity
@Table(name = "badge_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BadgeDefinition {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "badge_type", nullable = false, unique = true)
    private String badgeType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "icon_name")
    private String iconName;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(name = "rule_value", nullable = false)
    private Double ruleValue;
}
