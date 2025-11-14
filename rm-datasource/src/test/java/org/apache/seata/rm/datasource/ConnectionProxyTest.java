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
package org.apache.seata.rm.datasource;

import org.apache.seata.common.LockStrategyMode;
import org.apache.seata.core.context.GlobalLockConfigHolder;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.exception.TransactionExceptionCode;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.model.GlobalLockConfig;
import org.apache.seata.rm.DefaultResourceManager;
import org.apache.seata.rm.datasource.ConnectionProxy.LockRetryPolicy;
import org.apache.seata.rm.datasource.exec.LockWaitTimeoutException;
import org.apache.seata.rm.datasource.mock.MockConnection;
import org.apache.seata.rm.datasource.mock.MockDriver;
import org.apache.seata.rm.datasource.sql.struct.Row;
import org.apache.seata.rm.datasource.sql.struct.TableRecords;
import org.apache.seata.rm.datasource.undo.SQLUndoLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * ConnectionProxy test
 *
 */
@EnabledOnJre({JRE.JAVA_8, JRE.JAVA_11
}) // `ReflectionUtil.modifyStaticFinalField` does not supported java17 and above versions
public class ConnectionProxyTest {
    private DataSourceProxy dataSourceProxy;

    private static final String TEST_RESOURCE_ID = "testResourceId";

    private static final String TEST_XID = "testXid";

    private static final String lockKey = "order:123";

    private static final String DB_TYPE = "mysql";

    private Field branchRollbackFlagField;

    @BeforeEach
    public void initBeforeEach() throws Exception {
        branchRollbackFlagField =
                LockRetryPolicy.class.getDeclaredField("LOCK_RETRY_POLICY_BRANCH_ROLLBACK_ON_CONFLICT");
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(branchRollbackFlagField, branchRollbackFlagField.getModifiers() & ~Modifier.FINAL);
        branchRollbackFlagField.setAccessible(true);
        boolean branchRollbackFlag = (boolean) branchRollbackFlagField.get(null);
        Assertions.assertTrue(branchRollbackFlag);

        dataSourceProxy = Mockito.mock(DataSourceProxy.class);
        Mockito.when(dataSourceProxy.getResourceId()).thenReturn(TEST_RESOURCE_ID);
        Mockito.when(dataSourceProxy.getDbType()).thenReturn(DB_TYPE);
        DefaultResourceManager rm = Mockito.mock(DefaultResourceManager.class);

        Mockito.when(rm.branchRegister(
                        BranchType.AT,
                        dataSourceProxy.getResourceId(),
                        null,
                        TEST_XID,
                        "{\"autoCommit\":false}",
                        lockKey))
                .thenThrow(new TransactionException(TransactionExceptionCode.LockKeyConflict));
        DefaultResourceManager defaultResourceManager = DefaultResourceManager.get();
        Assertions.assertNotNull(defaultResourceManager);
        DefaultResourceManager.mockResourceManager(BranchType.AT, rm);
    }

    @Test
    public void testLockRetryPolicyRollbackOnConflict() throws Exception {
        boolean oldBranchRollbackFlag = (boolean) branchRollbackFlagField.get(null);
        branchRollbackFlagField.set(null, true);
        GlobalLockConfig preGlobalLockConfig = new GlobalLockConfig();
        preGlobalLockConfig.setLockRetryTimes(0);
        preGlobalLockConfig.setLockRetryInterval(10);
        preGlobalLockConfig.setLockStrategyMode(LockStrategyMode.PESSIMISTIC);
        GlobalLockConfig globalLockConfig = GlobalLockConfigHolder.setAndReturnPrevious(preGlobalLockConfig);
        ConnectionProxy connectionProxy =
                new ConnectionProxy(dataSourceProxy, new MockConnection(new MockDriver(), "", null));
        connectionProxy.bind(TEST_XID);
        SQLUndoLog sqlUndoLog = new SQLUndoLog();
        TableRecords beforeImage = new TableRecords();
        beforeImage.add(new Row());
        sqlUndoLog.setBeforeImage(beforeImage);
        connectionProxy.getContext().appendUndoItem(sqlUndoLog);
        connectionProxy.appendUndoLog(new SQLUndoLog());
        connectionProxy.appendLockKey(lockKey);
        Assertions.assertThrows(LockWaitTimeoutException.class, connectionProxy::commit);
        branchRollbackFlagField.set(null, oldBranchRollbackFlag);
    }

