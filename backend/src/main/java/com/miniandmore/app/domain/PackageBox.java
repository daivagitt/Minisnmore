package com.miniandmore.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "package_box")
public class PackageBox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 80)
    @Column(name = "name", length = 80, nullable = false, unique = true)
    private String name;

    @NotNull
    @Min(1)
    @Column(name = "length_cm", nullable = false)
    private Integer lengthCm;

    @NotNull
    @Min(1)
    @Column(name = "breadth_cm", nullable = false)
    private Integer breadthCm;

    @NotNull
    @Min(1)
    @Column(name = "height_cm", nullable = false)
    private Integer heightCm;

    @Min(1)
    @Column(name = "weight_grams")
    private Integer weightGrams;

    public Long getId() {
        return id;
    }

    public PackageBox id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public PackageBox name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLengthCm() {
        return lengthCm;
    }

    public PackageBox lengthCm(Integer lengthCm) {
        this.setLengthCm(lengthCm);
        return this;
    }

    public void setLengthCm(Integer lengthCm) {
        this.lengthCm = lengthCm;
    }

    public Integer getBreadthCm() {
        return breadthCm;
    }

    public PackageBox breadthCm(Integer breadthCm) {
        this.setBreadthCm(breadthCm);
        return this;
    }

    public void setBreadthCm(Integer breadthCm) {
        this.breadthCm = breadthCm;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public PackageBox heightCm(Integer heightCm) {
        this.setHeightCm(heightCm);
        return this;
    }

    public void setHeightCm(Integer heightCm) {
        this.heightCm = heightCm;
    }

    public Integer getWeightGrams() {
        return weightGrams;
    }

    public PackageBox weightGrams(Integer weightGrams) {
        this.setWeightGrams(weightGrams);
        return this;
    }

    public void setWeightGrams(Integer weightGrams) {
        this.weightGrams = weightGrams;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PackageBox)) {
            return false;
        }
        return id != null && id.equals(((PackageBox) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
