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

import org.apache.seata.common.exception.ShouldNeverHappenException;
import org.apache.seata.common.loader.EnhancedServiceLoader;
import org.apache.seata.config.Configuration;
import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.core.constants.ConfigurationKeys;
import org.apache.seata.core.store.DistributedLockDO;
import org.apache.seata.core.store.DistributedLocker;
import org.apache.seata.core.store.db.DataSourceProvider;
import org.apache.seata.core.store.db.sql.distributed.lock.DistributedLockSql;
import org.apache.seata.core.store.db.sql.distributed.lock.DistributedLockSqlFactory;
import org.apache.seata.server.BaseSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "dbCaseEnabled", matches = "true")
public class DataBaseDistributedLockerTest extends BaseSpringBootTest {

    private DataBaseDistributedLocker dataBaseDistributedLocker;

    @BeforeEach
    public void setUp() {
        dataBaseDistributedLocker = new DataBaseDistributedLocker();
    }

    @AfterEach
    public void tearDown() {
        // Clean up test locks
        try {
            DistributedLockDO cleanupLock = new DistributedLockDO();
            cleanupLock.setLockKey("test-key");
            cleanupLock.setLockValue("");
            cleanupLock.setExpireTime(0L);
            dataBaseDistributedLocker.releaseLock(cleanupLock);

            cleanupLock.setLockKey("test-key-2");
            dataBaseDistributedLocker.releaseLock(cleanupLock);

            cleanupLock.setLockKey("test-key-expired");
            dataBaseDistributedLocker.releaseLock(cleanupLock);

            cleanupLock.setLockKey("test-key-conflict");
            dataBaseDistributedLocker.releaseLock(cleanupLock);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    public void testInstanceCreation() {
        Assertions.assertNotNull(dataBaseDistributedLocker);
    }

    @Test
    public void testImplementsDistributedLocker() {
        Assertions.assertTrue(dataBaseDistributedLocker instanceof DistributedLocker);
    }

    @Test
    public void testAcquireAndReleaseLock() {
        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");
        lockDO.setLockValue("test-value");
        lockDO.setExpireTime(60000L);

        boolean acquired = dataBaseDistributedLocker.acquireLock(lockDO);
        Assertions.assertTrue(acquired, "Lock acquisition should succeed");

        boolean released = dataBaseDistributedLocker.releaseLock(lockDO);
        Assertions.assertTrue(released, "Lock release should succeed");
    }

    @Test
    public void testAcquireLockConflict() {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key-conflict");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(60000L);

        // First acquisition should succeed
        boolean firstAcquired = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(firstAcquired, "First lock acquisition should succeed");

        // Second acquisition with different value should fail
        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key-conflict");
        lockDO2.setLockValue("holder-2");
        lockDO2.setExpireTime(60000L);

        boolean secondAcquired = dataBaseDistributedLocker.acquireLock(lockDO2);
        Assertions.assertFalse(secondAcquired, "Conflicting lock acquisition should fail");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO1);
    }

    @Test
    public void testAcquireLockAfterExpired() throws InterruptedException {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key-expired");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(100L); // 100ms expiration

        // First acquisition should succeed
        boolean firstAcquired = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(firstAcquired, "First lock acquisition should succeed");

        // Wait for lock to expire
        Thread.sleep(150);

        // Second acquisition should succeed after expiration
        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key-expired");
        lockDO2.setLockValue("holder-2");
        lockDO2.setExpireTime(60000L);

        boolean secondAcquired = dataBaseDistributedLocker.acquireLock(lockDO2);
        Assertions.assertTrue(secondAcquired, "Lock acquisition should succeed after expiration");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO2);
    }

