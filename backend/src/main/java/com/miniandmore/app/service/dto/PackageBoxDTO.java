package com.miniandmore.app.service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;

public class PackageBoxDTO implements Serializable {

    private Long id;

    @NotBlank
    @Size(min = 1, max = 80)
    private String name;

    @NotNull
    @Min(1)
    @JsonAlias("length_cm")
    private Integer lengthCm;

    @NotNull
    @Min(1)
    @JsonAlias("breadth_cm")
    private Integer breadthCm;

    @NotNull
    @Min(1)
    @JsonAlias("height_cm")
    private Integer heightCm;

    @Min(1)
    @JsonAlias("weight_grams")
    private Integer weightGrams;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLengthCm() {
        return lengthCm;
    }

    public void setLengthCm(Integer lengthCm) {
        this.lengthCm = lengthCm;
    }

    public Integer getBreadthCm() {
        return breadthCm;
    }

    public void setBreadthCm(Integer breadthCm) {
        this.breadthCm = breadthCm;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Integer heightCm) {
        this.heightCm = heightCm;
    }

    public Integer getWeightGrams() {
        return weightGrams;
    }

    public void setWeightGrams(Integer weightGrams) {
        this.weightGrams = weightGrams;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PackageBoxDTO other)) {
            return false;
        }
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }
}
