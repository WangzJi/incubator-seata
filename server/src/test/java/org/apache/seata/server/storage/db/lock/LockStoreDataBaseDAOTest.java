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
package org.apache.seata.server.storage.db.lock;

import org.apache.seata.common.exception.DataAccessException;
import org.apache.seata.common.exception.StoreException;
import org.apache.seata.common.loader.EnhancedServiceLoader;
import org.apache.seata.config.Configuration;
import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.core.constants.ConfigurationKeys;
import org.apache.seata.core.constants.ServerTableColumnsName;
import org.apache.seata.core.model.LockStatus;
import org.apache.seata.core.store.LockDO;
import org.apache.seata.core.store.db.DataSourceProvider;
import org.apache.seata.core.store.db.sql.lock.LockStoreSql;
import org.apache.seata.core.store.db.sql.lock.LockStoreSqlFactory;
import org.apache.seata.server.BaseSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "dbCaseEnabled", matches = "true")
public class LockStoreDataBaseDAOTest extends BaseSpringBootTest {

    private LockStoreDataBaseDAO lockStoreDataBaseDAO;

    @BeforeEach
    public void setUp() {
        DataSource dataSource =
                EnhancedServiceLoader.load(DataSourceProvider.class, "druid").provide();
        lockStoreDataBaseDAO = new LockStoreDataBaseDAO(dataSource);
    }

    @AfterEach
    public void tearDown() {
        // Clean up any test locks
        try {
            lockStoreDataBaseDAO.unLock("test-xid-1");
            lockStoreDataBaseDAO.unLock("test-xid-2");
            lockStoreDataBaseDAO.unLock("test-xid-conflict");
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    public void testInstanceCreation() {
        Assertions.assertNotNull(lockStoreDataBaseDAO);
    }

    @Test
    public void testAcquireSingleLock() {
        LockDO lockDO = createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "1");

        boolean result = lockStoreDataBaseDAO.acquireLock(lockDO);
        Assertions.assertTrue(result, "Should successfully acquire lock");

        // Clean up
        lockStoreDataBaseDAO.unLock(lockDO);
    }

    @Test
    public void testAcquireSingleLockDuplicate() {
        LockDO lockDO = createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "2");

        // First acquisition should succeed
        boolean firstResult = lockStoreDataBaseDAO.acquireLock(lockDO);
        Assertions.assertTrue(firstResult, "First lock acquisition should succeed");

        // Second acquisition with same xid should succeed (same transaction)
        boolean secondResult = lockStoreDataBaseDAO.acquireLock(lockDO);
        Assertions.assertTrue(secondResult, "Same XID should be able to reacquire lock");

        // Clean up
        lockStoreDataBaseDAO.unLock(lockDO);
    }

    @Test
    public void testAcquireLockConflict() {
        LockDO lockDO1 = createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "3");
        LockDO lockDO2 = createLockDO("test-xid-2", 2L, 2L, "test-resource", "test_table", "3");

        // First lock should succeed
        boolean firstResult = lockStoreDataBaseDAO.acquireLock(lockDO1);
        Assertions.assertTrue(firstResult, "First lock should succeed");

        // Second lock with different xid should fail
        boolean secondResult = lockStoreDataBaseDAO.acquireLock(lockDO2);
        Assertions.assertFalse(secondResult, "Conflicting lock should fail");

