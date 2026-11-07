package com.venuenexus.venue.entity;

import com.venuenexus.common.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Top-level tenant customer entity.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant")
public class Tenant extends BaseAuditableEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false)
    private String status; // 'ACTIVE', 'SUSPENDED'

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "branding")
    private String branding;
}
