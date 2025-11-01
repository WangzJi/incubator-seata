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
package org.apache.seata.server.storage.db.store;

import org.apache.seata.common.loader.EnhancedServiceLoader;
import org.apache.seata.common.metadata.Instance;
import org.apache.seata.config.Configuration;
import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.core.store.MappingDO;
import org.apache.seata.core.store.db.DataSourceProvider;
import org.apache.seata.server.BaseSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "dbCaseEnabled", matches = "true")
public class VGroupMappingDataBaseDAOTest extends BaseSpringBootTest {

    private VGroupMappingDataBaseDAO vGroupMappingDataBaseDAO;

    @BeforeEach
    public void setUp() {
        DataSource dataSource =
                EnhancedServiceLoader.load(DataSourceProvider.class, "druid").provide();
        vGroupMappingDataBaseDAO = new VGroupMappingDataBaseDAO(dataSource);
    }

    @AfterEach
    public void tearDown() {
        // Clean up any resources
    }

    @Test
    public void testInstanceCreation() {
        Assertions.assertNotNull(vGroupMappingDataBaseDAO);
    }

    @Test
    public void testInsertAndDeleteMappingDO() {
        MappingDO mappingDO = new MappingDO();
        mappingDO.setVGroup("test-vgroup");
        mappingDO.setNamespace("test-namespace");
        mappingDO.setCluster("test-cluster");

        boolean inserted = vGroupMappingDataBaseDAO.insertMappingDO(mappingDO);
        Assertions.assertTrue(inserted);

        boolean deleted = vGroupMappingDataBaseDAO.clearMappingDOByVGroup("test-vgroup");
        Assertions.assertTrue(deleted);
    }

    // ==================== Unit Tests with Mock ====================

    @Test
    public void testInsertMappingDO_Success() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            MappingDO mappingDO = new MappingDO();
            mappingDO.setVGroup("test-vgroup");
            mappingDO.setNamespace("test-namespace");
            mappingDO.setCluster("test-cluster");

            boolean result = dao.insertMappingDO(mappingDO);

            assertTrue(result);
            verify(preparedStatement, times(1)).setString(1, "test-vgroup");
            verify(preparedStatement, times(1)).setString(2, "test-namespace");
            verify(preparedStatement, times(1)).setString(3, "test-cluster");
        }
    }

    @Test
    public void testInsertMappingDO_Failure() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(0);

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            MappingDO mappingDO = new MappingDO();
            mappingDO.setVGroup("test-vgroup");

            boolean result = dao.insertMappingDO(mappingDO);

            assertFalse(result);
        }
    }

    @Test
    public void testInsertMappingDO_SQLException() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenThrow(new SQLException("Database error"));

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            MappingDO mappingDO = new MappingDO();
            mappingDO.setVGroup("test-vgroup");

            assertThrows(
                    org.apache.seata.common.exception.SeataRuntimeException.class,
                    () -> dao.insertMappingDO(mappingDO));
        }
    }

    @Test
    public void testClearMappingDOByVGroup_Success() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            boolean result = dao.clearMappingDOByVGroup("test-vgroup");

            assertTrue(result);
        }
    }

    @Test
    public void testClearMappingDOByVGroup_Failure() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(0);

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            boolean result = dao.clearMappingDOByVGroup("non-existent-vgroup");

            assertFalse(result);
        }
    }

    @Test
    public void testClearMappingDOByVGroup_SQLException() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenThrow(new SQLException("Database error"));

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            assertThrows(
                    org.apache.seata.common.exception.SeataRuntimeException.class,
                    () -> dao.clearMappingDOByVGroup("test-vgroup"));
        }
    }

    @Test
    public void testDeleteMappingDOByVGroup_Success() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<Instance> instanceMock = Mockito.mockStatic(Instance.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");

            Instance instance = mock(Instance.class);
            instanceMock.when(Instance::getInstance).thenReturn(instance);
            when(instance.getClusterName()).thenReturn("test-cluster");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            boolean result = dao.deleteMappingDOByVGroup("test-vgroup");

            assertTrue(result);
        }
    }

    @Test
    public void testDeleteMappingDOByVGroup_SQLException() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class);
                MockedStatic<Instance> instanceMock = Mockito.mockStatic(Instance.class)) {

            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");

            Instance instance = mock(Instance.class);
            instanceMock.when(Instance::getInstance).thenReturn(instance);
            when(instance.getClusterName()).thenReturn("test-cluster");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenThrow(new SQLException("Database error"));

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            assertThrows(
                    org.apache.seata.common.exception.SeataRuntimeException.class,
                    () -> dao.deleteMappingDOByVGroup("test-vgroup"));
        }
    }

    @Test
    public void testQueryMappingDO_Success() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");
            when(config.getConfig(anyString())).thenReturn("test-cluster");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getString("namespace")).thenReturn("test-namespace", "test-namespace-2");
            when(resultSet.getString("cluster")).thenReturn("test-cluster", "test-cluster");
            when(resultSet.getString("vGroup")).thenReturn("vgroup-1", "vgroup-2");

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            List<MappingDO> result = dao.queryMappingDO();

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("vgroup-1", result.get(0).getVGroup());
            assertEquals("vgroup-2", result.get(1).getVGroup());
        }
    }

    @Test
    public void testQueryMappingDO_EmptyResult() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");
            when(config.getConfig(anyString())).thenReturn("test-cluster");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            List<MappingDO> result = dao.queryMappingDO();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    public void testQueryMappingDO_SQLException() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");
            when(config.getConfig(anyString())).thenReturn("test-cluster");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenThrow(new SQLException("Database error"));

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            assertThrows(org.apache.seata.common.exception.SeataRuntimeException.class, () -> dao.queryMappingDO());
        }
    }

    @Test
    public void testQueryMappingDO_WithNullFields() throws Exception {
        try (MockedStatic<ConfigurationFactory> configFactoryMock = Mockito.mockStatic(ConfigurationFactory.class)) {
            Configuration config = mock(Configuration.class);
            configFactoryMock.when(ConfigurationFactory::getInstance).thenReturn(config);
            when(config.getConfig(anyString(), anyString())).thenReturn("vgroup_mapping");
            when(config.getConfig(anyString())).thenReturn("test-cluster");

            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement preparedStatement = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString("namespace")).thenReturn(null);
            when(resultSet.getString("cluster")).thenReturn("test-cluster");
            when(resultSet.getString("vGroup")).thenReturn("vgroup-1");

            VGroupMappingDataBaseDAO dao = new VGroupMappingDataBaseDAO(dataSource);

            List<MappingDO> result = dao.queryMappingDO();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertNull(result.get(0).getNamespace());
            assertEquals("test-cluster", result.get(0).getCluster());
            assertEquals("vgroup-1", result.get(0).getVGroup());
        }
    }
}