        // Clean up
        lockStoreDataBaseDAO.unLock(lockDO1);
    }

    @Test
    public void testAcquireBatchLocks() {
        List<LockDO> lockDOs = new ArrayList<>();
        lockDOs.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "10"));
        lockDOs.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "11"));
        lockDOs.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "12"));

        boolean result = lockStoreDataBaseDAO.acquireLock(lockDOs);
        Assertions.assertTrue(result, "Batch lock acquisition should succeed");

        // Clean up
        lockStoreDataBaseDAO.unLock(lockDOs);
    }

    @Test
    public void testAcquireBatchLocksWithDuplicates() {
        List<LockDO> lockDOs = new ArrayList<>();
        // Add same row key twice - should be deduplicated
        lockDOs.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "20"));
        lockDOs.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "20"));
        lockDOs.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "21"));

        boolean result = lockStoreDataBaseDAO.acquireLock(lockDOs);
        Assertions.assertTrue(result, "Batch lock with duplicates should succeed after deduplication");

        // Clean up
        lockStoreDataBaseDAO.unLock("test-xid-1");
    }

    @Test
    public void testAcquireBatchLocksConflict() {
        // First acquire a lock
        LockDO existingLock = createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "30");
        lockStoreDataBaseDAO.acquireLock(existingLock);

        // Try to acquire batch including conflicting lock
        List<LockDO> lockDOs = new ArrayList<>();
        lockDOs.add(createLockDO("test-xid-2", 2L, 2L, "test-resource", "test_table", "30")); // Conflict
        lockDOs.add(createLockDO("test-xid-2", 2L, 2L, "test-resource", "test_table", "31"));

        boolean result = lockStoreDataBaseDAO.acquireLock(lockDOs);
        Assertions.assertFalse(result, "Batch lock with conflict should fail");

        // Clean up
        lockStoreDataBaseDAO.unLock(existingLock);
    }

    @Test
    public void testAcquireLockWithSkipCheckLock() {
        LockDO lockDO = createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "40");
        List<LockDO> lockDOs = Arrays.asList(lockDO);

        boolean result = lockStoreDataBaseDAO.acquireLock(lockDOs, true, true);
        Assertions.assertTrue(result, "Lock acquisition with skipCheckLock should succeed");

        // Clean up
        lockStoreDataBaseDAO.unLock(lockDO);
    }

    @Test
    public void testUnLockSingleLock() {
        LockDO lockDO = createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "50");

        // Acquire then unlock
        lockStoreDataBaseDAO.acquireLock(lockDO);
        boolean result = lockStoreDataBaseDAO.unLock(lockDO);
        Assertions.assertTrue(result, "Unlock should succeed");
    }

    @Test
    public void testUnLockBatchLocks() {
        List<LockDO> lockDOs = new ArrayList<>();
        lockDOs.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "60"));
        lockDOs.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "61"));

        // Acquire then unlock
        lockStoreDataBaseDAO.acquireLock(lockDOs);
        boolean result = lockStoreDataBaseDAO.unLock(lockDOs);
        Assertions.assertTrue(result, "Batch unlock should succeed");
    }

    @Test
    public void testUnLockByXid() {
        List<LockDO> lockDOs = new ArrayList<>();
        lockDOs.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "70"));
        lockDOs.add(createLockDO("test-xid-1", 1L, 2L, "test-resource", "test_table", "71"));

        // Acquire locks
        lockStoreDataBaseDAO.acquireLock(lockDOs);

        // Unlock all by xid
        boolean result = lockStoreDataBaseDAO.unLock("test-xid-1");
        Assertions.assertTrue(result, "Unlock by xid should succeed");
    }

    @Test
    public void testUnLockByBranchId() {
        LockDO lockDO = createLockDO("test-xid-1", 1L, 100L, "test-resource", "test_table", "80");

        // Acquire lock
        lockStoreDataBaseDAO.acquireLock(lockDO);

        // Unlock by branchId
        boolean result = lockStoreDataBaseDAO.unLock(100L);
        Assertions.assertTrue(result, "Unlock by branchId should succeed");
    }

    @Test
    public void testIsLockable() {
        LockDO lockDO1 = createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "90");
        LockDO lockDO2 = createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "91");

        // Before acquiring, should be lockable
        List<LockDO> lockDOs = Arrays.asList(lockDO1, lockDO2);
        boolean result = lockStoreDataBaseDAO.isLockable(lockDOs);
        Assertions.assertTrue(result, "Should be lockable when no existing locks");

        // Acquire locks
        lockStoreDataBaseDAO.acquireLock(lockDOs);

        // Same xid should still be lockable
        boolean sameTxResult = lockStoreDataBaseDAO.isLockable(lockDOs);
        Assertions.assertTrue(sameTxResult, "Should be lockable by same xid");

        // Different xid should not be lockable
        LockDO conflictLock = createLockDO("test-xid-2", 2L, 2L, "test-resource", "test_table", "90");
        boolean conflictResult = lockStoreDataBaseDAO.isLockable(Arrays.asList(conflictLock));
        Assertions.assertFalse(conflictResult, "Should not be lockable by different xid");

        // Clean up
        lockStoreDataBaseDAO.unLock(lockDOs);
    }

    @Test
    public void testUpdateLockStatus() {
        LockDO lockDO = createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "100");

        // Acquire lock
        lockStoreDataBaseDAO.acquireLock(lockDO);

        // Update status to Rollbacking
        lockStoreDataBaseDAO.updateLockStatus("test-xid-1", LockStatus.Rollbacking);

        // Note: We can't easily verify the status update without querying the DB directly
        // The method should execute without throwing exceptions

        // Clean up
        lockStoreDataBaseDAO.unLock("test-xid-1");
    }

    @Test
    public void testAcquireLockWithAutoCommitFalse() {
        List<LockDO> lockDOs = new ArrayList<>();
        lockDOs.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "110"));

        boolean result = lockStoreDataBaseDAO.acquireLock(lockDOs, false, false);
        Assertions.assertTrue(result, "Lock acquisition with autoCommit=false should succeed");

        // Clean up
        lockStoreDataBaseDAO.unLock(lockDOs);
    }

    @Test
    public void testAcquireLockReacquireAfterPartialExists() {
        // First, acquire some locks
        List<LockDO> firstBatch = new ArrayList<>();
        firstBatch.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "120"));
        firstBatch.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "121"));
        lockStoreDataBaseDAO.acquireLock(firstBatch);

        // Then try to acquire overlapping locks
        List<LockDO> secondBatch = new ArrayList<>();
        secondBatch.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "120")); // Already exists
        secondBatch.add(createLockDO("test-xid-1", 1L, 1L, "test-resource", "test_table", "122")); // New

        boolean result = lockStoreDataBaseDAO.acquireLock(secondBatch);
        Assertions.assertTrue(result, "Should succeed when some locks already exist with same xid");

        // Clean up
        lockStoreDataBaseDAO.unLock("test-xid-1");
    }

    /**
     * Helper method to create a LockDO object
     */
    private LockDO createLockDO(
            String xid, Long transactionId, Long branchId, String resourceId, String tableName, String pk) {
        LockDO lockDO = new LockDO();
        lockDO.setXid(xid);
        lockDO.setTransactionId(transactionId);
        lockDO.setBranchId(branchId);
        lockDO.setResourceId(resourceId);
        lockDO.setTableName(tableName);
        lockDO.setPk(pk);
        lockDO.setRowKey(resourceId + "^^^" + tableName + "^^^" + pk);
        lockDO.setStatus(LockStatus.Locked.getCode());
        return lockDO;
    }

    // ==================== Unit Tests with Mock ====================

    @Test
    public void testConstructor_WithNullDataSource() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");

            assertThrows(StoreException.class, () -> new LockStoreDataBaseDAO(null));
        }
    }

    @Test
    public void testConstructor_WithBlankDbType() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("");

            DataSource dataSource = mock(DataSource.class);

            assertThrows(StoreException.class, () -> new LockStoreDataBaseDAO(dataSource));
        }
    }

    @Test
    public void testAcquireLock_Single_Success() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(false, false, null);
        LockDO lockDO = createLockDO("test-xid", 1L, 1L, "resource", "table", "1");

        boolean result = dao.acquireLock(lockDO);

        assertTrue(result);
    }

    @Test
    public void testAcquireLock_Single_Conflict() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(true, false, "other-xid");
        LockDO lockDO = createLockDO("test-xid", 1L, 1L, "resource", "table", "1");

        boolean result = dao.acquireLock(lockDO);

        assertFalse(result);
    }

    @Test
    public void testAcquireLock_List_Success() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(false, false, null);
        List<LockDO> lockDOs = Arrays.asList(
                createLockDO("test-xid", 1L, 1L, "resource", "table", "1"),
                createLockDO("test-xid", 1L, 1L, "resource", "table", "2"));

        boolean result = dao.acquireLock(lockDOs);

        assertTrue(result);
    }

    @Test
    public void testAcquireLock_List_WithDuplicates() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(false, false, null);
        List<LockDO> lockDOs = Arrays.asList(
                createLockDO("test-xid", 1L, 1L, "resource", "table", "1"),
                createLockDO("test-xid", 1L, 1L, "resource", "table", "1")); // duplicate

        boolean result = dao.acquireLock(lockDOs);

        assertTrue(result);
    }

    @Test
    public void testAcquireLock_List_Conflict() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(true, false, "other-xid");
        List<LockDO> lockDOs = Arrays.asList(createLockDO("test-xid", 1L, 1L, "resource", "table", "1"));

        boolean result = dao.acquireLock(lockDOs);

        assertFalse(result);
    }

    @Test
    public void testAcquireLock_WithSkipCheckLock() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(false, false, null);
        List<LockDO> lockDOs = Arrays.asList(createLockDO("test-xid", 1L, 1L, "resource", "table", "1"));

        boolean result = dao.acquireLock(lockDOs, true, true);

        assertTrue(result);
    }

    @Test
    public void testAcquireLock_WithFailFast() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(true, true, "other-xid");
        List<LockDO> lockDOs = Arrays.asList(createLockDO("test-xid", 1L, 1L, "resource", "table", "1"));

        assertThrows(StoreException.class, () -> dao.acquireLock(lockDOs, false, false));
    }

    @Test
    public void testAcquireLock_SQLException() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOWithException();
        LockDO lockDO = createLockDO("test-xid", 1L, 1L, "resource", "table", "1");

        assertThrows(StoreException.class, () -> dao.acquireLock(lockDO));
    }

    @Test
    public void testAcquireLock_ExistingLockSameXid() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(true, false, "test-xid");
        List<LockDO> lockDOs = Arrays.asList(createLockDO("test-xid", 1L, 1L, "resource", "table", "1"));

        boolean result = dao.acquireLock(lockDOs);

        assertTrue(result);
    }

    @Test
    public void testAcquireLock_EmptyList() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(false, false, null);
        List<LockDO> lockDOs = Collections.emptyList();

        boolean result = dao.acquireLock(lockDOs);

        assertTrue(result);
    }

    @Test
    public void testUnLock_Single() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOForUnlock();
        LockDO lockDO = createLockDO("test-xid", 1L, 1L, "resource", "table", "1");

        boolean result = dao.unLock(lockDO);

        assertTrue(result);
    }

    @Test
    public void testUnLock_List() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOForUnlock();
        List<LockDO> lockDOs = Arrays.asList(
                createLockDO("test-xid", 1L, 1L, "resource", "table", "1"),
                createLockDO("test-xid", 1L, 1L, "resource", "table", "2"));

        boolean result = dao.unLock(lockDOs);

        assertTrue(result);
    }

    @Test
    public void testUnLock_ByXid() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOForUnlockByXid();

        boolean result = dao.unLock("test-xid");

        assertTrue(result);
    }

    @Test
    public void testUnLock_ByBranchId() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOForUnlockByBranchId();

        boolean result = dao.unLock(100L);

        assertTrue(result);
    }

    @Test
    public void testUnLock_SQLException() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOWithException();

        assertThrows(StoreException.class, () -> dao.unLock("test-xid"));
    }

    @Test
    public void testIsLockable_True() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(false, false, null);
        List<LockDO> lockDOs = Arrays.asList(createLockDO("test-xid", 1L, 1L, "resource", "table", "1"));

        boolean result = dao.isLockable(lockDOs);

        assertTrue(result);
    }

    @Test
    public void testIsLockable_False() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(true, false, "other-xid");
        List<LockDO> lockDOs = Arrays.asList(createLockDO("test-xid", 1L, 1L, "resource", "table", "1"));

        boolean result = dao.isLockable(lockDOs);

        assertFalse(result);
    }

    @Test
    public void testIsLockable_SameXid() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(true, false, "test-xid");
        List<LockDO> lockDOs = Arrays.asList(createLockDO("test-xid", 1L, 1L, "resource", "table", "1"));

        boolean result = dao.isLockable(lockDOs);

        assertTrue(result);
    }

    @Test
    public void testIsLockable_SQLException() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOWithException();
        List<LockDO> lockDOs = Arrays.asList(createLockDO("test-xid", 1L, 1L, "resource", "table", "1"));

        assertThrows(DataAccessException.class, () -> dao.isLockable(lockDOs));
    }

    @Test
    public void testUpdateLockStatus_Success() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOForUpdateStatus();

        dao.updateLockStatus("test-xid", LockStatus.Rollbacking);
    }

    @Test
    public void testUpdateLockStatus_SQLException() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOWithException();

        assertThrows(DataAccessException.class, () -> dao.updateLockStatus("test-xid", LockStatus.Rollbacking));
    }

    @Test
    public void testAcquireLock_WithIntegrityConstraintViolation() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOWithConstraintViolation();
        LockDO lockDO = createLockDO("test-xid", 1L, 1L, "resource", "table", "1");

        boolean result = dao.acquireLock(lockDO);

        assertFalse(result);
    }

    @Test
    public void testAcquireLock_BatchWithIntegrityConstraintViolation() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOWithBatchConstraintViolation();
        List<LockDO> lockDOs = Arrays.asList(
                createLockDO("test-xid", 1L, 1L, "resource", "table", "1"),
                createLockDO("test-xid", 1L, 1L, "resource", "table", "2"));

        boolean result = dao.acquireLock(lockDOs, true, true);

        assertFalse(result);
    }

    @Test
    public void testAcquireLock_BatchPartialFailure() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAOWithBatchPartialFailure();
        List<LockDO> lockDOs = Arrays.asList(
                createLockDO("test-xid", 1L, 1L, "resource", "table", "1"),
                createLockDO("test-xid", 1L, 1L, "resource", "table", "2"));

        boolean result = dao.acquireLock(lockDOs, true, true);

        assertFalse(result);
    }

    @Test
    public void testAcquireLock_WithAutoCommitFalse() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(false, false, null);
        List<LockDO> lockDOs = Arrays.asList(createLockDO("test-xid", 1L, 1L, "resource", "table", "1"));

        boolean result = dao.acquireLock(lockDOs, false, false);

        assertTrue(result);
    }

    @Test
    public void testAcquireLock_AllLocksAlreadyExist() throws Exception {
        LockStoreDataBaseDAO dao = createMockedDAO(true, false, "test-xid");
        List<LockDO> lockDOs = Arrays.asList(createLockDO("test-xid", 1L, 1L, "resource", "table", "1"));

        boolean result = dao.acquireLock(lockDOs);

        assertTrue(result);
    }

    // ==================== Helper Methods ====================

    private LockStoreDataBaseDAO createMockedDAO(boolean hasExistingLock, boolean isRollbacking, String existingXid)
            throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<LockStoreSqlFactory> sqlFactoryMock = Mockito.mockStatic(LockStoreSqlFactory.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");

            LockStoreSql lockSql = mock(LockStoreSql.class);
            when(lockSql.getCheckLockableSql(anyString(), anyInt()))
                    .thenReturn("SELECT * FROM lock_table WHERE row_key IN (?)");
            when(lockSql.getInsertLockSQL(anyString())).thenReturn("INSERT INTO lock_table VALUES (?,?,?,?,?,?,?,?)");
            sqlFactoryMock
                    .when(() -> LockStoreSqlFactory.getLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement checkPst = mock(PreparedStatement.class);
            PreparedStatement insertPst = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(checkPst, insertPst);
            when(connection.getAutoCommit()).thenReturn(true);
            doNothing().when(connection).setAutoCommit(false);
            doNothing().when(connection).commit();
            doNothing().when(connection).rollback();

            when(checkPst.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(hasExistingLock);
            if (hasExistingLock) {
                when(resultSet.getString(ServerTableColumnsName.LOCK_TABLE_XID)).thenReturn(existingXid);
                when(resultSet.getString(ServerTableColumnsName.LOCK_TABLE_ROW_KEY))
                        .thenReturn("resource^^^table^^^1");
                if (isRollbacking) {
                    when(resultSet.getInt(ServerTableColumnsName.LOCK_TABLE_STATUS))
                            .thenReturn(LockStatus.Rollbacking.getCode());
                } else {
                    when(resultSet.getInt(ServerTableColumnsName.LOCK_TABLE_STATUS))
                            .thenReturn(LockStatus.Locked.getCode());
                }
            }

            when(insertPst.executeUpdate()).thenReturn(1);
            when(insertPst.executeBatch()).thenReturn(new int[] {1, 1});

            return new LockStoreDataBaseDAO(dataSource);
        }
    }

    private LockStoreDataBaseDAO createMockedDAOWithException() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<LockStoreSqlFactory> sqlFactoryMock = Mockito.mockStatic(LockStoreSqlFactory.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");

            LockStoreSql lockSql = mock(LockStoreSql.class);
            when(lockSql.getCheckLockableSql(anyString(), anyInt()))
                    .thenReturn("SELECT * FROM lock_table WHERE row_key IN (?)");
            when(lockSql.getInsertLockSQL(anyString())).thenReturn("INSERT INTO lock_table VALUES (?,?,?,?,?,?,?,?)");
            when(lockSql.getBatchDeleteLockSql(anyString(), anyInt()))
                    .thenReturn("DELETE FROM lock_table WHERE xid = ? AND row_key IN (?)");
            when(lockSql.getBatchDeleteLockSqlByXid(anyString())).thenReturn("DELETE FROM lock_table WHERE xid = ?");
            when(lockSql.getBatchDeleteLockSqlByBranchId(anyString()))
                    .thenReturn("DELETE FROM lock_table WHERE branch_id = ?");
            when(lockSql.getBatchUpdateStatusLockByGlobalSql(anyString()))
                    .thenReturn("UPDATE lock_table SET status = ? WHERE xid = ?");
            sqlFactoryMock
                    .when(() -> LockStoreSqlFactory.getLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(connection.getAutoCommit()).thenReturn(true);
            when(preparedStatement.executeQuery()).thenThrow(new SQLException("Database error"));
            when(preparedStatement.executeUpdate()).thenThrow(new SQLException("Database error"));

            return new LockStoreDataBaseDAO(dataSource);
        }
    }

    private LockStoreDataBaseDAO createMockedDAOForUnlock() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<LockStoreSqlFactory> sqlFactoryMock = Mockito.mockStatic(LockStoreSqlFactory.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");

            LockStoreSql lockSql = mock(LockStoreSql.class);
            when(lockSql.getBatchDeleteLockSql(anyString(), anyInt()))
                    .thenReturn("DELETE FROM lock_table WHERE xid = ? AND row_key IN (?)");
            sqlFactoryMock
                    .when(() -> LockStoreSqlFactory.getLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            return new LockStoreDataBaseDAO(dataSource);
        }
    }

    private LockStoreDataBaseDAO createMockedDAOForUnlockByXid() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<LockStoreSqlFactory> sqlFactoryMock = Mockito.mockStatic(LockStoreSqlFactory.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");

            LockStoreSql lockSql = mock(LockStoreSql.class);
            when(lockSql.getBatchDeleteLockSqlByXid(anyString())).thenReturn("DELETE FROM lock_table WHERE xid = ?");
            sqlFactoryMock
                    .when(() -> LockStoreSqlFactory.getLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            return new LockStoreDataBaseDAO(dataSource);
        }
    }

    private LockStoreDataBaseDAO createMockedDAOForUnlockByBranchId() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<LockStoreSqlFactory> sqlFactoryMock = Mockito.mockStatic(LockStoreSqlFactory.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");

            LockStoreSql lockSql = mock(LockStoreSql.class);
            when(lockSql.getBatchDeleteLockSqlByBranchId(anyString()))
                    .thenReturn("DELETE FROM lock_table WHERE branch_id = ?");
            sqlFactoryMock
                    .when(() -> LockStoreSqlFactory.getLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            return new LockStoreDataBaseDAO(dataSource);
        }
    }

    private LockStoreDataBaseDAO createMockedDAOForUpdateStatus() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<LockStoreSqlFactory> sqlFactoryMock = Mockito.mockStatic(LockStoreSqlFactory.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");

            LockStoreSql lockSql = mock(LockStoreSql.class);
            when(lockSql.getBatchUpdateStatusLockByGlobalSql(anyString()))
                    .thenReturn("UPDATE lock_table SET status = ? WHERE xid = ?");
            sqlFactoryMock
                    .when(() -> LockStoreSqlFactory.getLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            return new LockStoreDataBaseDAO(dataSource);
        }
    }

    private LockStoreDataBaseDAO createMockedDAOWithConstraintViolation() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<LockStoreSqlFactory> sqlFactoryMock = Mockito.mockStatic(LockStoreSqlFactory.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");

            LockStoreSql lockSql = mock(LockStoreSql.class);
            when(lockSql.getCheckLockableSql(anyString(), anyInt()))
                    .thenReturn("SELECT * FROM lock_table WHERE row_key IN (?)");
            when(lockSql.getInsertLockSQL(anyString())).thenReturn("INSERT INTO lock_table VALUES (?,?,?,?,?,?,?,?)");
            sqlFactoryMock
                    .when(() -> LockStoreSqlFactory.getLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement checkPst = mock(PreparedStatement.class);
            PreparedStatement insertPst = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(checkPst, insertPst);
            when(connection.getAutoCommit()).thenReturn(true);
            doNothing().when(connection).setAutoCommit(false);
            doNothing().when(connection).commit();
            doNothing().when(connection).rollback();

            when(checkPst.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            when(insertPst.executeUpdate()).thenThrow(new SQLIntegrityConstraintViolationException("Duplicate entry"));

            return new LockStoreDataBaseDAO(dataSource);
        }
    }

    private LockStoreDataBaseDAO createMockedDAOWithBatchConstraintViolation() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<LockStoreSqlFactory> sqlFactoryMock = Mockito.mockStatic(LockStoreSqlFactory.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");

            LockStoreSql lockSql = mock(LockStoreSql.class);
            when(lockSql.getInsertLockSQL(anyString())).thenReturn("INSERT INTO lock_table VALUES (?,?,?,?,?,?,?,?)");
            sqlFactoryMock
                    .when(() -> LockStoreSqlFactory.getLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement insertPst = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(insertPst);
            when(connection.getAutoCommit()).thenReturn(true);
            doNothing().when(connection).setAutoCommit(false);
            doNothing().when(connection).commit();
            doNothing().when(connection).rollback();

            when(insertPst.executeBatch()).thenThrow(new SQLIntegrityConstraintViolationException("Duplicate entry"));

            return new LockStoreDataBaseDAO(dataSource);
        }
    }

    private LockStoreDataBaseDAO createMockedDAOWithBatchPartialFailure() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<LockStoreSqlFactory> sqlFactoryMock = Mockito.mockStatic(LockStoreSqlFactory.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("lock_table");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");

            LockStoreSql lockSql = mock(LockStoreSql.class);
            when(lockSql.getInsertLockSQL(anyString())).thenReturn("INSERT INTO lock_table VALUES (?,?,?,?,?,?,?,?)");
            sqlFactoryMock
                    .when(() -> LockStoreSqlFactory.getLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement insertPst = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(insertPst);
            when(connection.getAutoCommit()).thenReturn(true);
            doNothing().when(connection).setAutoCommit(false);
            doNothing().when(connection).commit();
            doNothing().when(connection).rollback();

            BatchUpdateException batchException = new BatchUpdateException(
                    "Batch update exception",
                    new int[] {1, 0},
                    new SQLIntegrityConstraintViolationException("Duplicate entry"));
            when(insertPst.executeBatch()).thenThrow(batchException);

            return new LockStoreDataBaseDAO(dataSource);
        }
    }
}