    @Test
    public void testReleaseLockNotOwned() {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key-2");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(60000L);

        // Acquire lock with holder-1
        boolean acquired = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(acquired, "Lock acquisition should succeed");

        // Try to release with different holder
        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key-2");
        lockDO2.setLockValue("holder-2");
        lockDO2.setExpireTime(60000L);

        boolean released = dataBaseDistributedLocker.releaseLock(lockDO2);
        // Release should succeed but not actually release (as per implementation)
        Assertions.assertTrue(released, "Release should return true even if not owned");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO1);
    }

    @Test
    public void testAcquireLockWithZeroExpireTime() {
        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");
        lockDO.setLockValue("test-value");
        lockDO.setExpireTime(0L);

        boolean acquired = dataBaseDistributedLocker.acquireLock(lockDO);
        Assertions.assertTrue(acquired, "Lock acquisition with zero expire time should succeed");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO);
    }

    @Test
    public void testReacquireSameLock() {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(60000L);

        // First acquisition
        boolean firstAcquired = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(firstAcquired, "First acquisition should succeed");

        // Try to reacquire same lock with same holder - should fail due to conflict
        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key");
        lockDO2.setLockValue("holder-1");
        lockDO2.setExpireTime(60000L);

        boolean secondAcquired = dataBaseDistributedLocker.acquireLock(lockDO2);
        Assertions.assertFalse(secondAcquired, "Reacquisition should fail while lock is held");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO1);
    }

    @Test
    public void testAcquireLockAfterRelease() {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(60000L);

        // Acquire and release
        boolean acquired1 = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(acquired1, "First acquisition should succeed");

        boolean released = dataBaseDistributedLocker.releaseLock(lockDO1);
        Assertions.assertTrue(released, "Release should succeed");

        // Acquire again with different holder
        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key");
        lockDO2.setLockValue("holder-2");
        lockDO2.setExpireTime(60000L);

        boolean acquired2 = dataBaseDistributedLocker.acquireLock(lockDO2);
        Assertions.assertTrue(acquired2, "Acquisition after release should succeed");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO2);
    }

    @Test
    public void testMultipleLockKeys() {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(60000L);

        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key-2");
        lockDO2.setLockValue("holder-2");
        lockDO2.setExpireTime(60000L);

        // Both locks should succeed (different keys)
        boolean acquired1 = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(acquired1, "First lock should succeed");

        boolean acquired2 = dataBaseDistributedLocker.acquireLock(lockDO2);
        Assertions.assertTrue(acquired2, "Second lock with different key should succeed");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO1);
        dataBaseDistributedLocker.releaseLock(lockDO2);
    }

    // ==================== Unit Tests with Mock ====================

    @Test
    public void testAcquireLock_WithDemotion() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerInDemotion();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");

        boolean result = locker.acquireLock(lockDO);

        assertTrue(result);
    }

    @Test
    public void testAcquireLock_Success_FirstInsert() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerWithConnection();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");
        lockDO.setLockValue("test-value");
        lockDO.setExpireTime(60000L);

        boolean result = locker.acquireLock(lockDO);

        assertTrue(result);
    }

    @Test
    public void testAcquireLock_LockHeldByOther() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerWithLockHeld();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");
        lockDO.setLockValue("test-value");
        lockDO.setExpireTime(60000L);

        boolean result = locker.acquireLock(lockDO);

        assertFalse(result);
    }

    @Test
    public void testAcquireLock_LockExpired() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerWithExpiredLock();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");
        lockDO.setLockValue("test-value");
        lockDO.setExpireTime(60000L);

        boolean result = locker.acquireLock(lockDO);

        assertTrue(result);
    }

    @Test
    public void testAcquireLock_SQLException() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerWithException();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");

        boolean result = locker.acquireLock(lockDO);

        assertFalse(result);
    }

    @Test
    public void testAcquireLock_SQLExceptionIgnored() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerWithIgnoredException();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");

        boolean result = locker.acquireLock(lockDO);

        assertFalse(result);
    }

    @Test
    public void testReleaseLock_WithDemotion() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerInDemotion();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");

        boolean result = locker.releaseLock(lockDO);

        assertTrue(result);
    }

    @Test
    public void testReleaseLock_Success() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerForRelease();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");
        lockDO.setLockValue("test-value");

        boolean result = locker.releaseLock(lockDO);

        assertTrue(result);
    }

    @Test
    public void testReleaseLock_LockNotExists() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerForReleaseNonExistent();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");

        assertThrows(ShouldNeverHappenException.class, () -> locker.releaseLock(lockDO));
    }

    @Test
    public void testReleaseLock_LockHeldByOther() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerForReleaseHeldByOther();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");
        lockDO.setLockValue("other-value");

        boolean result = locker.releaseLock(lockDO);

        assertTrue(result);
    }

    @Test
    public void testReleaseLock_SQLException() throws Exception {
        DataBaseDistributedLocker locker = createMockedLockerWithException();

        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");

        boolean result = locker.releaseLock(lockDO);

        assertFalse(result);
    }

    // ==================== Helper Methods ====================

    private DataBaseDistributedLocker createMockedLockerInDemotion() throws Exception {
        DataBaseDistributedLocker locker = new DataBaseDistributedLocker();
        Field demotionField = DataBaseDistributedLocker.class.getDeclaredField("demotion");
        demotionField.setAccessible(true);
        demotionField.set(locker, true);
        return locker;
    }

    private DataBaseDistributedLocker createMockedLockerWithConnection() throws Exception {
        return createMockedLocker(getDataSourceWithConnection(false, false, System.currentTimeMillis() + 60000));
    }

    private DataBaseDistributedLocker createMockedLockerWithLockHeld() throws Exception {
        return createMockedLocker(getDataSourceWithConnection(true, false, System.currentTimeMillis() + 60000));
    }

    private DataBaseDistributedLocker createMockedLockerWithExpiredLock() throws Exception {
        return createMockedLocker(getDataSourceWithConnection(true, false, System.currentTimeMillis() - 60000));
    }

    private DataBaseDistributedLocker createMockedLockerWithException() throws Exception {
        return createMockedLocker(getDataSourceWithConnection(true, true, 0L));
    }

    private DataBaseDistributedLocker createMockedLockerWithIgnoredException() throws Exception {
        try (MockedStatic<DistributedLockSqlFactory> sqlFactoryMock =
                Mockito.mockStatic(DistributedLockSqlFactory.class)) {
            DistributedLockSql lockSql = mock(DistributedLockSql.class);
            when(lockSql.getSelectDistributeForUpdateSql(anyString()))
                    .thenReturn("SELECT * FROM distributed_lock WHERE lock_key = ? FOR UPDATE");

            sqlFactoryMock
                    .when(() -> DistributedLockSqlFactory.getDistributedLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenThrow(createMySQLTimeoutException());

            return createMockedLocker(dataSource);
        }
    }

    private DataBaseDistributedLocker createMockedLockerForRelease() throws Exception {
        return createMockedLocker(getDataSourceWithConnection(true, false, System.currentTimeMillis() - 60000));
    }

    private DataBaseDistributedLocker createMockedLockerForReleaseNonExistent() throws Exception {
        return createMockedLocker(getDataSourceWithConnection(false, false, 0L));
    }

    private DataBaseDistributedLocker createMockedLockerForReleaseHeldByOther() throws Exception {
        return createMockedLocker(getDataSourceWithConnection(true, false, System.currentTimeMillis() + 60000));
    }

    private DataBaseDistributedLocker createMockedLocker(DataSource dataSource) throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<EnhancedServiceLoader> loaderMock = Mockito.mockStatic(EnhancedServiceLoader.class);
                MockedStatic<DistributedLockSqlFactory> sqlFactoryMock =
                        Mockito.mockStatic(DistributedLockSqlFactory.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(ConfigurationKeys.DISTRIBUTED_LOCK_DB_TABLE)).thenReturn("distributed_lock");
            when(config.getConfig(ConfigurationKeys.STORE_DB_TYPE)).thenReturn("mysql");
            when(config.getConfig(ConfigurationKeys.STORE_DB_DATASOURCE_TYPE)).thenReturn("druid");

            DataSourceProvider dataSourceProvider = mock(DataSourceProvider.class);
            when(dataSourceProvider.provide()).thenReturn(dataSource);
            loaderMock
                    .when(() -> EnhancedServiceLoader.load(DataSourceProvider.class, anyString()))
                    .thenReturn(dataSourceProvider);

            DistributedLockSql lockSql = mock(DistributedLockSql.class);
            when(lockSql.getSelectDistributeForUpdateSql(anyString()))
                    .thenReturn("SELECT * FROM distributed_lock WHERE lock_key = ? FOR UPDATE");
            when(lockSql.getInsertSql(anyString()))
                    .thenReturn("INSERT INTO distributed_lock (lock_key, lock_value, expire_time) VALUES (?, ?, ?)");
            when(lockSql.getUpdateSql(anyString()))
                    .thenReturn("UPDATE distributed_lock SET lock_value=?, expire_time=? WHERE lock_key=?");

            sqlFactoryMock
                    .when(() -> DistributedLockSqlFactory.getDistributedLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            return new DataBaseDistributedLocker();
        }
    }

    private DataSource getDataSourceWithConnection(boolean hasLock, boolean throwException, long expireTime)
            throws Exception {
        try (MockedStatic<DistributedLockSqlFactory> sqlFactoryMock =
                Mockito.mockStatic(DistributedLockSqlFactory.class)) {
            DistributedLockSql lockSql = mock(DistributedLockSql.class);
            when(lockSql.getSelectDistributeForUpdateSql(anyString()))
                    .thenReturn("SELECT * FROM distributed_lock WHERE lock_key = ? FOR UPDATE");
            when(lockSql.getInsertSql(anyString()))
                    .thenReturn("INSERT INTO distributed_lock (lock_key, lock_value, expire_time) VALUES (?, ?, ?)");
            when(lockSql.getUpdateSql(anyString()))
                    .thenReturn("UPDATE distributed_lock SET lock_value=?, expire_time=? WHERE lock_key=?");

            sqlFactoryMock
                    .when(() -> DistributedLockSqlFactory.getDistributedLogStoreSql(anyString()))
                    .thenReturn(lockSql);

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement selectPst = mock(PreparedStatement.class);
            PreparedStatement insertPst = mock(PreparedStatement.class);
            PreparedStatement updatePst = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(selectPst, insertPst, updatePst);
            when(connection.getAutoCommit()).thenReturn(true);
            doNothing().when(connection).setAutoCommit(false);
            doNothing().when(connection).commit();
            doNothing().when(connection).rollback();

            if (throwException && hasLock) {
                when(selectPst.executeQuery()).thenThrow(new SQLException("Database error"));
            } else {
                when(selectPst.executeQuery()).thenReturn(resultSet);
                when(resultSet.next()).thenReturn(hasLock);
                if (hasLock) {
                    when(resultSet.getLong(anyString())).thenReturn(expireTime);
                    when(resultSet.getString(anyString())).thenReturn("existing-value");
                }
            }

            if (!hasLock || expireTime < System.currentTimeMillis()) {
                when(insertPst.executeUpdate()).thenReturn(1);
                when(updatePst.executeUpdate()).thenReturn(1);
            }

            return dataSource;
        }
    }

    private SQLException createMySQLTimeoutException() {
        SQLException exception = new SQLException("Lock wait timeout exceeded; try restarting transaction");
        try {
            Field codeField = SQLException.class.getDeclaredField("vendorCode");
            codeField.setAccessible(true);
            codeField.set(exception, "1205");
        } catch (Exception e) {
            // Ignore
        }
        return exception;
    }
}
