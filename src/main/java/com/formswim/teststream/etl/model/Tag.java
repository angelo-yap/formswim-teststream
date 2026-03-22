package com.formswim.teststream.etl.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "tag",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tag_team_normalized_name", columnNames = { "team_key", "normalized_name" })
    }
)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Column(name = "team_key", length = 100, nullable = false)
    private String teamKey;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @JsonIgnore
    @Column(name = "normalized_name", length = 200, nullable = false)
    private String normalizedName;

    protected Tag() {
    }

    public Tag(String teamKey, String name) {
        this.teamKey = teamKey;
        this.name = name;
        this.normalizedName = normalize(name);
    }

    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    public Long getId() {
        return id;
    }

    public String getTeamKey() {
        return teamKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.normalizedName = normalize(name);
    }

    public String getNormalizedName() {
        return normalizedName;
    }
}
