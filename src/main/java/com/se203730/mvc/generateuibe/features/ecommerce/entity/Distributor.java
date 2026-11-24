package com.se203730.mvc.generateuibe.features.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Nhà phân phối lấy từ public.distributors (V45). */
@Entity
@Table(name = "distributors", schema = "public")
@Getter
@NoArgsConstructor
public class Distributor {
    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;
}
