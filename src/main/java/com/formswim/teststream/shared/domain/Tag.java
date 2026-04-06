package com.formswim.teststream.shared.domain;

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
    name = "custom_tags",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_custom_tags_team_name", columnNames = { "team_key", "name" })
    }
)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(name = "team_key", nullable = false, length = 100)
    private String teamKey;

    protected Tag() {
    }

    public Tag(String name, String color, String teamKey) {
        this.name = name;
        this.color = color;
        this.teamKey = teamKey;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    @JsonIgnore
    public String getTeamKey() {
        return teamKey;
    }
}
