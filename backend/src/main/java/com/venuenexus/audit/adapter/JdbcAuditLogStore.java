package com.venuenexus.audit.adapter;

import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.model.AuditRecord;
import com.venuenexus.audit.port.AuditLogStore;
import com.venuenexus.audit.service.AuditLogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC implementation of AuditLogStore using plain JdbcTemplate.
 * Maintains sequence counter in the audit_chain table and serializes chain progression
 * per tenant at the database level using row locks (SELECT ... FOR UPDATE) in a single transaction.
 */
public class JdbcAuditLogStore implements AuditLogStore {

    public static final UUID PLATFORM_CHAIN_KEY = new UUID(0L, 0L);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditLogStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ChainState lockChain(UUID tenantId) {
        UUID key = (tenantId != null) ? tenantId : PLATFORM_CHAIN_KEY;

        // 1. Ensure the counter row exists for this tenant/platform without overwriting existing state
        jdbcTemplate.update("""
            INSERT INTO audit_chain (chain_key, last_sequence, last_hash)
            VALUES (?, 0, ?)
            ON CONFLICT (chain_key) DO NOTHING
            """, key, AuditLogService.GENESIS_PREV_HASH);

        // 2. Lock chain row FOR UPDATE exclusively for the active transaction
        return jdbcTemplate.queryForObject("""
            SELECT last_sequence, last_hash
            FROM audit_chain
            WHERE chain_key = ?
            FOR UPDATE
            """, (rs, rowNum) -> new ChainState(
                rs.getLong("last_sequence"),
                rs.getString("last_hash")
        ), key);
    }

    @Override
    public void save(AuditRecord record) {
        // 1. Insert audit log record
        jdbcTemplate.update("""
            INSERT INTO audit_log (
                id, tenant_id, user_id, sequence, event_type, outcome,
                trace_id, ip_hash, details, occurred_at, prev_hash, hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            """,
                record.id(),
                record.tenantId(),
                record.userId(),
                record.sequence(),
                record.eventType().name(),
                record.outcome(),
                record.traceId(),
                record.ipHash(),
                record.details(),
                Timestamp.from(record.occurredAt()),
                record.prevHash(),
                record.hash()
        );

        // 2. Advance audit_chain counter row
        UUID key = (record.tenantId() != null) ? record.tenantId() : PLATFORM_CHAIN_KEY;
        jdbcTemplate.update("""
            UPDATE audit_chain
            SET last_sequence = ?, last_hash = ?
            WHERE chain_key = ?
            """,
                record.sequence(),
                record.hash(),
                key
        );
    }

    @Override
    public Optional<AuditRecord> findLatestByTenant(UUID tenantId) {
        String sql = (tenantId != null)
                ? "SELECT id, tenant_id, user_id, sequence, event_type, outcome, trace_id, ip_hash, details, occurred_at, prev_hash, hash FROM audit_log WHERE tenant_id = ? ORDER BY sequence DESC LIMIT 1"
                : "SELECT id, tenant_id, user_id, sequence, event_type, outcome, trace_id, ip_hash, details, occurred_at, prev_hash, hash FROM audit_log WHERE tenant_id IS NULL ORDER BY sequence DESC LIMIT 1";

        List<AuditRecord> results = (tenantId != null)
                ? jdbcTemplate.query(sql, this::mapRowToAuditRecord, tenantId)
                : jdbcTemplate.query(sql, this::mapRowToAuditRecord);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<AuditRecord> findAllByTenantOrderBySequenceAsc(UUID tenantId) {
        String sql = (tenantId != null)
                ? "SELECT id, tenant_id, user_id, sequence, event_type, outcome, trace_id, ip_hash, details, occurred_at, prev_hash, hash FROM audit_log WHERE tenant_id = ? ORDER BY sequence ASC"
                : "SELECT id, tenant_id, user_id, sequence, event_type, outcome, trace_id, ip_hash, details, occurred_at, prev_hash, hash FROM audit_log WHERE tenant_id IS NULL ORDER BY sequence ASC";

        return (tenantId != null)
                ? jdbcTemplate.query(sql, this::mapRowToAuditRecord, tenantId)
                : jdbcTemplate.query(sql, this::mapRowToAuditRecord);
    }

    @Override
    public int countByTenant(UUID tenantId) {
        String sql = (tenantId != null)
                ? "SELECT COUNT(*) FROM audit_log WHERE tenant_id = ?"
                : "SELECT COUNT(*) FROM audit_log WHERE tenant_id IS NULL";

        Integer count = (tenantId != null)
                ? jdbcTemplate.queryForObject(sql, Integer.class, tenantId)
                : jdbcTemplate.queryForObject(sql, Integer.class);

        return (count != null) ? count : 0;
    }

    @Override
    public void clear() {
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM audit_chain");
    }

    private AuditRecord mapRowToAuditRecord(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID tenantId = rs.getObject("tenant_id", UUID.class);
        UUID userId = rs.getObject("user_id", UUID.class);
        long sequence = rs.getLong("sequence");
        AuditEventType eventType = AuditEventType.valueOf(rs.getString("event_type"));
        String outcome = rs.getString("outcome");
        String traceId = rs.getString("trace_id");
        String ipHash = rs.getString("ip_hash");
        String details = rs.getString("details");
        Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();
        String prevHash = rs.getString("prev_hash");
        String hash = rs.getString("hash");

        return new AuditRecord(
                id,
                tenantId,
                userId,
                sequence,
                eventType,
                outcome,
                traceId,
                ipHash,
                details,
                occurredAt,
                prevHash,
                hash
        );
    }
}
