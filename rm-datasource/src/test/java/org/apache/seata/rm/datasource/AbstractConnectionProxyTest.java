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

import org.apache.seata.core.context.RootContext;
import org.apache.seata.core.model.BranchType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class AbstractConnectionProxyTest {

    private DataSourceProxy dataSourceProxy;
    private Connection targetConnection;
    private TestConnectionProxy connectionProxy;
    private MockedStatic<RootContext> mockedRootContext;

    private static class TestConnectionProxy extends AbstractConnectionProxy {
        public TestConnectionProxy(DataSourceProxy dataSourceProxy, Connection targetConnection) {
            super(dataSourceProxy, targetConnection);
        }

        @Override
        public void commit() throws SQLException {
            targetConnection.commit();
        }

        @Override
        public void rollback() throws SQLException {
            targetConnection.rollback();
        }

        @Override
        public void rollback(Savepoint savepoint) throws SQLException {
            targetConnection.rollback(savepoint);
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            targetConnection.setAutoCommit(autoCommit);
        }

        @Override
        public Savepoint setSavepoint() throws SQLException {
            return targetConnection.setSavepoint();
        }

        @Override
        public Savepoint setSavepoint(String name) throws SQLException {
            return targetConnection.setSavepoint(name);
        }

        @Override
        public void releaseSavepoint(Savepoint savepoint) throws SQLException {
            targetConnection.releaseSavepoint(savepoint);
        }
    }

    @BeforeEach
    public void setUp() throws SQLException {
        dataSourceProxy = mock(DataSourceProxy.class);
        targetConnection = mock(Connection.class);
        when(dataSourceProxy.getDbType()).thenReturn("mysql");
        when(dataSourceProxy.getResourceId()).thenReturn("jdbc:mysql://localhost:3306/test");

        connectionProxy = new TestConnectionProxy(dataSourceProxy, targetConnection);

        mockedRootContext = Mockito.mockStatic(RootContext.class);
        mockedRootContext.when(RootContext::getBranchType).thenReturn(BranchType.TCC);
    }

    @AfterEach
    public void tearDown() {
        if (mockedRootContext != null) {
            mockedRootContext.close();
        }
    }

    @Test
    public void testConstructor() {
        Assertions.assertNotNull(connectionProxy);
        Assertions.assertEquals(dataSourceProxy, connectionProxy.getDataSourceProxy());
        Assertions.assertEquals(targetConnection, connectionProxy.getTargetConnection());
    }

    @Test
    public void testGetDataSourceProxy() {
        DataSourceProxy result = connectionProxy.getDataSourceProxy();
        Assertions.assertSame(dataSourceProxy, result);
    }

    @Test
    public void testGetTargetConnection() {
        Connection result = connectionProxy.getTargetConnection();
        Assertions.assertSame(targetConnection, result);
    }

    @Test
    public void testGetDbType() {
        String dbType = connectionProxy.getDbType();
        Assertions.assertEquals("mysql", dbType);
        verify(dataSourceProxy).getDbType();
    }

    @Test
    public void testCreateStatement() throws SQLException {
        Statement mockStatement = mock(Statement.class);
        when(targetConnection.createStatement()).thenReturn(mockStatement);

        Statement result = connectionProxy.createStatement();

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof StatementProxy);
        verify(targetConnection).createStatement();
    }

    @Test
    public void testCreateStatementWithParameters() throws SQLException {
        Statement mockStatement = mock(Statement.class);
        when(targetConnection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY))
                .thenReturn(mockStatement);

        Statement result = connectionProxy.createStatement(
                java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);

        Assertions.assertNotNull(result);
        verify(targetConnection)
                .createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
    }

    @Test
    public void testCreateStatementWithAllParameters() throws SQLException {
        Statement mockStatement = mock(Statement.class);
        when(targetConnection.createStatement(
                        java.sql.ResultSet.TYPE_FORWARD_ONLY,
                        java.sql.ResultSet.CONCUR_READ_ONLY,
                        java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT))
                .thenReturn(mockStatement);

        Statement result = connectionProxy.createStatement(
                java.sql.ResultSet.TYPE_FORWARD_ONLY,
                java.sql.ResultSet.CONCUR_READ_ONLY,
                java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT);

        Assertions.assertNotNull(result);
        verify(targetConnection)
                .createStatement(
                        java.sql.ResultSet.TYPE_FORWARD_ONLY,
                        java.sql.ResultSet.CONCUR_READ_ONLY,
                        java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT);
    }

    @Test
    public void testPrepareStatementSimple() throws SQLException {
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        when(targetConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

        PreparedStatement result = connectionProxy.prepareStatement("SELECT * FROM test");

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof PreparedStatementProxy);
        verify(targetConnection).prepareStatement("SELECT * FROM test");
    }

    @Test
    public void testPrepareStatementNotInATMode() throws SQLException {
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        when(targetConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

        mockedRootContext.when(RootContext::getBranchType).thenReturn(BranchType.TCC);

        PreparedStatement result = connectionProxy.prepareStatement("INSERT INTO test VALUES (1)");

        Assertions.assertNotNull(result);
        verify(targetConnection).prepareStatement("INSERT INTO test VALUES (1)");
    }

    @Test
    public void testPrepareStatementWithResultSetType() throws SQLException {
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        when(targetConnection.prepareStatement(
                        anyString(), eq(java.sql.ResultSet.TYPE_FORWARD_ONLY), eq(java.sql.ResultSet.CONCUR_READ_ONLY)))
                .thenReturn(mockPreparedStatement);

        PreparedStatement result = connectionProxy.prepareStatement(
                "SELECT * FROM test", java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);

        Assertions.assertNotNull(result);
        verify(targetConnection)
                .prepareStatement(
                        "SELECT * FROM test",
                        java.sql.ResultSet.TYPE_FORWARD_ONLY,
                        java.sql.ResultSet.CONCUR_READ_ONLY);
    }

    @Test
    public void testPrepareStatementWithAllResultSetParameters() throws SQLException {
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        when(targetConnection.prepareStatement(
                        anyString(),
                        eq(java.sql.ResultSet.TYPE_FORWARD_ONLY),
                        eq(java.sql.ResultSet.CONCUR_READ_ONLY),
                        eq(java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT)))
                .thenReturn(mockPreparedStatement);

        PreparedStatement result = connectionProxy.prepareStatement(
                "SELECT * FROM test",
                java.sql.ResultSet.TYPE_FORWARD_ONLY,
                java.sql.ResultSet.CONCUR_READ_ONLY,
                java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT);

        Assertions.assertNotNull(result);
        verify(targetConnection)
                .prepareStatement(
                        anyString(),
                        eq(java.sql.ResultSet.TYPE_FORWARD_ONLY),
                        eq(java.sql.ResultSet.CONCUR_READ_ONLY),
                        eq(java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT));
    }

    @Test
    public void testPrepareStatementWithAutoGeneratedKeys() throws SQLException {
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        when(targetConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(mockPreparedStatement);

        PreparedStatement result =
                connectionProxy.prepareStatement("INSERT INTO test VALUES (1)", Statement.RETURN_GENERATED_KEYS);

        Assertions.assertNotNull(result);
        verify(targetConnection).prepareStatement("INSERT INTO test VALUES (1)", Statement.RETURN_GENERATED_KEYS);
    }

    @Test
    public void testPrepareStatementWithColumnIndexes() throws SQLException {
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        int[] columnIndexes = {1, 2};
        when(targetConnection.prepareStatement(anyString(), any(int[].class))).thenReturn(mockPreparedStatement);

        PreparedStatement result = connectionProxy.prepareStatement("INSERT INTO test VALUES (1)", columnIndexes);

        Assertions.assertNotNull(result);
        verify(targetConnection).prepareStatement("INSERT INTO test VALUES (1)", columnIndexes);
    }

    @Test
    public void testPrepareStatementWithColumnNames() throws SQLException {
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        String[] columnNames = {"id", "name"};
        when(targetConnection.prepareStatement(anyString(), any(String[].class)))
                .thenReturn(mockPreparedStatement);

        PreparedStatement result = connectionProxy.prepareStatement("INSERT INTO test VALUES (1)", columnNames);

        Assertions.assertNotNull(result);
        verify(targetConnection).prepareStatement("INSERT INTO test VALUES (1)", columnNames);
    }

    @Test
    public void testPrepareCallNotInGlobalTransaction() throws SQLException {
        CallableStatement mockCallableStatement = mock(CallableStatement.class);
        when(targetConnection.prepareCall(anyString())).thenReturn(mockCallableStatement);

        mockedRootContext.when(RootContext::inGlobalTransaction).thenReturn(false);
        mockedRootContext.when(() -> RootContext.assertNotInGlobalTransaction()).thenCallRealMethod();

        CallableStatement result = connectionProxy.prepareCall("{call test_proc()}");

        Assertions.assertNotNull(result);
        verify(targetConnection).prepareCall("{call test_proc()}");
    }

    @Test
    public void testPrepareCallInGlobalTransactionThrowsException() {
        mockedRootContext.when(RootContext::inGlobalTransaction).thenReturn(true);
        mockedRootContext
                .when(() -> RootContext.assertNotInGlobalTransaction())
                .thenThrow(new RuntimeException("Should NOT be in global transaction!"));

        Assertions.assertThrows(RuntimeException.class, () -> {
            connectionProxy.prepareCall("{call test_proc()}");
        });
    }

    @Test
    public void testNativeSQL() throws SQLException {
        when(targetConnection.nativeSQL(anyString())).thenReturn("SELECT * FROM test");

        String result = connectionProxy.nativeSQL("SELECT * FROM test");

        Assertions.assertEquals("SELECT * FROM test", result);
        verify(targetConnection).nativeSQL("SELECT * FROM test");
    }

    @Test
    public void testGetAutoCommit() throws SQLException {
        when(targetConnection.getAutoCommit()).thenReturn(true);

        boolean result = connectionProxy.getAutoCommit();

        Assertions.assertTrue(result);
        verify(targetConnection).getAutoCommit();
    }

    @Test
    public void testClose() throws SQLException {
        connectionProxy.close();

        verify(targetConnection).close();
    }

    @Test
    public void testIsClosed() throws SQLException {
        when(targetConnection.isClosed()).thenReturn(false);

        boolean result = connectionProxy.isClosed();

        Assertions.assertFalse(result);
        verify(targetConnection).isClosed();
    }

    @Test
    public void testGetMetaData() throws SQLException {
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
        when(targetConnection.getMetaData()).thenReturn(mockMetaData);

        DatabaseMetaData result = connectionProxy.getMetaData();

        Assertions.assertSame(mockMetaData, result);
        verify(targetConnection).getMetaData();
    }

    @Test
    public void testSetReadOnly() throws SQLException {
        connectionProxy.setReadOnly(true);

        verify(targetConnection).setReadOnly(true);
    }

    @Test
    public void testIsReadOnly() throws SQLException {
        when(targetConnection.isReadOnly()).thenReturn(true);

        boolean result = connectionProxy.isReadOnly();

        Assertions.assertTrue(result);
        verify(targetConnection).isReadOnly();
    }

    @Test
    public void testSetCatalog() throws SQLException {
        connectionProxy.setCatalog("test_catalog");

        verify(targetConnection).setCatalog("test_catalog");
    }

    @Test
    public void testGetCatalog() throws SQLException {
        when(targetConnection.getCatalog()).thenReturn("test_catalog");

        String result = connectionProxy.getCatalog();

        Assertions.assertEquals("test_catalog", result);
        verify(targetConnection).getCatalog();
    }

    @Test
    public void testSetTransactionIsolation() throws SQLException {
        connectionProxy.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

        verify(targetConnection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    @Test
    public void testGetTransactionIsolation() throws SQLException {
        when(targetConnection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);

        int result = connectionProxy.getTransactionIsolation();

        Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED, result);
        verify(targetConnection).getTransactionIsolation();
    }

    @Test
    public void testGetWarnings() throws SQLException {
        SQLWarning mockWarning = new SQLWarning("test warning");
        when(targetConnection.getWarnings()).thenReturn(mockWarning);

        SQLWarning result = connectionProxy.getWarnings();

        Assertions.assertSame(mockWarning, result);
        verify(targetConnection).getWarnings();
    }

    @Test
    public void testClearWarnings() throws SQLException {
        connectionProxy.clearWarnings();

        verify(targetConnection).clearWarnings();
    }

    @Test
    public void testGetTypeMap() throws SQLException {
        when(targetConnection.getTypeMap()).thenReturn(new java.util.HashMap<>());

        Map<String, Class<?>> result = connectionProxy.getTypeMap();

        Assertions.assertNotNull(result);
        verify(targetConnection).getTypeMap();
    }

    @Test
    public void testSetTypeMap() throws SQLException {
        Map<String, Class<?>> typeMap = new java.util.HashMap<>();
        connectionProxy.setTypeMap(typeMap);

        verify(targetConnection).setTypeMap(typeMap);
    }

    @Test
    public void testSetHoldability() throws SQLException {
        connectionProxy.setHoldability(java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT);

        verify(targetConnection).setHoldability(java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT);
    }

    @Test
    public void testGetHoldability() throws SQLException {
        when(targetConnection.getHoldability()).thenReturn(java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT);

        int result = connectionProxy.getHoldability();

        Assertions.assertEquals(java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT, result);
        verify(targetConnection).getHoldability();
    }

    @Test
    public void testIsValid() throws SQLException {
        when(targetConnection.isValid(10)).thenReturn(true);

        boolean result = connectionProxy.isValid(10);

        Assertions.assertTrue(result);
        verify(targetConnection).isValid(10);
    }

    @Test
    public void testSetClientInfo() throws SQLException {
        connectionProxy.setClientInfo("ApplicationName", "TestApp");

        verify(targetConnection).setClientInfo("ApplicationName", "TestApp");
    }

    @Test
    public void testSetClientInfoWithProperties() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("ApplicationName", "TestApp");

        connectionProxy.setClientInfo(properties);

        verify(targetConnection).setClientInfo(properties);
    }

    @Test
    public void testGetClientInfo() throws SQLException {
        when(targetConnection.getClientInfo("ApplicationName")).thenReturn("TestApp");

        String result = connectionProxy.getClientInfo("ApplicationName");

        Assertions.assertEquals("TestApp", result);
        verify(targetConnection).getClientInfo("ApplicationName");
    }

    @Test
    public void testGetClientInfoProperties() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("ApplicationName", "TestApp");
        when(targetConnection.getClientInfo()).thenReturn(properties);

        Properties result = connectionProxy.getClientInfo();

        Assertions.assertNotNull(result);
        Assertions.assertEquals("TestApp", result.getProperty("ApplicationName"));
        verify(targetConnection).getClientInfo();
    }

    @Test
    public void testUnwrap() throws SQLException {
        when(targetConnection.unwrap(Connection.class)).thenReturn(targetConnection);

        Connection result = connectionProxy.unwrap(Connection.class);

        Assertions.assertSame(targetConnection, result);
        verify(targetConnection).unwrap(Connection.class);
    }

    @Test
    public void testIsWrapperFor() throws SQLException {
        when(targetConnection.isWrapperFor(Connection.class)).thenReturn(true);

        boolean result = connectionProxy.isWrapperFor(Connection.class);

        Assertions.assertTrue(result);
        verify(targetConnection).isWrapperFor(Connection.class);
    }
}