    @Test
    public void testLockRetryPolicyNotRollbackOnConflict() throws Exception {
        boolean oldBranchRollbackFlag = (boolean) branchRollbackFlagField.get(null);
        branchRollbackFlagField.set(null, false);
        GlobalLockConfig preGlobalLockConfig = new GlobalLockConfig();
        preGlobalLockConfig.setLockRetryTimes(30);
        preGlobalLockConfig.setLockRetryInterval(10);
        preGlobalLockConfig.setLockStrategyMode(LockStrategyMode.PESSIMISTIC);
        GlobalLockConfig globalLockConfig = GlobalLockConfigHolder.setAndReturnPrevious(preGlobalLockConfig);
        ConnectionProxy connectionProxy = new ConnectionProxy(dataSourceProxy, null);
        connectionProxy.bind(TEST_XID);
        connectionProxy.appendUndoLog(new SQLUndoLog());
        connectionProxy.appendLockKey(lockKey);
        SQLUndoLog sqlUndoLog = new SQLUndoLog();
        TableRecords beforeImage = new TableRecords();
        beforeImage.add(new Row());
        sqlUndoLog.setBeforeImage(beforeImage);
        connectionProxy.getContext().appendUndoItem(sqlUndoLog);
        Assertions.assertThrows(LockWaitTimeoutException.class, connectionProxy::commit);
        branchRollbackFlagField.set(null, oldBranchRollbackFlag);
    }

    @Test
    public void testGetContext() {
        ConnectionProxy connectionProxy =
                new ConnectionProxy(dataSourceProxy, new MockConnection(new MockDriver(), "", null));
        Assertions.assertNotNull(connectionProxy.getContext());
    }

    @Test
    public void testBindXid() {
        ConnectionProxy connectionProxy =
                new ConnectionProxy(dataSourceProxy, new MockConnection(new MockDriver(), "", null));
        connectionProxy.bind(TEST_XID);
        Assertions.assertEquals(TEST_XID, connectionProxy.getContext().getXid());
    }

    @Test
    public void testSetGlobalLockRequire() {
        ConnectionProxy connectionProxy =
                new ConnectionProxy(dataSourceProxy, new MockConnection(new MockDriver(), "", null));
        connectionProxy.setGlobalLockRequire(true);
        Assertions.assertTrue(connectionProxy.isGlobalLockRequire());
        connectionProxy.setGlobalLockRequire(false);
        Assertions.assertFalse(connectionProxy.isGlobalLockRequire());
    }

    @Test
    public void testAppendUndoLog() {
        ConnectionProxy connectionProxy =
                new ConnectionProxy(dataSourceProxy, new MockConnection(new MockDriver(), "", null));
        SQLUndoLog undoLog = new SQLUndoLog();
        connectionProxy.appendUndoLog(undoLog);
        Assertions.assertEquals(1, connectionProxy.getContext().getUndoItems().size());
    }

    @Test
    public void testAppendLockKey() {
        ConnectionProxy connectionProxy =
                new ConnectionProxy(dataSourceProxy, new MockConnection(new MockDriver(), "", null));
        connectionProxy.appendLockKey("test:1");
        connectionProxy.appendLockKey("test:2");
        Assertions.assertNotNull(connectionProxy.getContext());
    }

    @Test
    public void testGetTargetConnection() {
        MockConnection mockConnection = new MockConnection(new MockDriver(), "", null);
        ConnectionProxy connectionProxy = new ConnectionProxy(dataSourceProxy, mockConnection);
        Assertions.assertEquals(mockConnection, connectionProxy.getTargetConnection());
    }

    @Test
    public void testGetDataSourceProxy() {
        ConnectionProxy connectionProxy =
                new ConnectionProxy(dataSourceProxy, new MockConnection(new MockDriver(), "", null));
        Assertions.assertEquals(dataSourceProxy, connectionProxy.getDataSourceProxy());
    }

    @Test
    public void testCheckLockWithBlankLockKeys() throws Exception {
        ConnectionProxy connectionProxy =
                new ConnectionProxy(dataSourceProxy, new MockConnection(new MockDriver(), "", null));
        connectionProxy.bind(TEST_XID);
        connectionProxy.checkLock("");
        connectionProxy.checkLock(null);
    }

    @Test
    public void testLockQueryWithBlankLockKeys() throws Exception {
        ConnectionProxy connectionProxy =
                new ConnectionProxy(dataSourceProxy, new MockConnection(new MockDriver(), "", null));
        connectionProxy.bind(TEST_XID);
        boolean result = connectionProxy.lockQuery("");
        Assertions.assertFalse(result);
    }
}
