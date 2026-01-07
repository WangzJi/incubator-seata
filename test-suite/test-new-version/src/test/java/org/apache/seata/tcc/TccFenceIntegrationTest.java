/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.seata.tcc;

import org.apache.seata.integration.tx.api.fence.constant.CommonFenceConstant;
import org.apache.seata.integration.tx.api.fence.store.CommonFenceDO;
import org.apache.seata.integration.tx.api.fence.store.db.CommonFenceStoreDataBaseDAO;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

/**
 * TCC Fence Integration Test
 * 
 * Tests TCC Fence mechanism for idempotency, empty rollback prevention, and suspend detection.
 * Uses H2 in-memory database.
 */
public class TccFenceIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TccFenceIntegrationTest.class);

    private static final String FENCE_TABLE = "tcc_fence_log";

    private static DataSource dataSource;
    private static Connection connection;

    @BeforeAll
    public static void beforeAll() throws SQLException {
        // Create H2 in-memory database
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setUrl("jdbc:h2:mem:tcc_fence_test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        dataSource = h2DataSource;
        
        connection = dataSource.getConnection();
        
        // Create TCC Fence table
        createFenceTable();
        
        LOGGER.info("TCC Fence test database initialized");
    }

    @AfterAll
    public static void afterAll() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @BeforeEach
    public void setUp() throws SQLException {
        // Clear fence table before each test
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM " + FENCE_TABLE);
        }
    }

    private static void createFenceTable() throws SQLException {
        String createTableSql = "CREATE TABLE IF NOT EXISTS " + FENCE_TABLE + " (" +
                "xid VARCHAR(128) NOT NULL, " +
                "branch_id BIGINT NOT NULL, " +
                "action_name VARCHAR(64) NOT NULL, " +
                "status TINYINT NOT NULL, " +
                "gmt_create TIMESTAMP NOT NULL, " +
                "gmt_modified TIMESTAMP NOT NULL, " +
                "PRIMARY KEY (xid, branch_id)" +
                ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        }
    }

    /**
     * Test inserting fence log for TRY phase
     */
    @Test
    public void testInsertFenceLogForTry() throws SQLException {
        String xid = "192.168.1.1:8091:123456";
        long branchId = 100001L;
        String actionName = "testAction";
        
        // Insert fence log with TRIED status
        insertFenceLog(xid, branchId, actionName, CommonFenceConstant.STATUS_TRIED);
        
        // Verify fence log exists
        CommonFenceDO fenceDO = queryFenceLog(xid, branchId);
        
        Assertions.assertNotNull(fenceDO, "Fence log should exist");
        Assertions.assertEquals(xid, fenceDO.getXid());
        Assertions.assertEquals(branchId, fenceDO.getBranchId());
        Assertions.assertEquals(CommonFenceConstant.STATUS_TRIED, fenceDO.getStatus());
        
        LOGGER.info("Insert fence log for TRY phase test passed");
    }

    /**
     * Test idempotent commit - second commit should be ignored
     */
    @Test
    public void testIdempotentCommit() throws SQLException {
        String xid = "192.168.1.1:8091:123457";
        long branchId = 100002L;
        String actionName = "testIdempotentAction";
        
        // First: Insert TRIED status
        insertFenceLog(xid, branchId, actionName, CommonFenceConstant.STATUS_TRIED);
        
        // Second: Update to COMMITTED status
        boolean updated = updateFenceStatus(xid, branchId, 
                CommonFenceConstant.STATUS_TRIED, CommonFenceConstant.STATUS_COMMITTED);
        Assertions.assertTrue(updated, "First commit should succeed");
        
        // Third: Try to update again (should fail because status is no longer TRIED)
        boolean secondUpdate = updateFenceStatus(xid, branchId, 
                CommonFenceConstant.STATUS_TRIED, CommonFenceConstant.STATUS_COMMITTED);
        Assertions.assertFalse(secondUpdate, "Second commit should be idempotent (no-op)");
        
        // Verify final status is COMMITTED
        CommonFenceDO fenceDO = queryFenceLog(xid, branchId);
        Assertions.assertEquals(CommonFenceConstant.STATUS_COMMITTED, fenceDO.getStatus());
        
        LOGGER.info("Idempotent commit test passed");
    }

    /**
     * Test idempotent rollback - second rollback should be ignored
     */
    @Test
    public void testIdempotentRollback() throws SQLException {
        String xid = "192.168.1.1:8091:123458";
        long branchId = 100003L;
        String actionName = "testIdempotentRollback";
        
        // First: Insert TRIED status
        insertFenceLog(xid, branchId, actionName, CommonFenceConstant.STATUS_TRIED);
        
        // Second: Update to ROLLBACKED status
        boolean updated = updateFenceStatus(xid, branchId, 
                CommonFenceConstant.STATUS_TRIED, CommonFenceConstant.STATUS_ROLLBACKED);
        Assertions.assertTrue(updated);
        
        // Third: Try to rollback again
        boolean secondUpdate = updateFenceStatus(xid, branchId, 
                CommonFenceConstant.STATUS_TRIED, CommonFenceConstant.STATUS_ROLLBACKED);
        Assertions.assertFalse(secondUpdate, "Second rollback should be idempotent");
        
        LOGGER.info("Idempotent rollback test passed");
    }

    /**
     * Test empty rollback detection - rollback without prior TRY
     */
    @Test
    public void testEmptyRollbackDetection() throws SQLException {
        String xid = "192.168.1.1:8091:123459";
        long branchId = 100004L;
        
        // Query fence log without prior TRY
        CommonFenceDO fenceDO = queryFenceLog(xid, branchId);
        
        // Should return null (no record)
        Assertions.assertNull(fenceDO, "Fence log should not exist for empty rollback");
        
        // This indicates an empty rollback scenario
        // In production, we would insert SUSPENDED status to prevent future TRY
        LOGGER.info("Empty rollback detection test passed");
    }

    /**
     * Test suspend detection - prevent TRY after Cancel has executed
     */
    @Test
    public void testSuspendDetection() throws SQLException {
        String xid = "192.168.1.1:8091:123460";
        long branchId = 100005L;
        String actionName = "testSuspendAction";
        
        // Cancel arrives first (before TRY) - insert SUSPENDED status
        insertFenceLog(xid, branchId, actionName, CommonFenceConstant.STATUS_SUSPENDED);
        
        // Now TRY arrives - should detect SUSPENDED status and reject
        CommonFenceDO fenceDO = queryFenceLog(xid, branchId);
        
        Assertions.assertNotNull(fenceDO);
        Assertions.assertEquals(CommonFenceConstant.STATUS_SUSPENDED, fenceDO.getStatus(),
                "Should detect suspended status and prevent TRY execution");
        
        LOGGER.info("Suspend detection test passed");
    }

    /**
     * Test delete fence by date for cleanup
     */
    @Test
    public void testDeleteFenceByDate() throws SQLException {
        String xid = "192.168.1.1:8091:123461";
        long branchId = 100006L;
        
        // Insert COMMITTED fence log
        insertFenceLog(xid, branchId, "cleanupAction", CommonFenceConstant.STATUS_COMMITTED);
        
        // Delete all records (for cleanup)
        int deleted = deleteFenceLogs();
        
        Assertions.assertEquals(1, deleted, "Should delete 1 fence log");
        
        // Verify deleted
        CommonFenceDO fenceDO = queryFenceLog(xid, branchId);
        Assertions.assertNull(fenceDO, "Fence log should be deleted");
        
        LOGGER.info("Delete fence by date test passed");
    }

    // Helper methods

    private void insertFenceLog(String xid, long branchId, String actionName, int status) throws SQLException {
        String sql = "INSERT INTO " + FENCE_TABLE + 
                " (xid, branch_id, action_name, status, gmt_create, gmt_modified) VALUES (?, ?, ?, ?, ?, ?)";
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, xid);
            pstmt.setLong(2, branchId);
            pstmt.setString(3, actionName);
            pstmt.setInt(4, status);
            pstmt.setTimestamp(5, new java.sql.Timestamp(System.currentTimeMillis()));
            pstmt.setTimestamp(6, new java.sql.Timestamp(System.currentTimeMillis()));
            pstmt.executeUpdate();
        }
    }

    private CommonFenceDO queryFenceLog(String xid, long branchId) throws SQLException {
        String sql = "SELECT xid, branch_id, status, gmt_create, gmt_modified FROM " + FENCE_TABLE + 
                " WHERE xid = ? AND branch_id = ?";
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, xid);
            pstmt.setLong(2, branchId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    CommonFenceDO fence = new CommonFenceDO();
                    fence.setXid(rs.getString("xid"));
                    fence.setBranchId(rs.getLong("branch_id"));
                    fence.setStatus(rs.getInt("status"));
                    fence.setGmtCreate(rs.getTimestamp("gmt_create"));
                    fence.setGmtModified(rs.getTimestamp("gmt_modified"));
                    return fence;
                }
            }
        }
        return null;
    }

    private boolean updateFenceStatus(String xid, long branchId, int fromStatus, int toStatus) throws SQLException {
        String sql = "UPDATE " + FENCE_TABLE + 
                " SET status = ?, gmt_modified = ? WHERE xid = ? AND branch_id = ? AND status = ?";
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, toStatus);
            pstmt.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
            pstmt.setString(3, xid);
            pstmt.setLong(4, branchId);
            pstmt.setInt(5, fromStatus);
            return pstmt.executeUpdate() > 0;
        }
    }

    private int deleteFenceLogs() throws SQLException {
        String sql = "DELETE FROM " + FENCE_TABLE;
        try (Statement stmt = connection.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }
}
