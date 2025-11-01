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

import org.apache.seata.common.exception.ErrorCode;
import org.apache.seata.common.exception.SeataRuntimeException;
import org.apache.seata.common.metadata.Instance;
import org.apache.seata.core.store.MappingDO;
import org.apache.seata.server.BaseSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "dbCaseEnabled", matches = "true")
public class DataBaseVGroupMappingStoreManagerTest extends BaseSpringBootTest {

    private DataBaseVGroupMappingStoreManager storeManager;

    @BeforeEach
    public void setUp() {
        storeManager = new DataBaseVGroupMappingStoreManager();
    }

    @AfterEach
    public void tearDown() {
        // Clean up any resources
    }

    @Test
    public void testInstanceCreation() {
        Assertions.assertNotNull(storeManager);
    }

    @Test
    public void testAddAndRemoveVGroup() {
        Instance instance = Instance.getInstance();
        instance.setNamespace("test-namespace");
        instance.setClusterName("test-cluster");

        MappingDO mappingDO = new MappingDO();
        mappingDO.setVGroup("test-vgroup");
        mappingDO.setNamespace("test-namespace");
        mappingDO.setCluster("test-cluster");

        boolean added = storeManager.addVGroup(mappingDO);
        Assertions.assertTrue(added);

        Map<String, Object> vGroups = storeManager.loadVGroups();
        Assertions.assertNotNull(vGroups);

        boolean removed = storeManager.removeVGroup("test-vgroup");
        Assertions.assertTrue(removed);
    }

    @Test
    public void testLoadVGroups() {
        Map<String, Object> vGroups = storeManager.loadVGroups();
        Assertions.assertNotNull(vGroups);
    }

    // ==================== Unit Tests with Mock ====================

    @Test
    public void testAddVGroup_Success() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        MappingDO mappingDO = new MappingDO();
        mappingDO.setVGroup("test-vgroup-1");
        mappingDO.setNamespace("test-namespace");
        mappingDO.setCluster("test-cluster");

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.insertMappingDO(any(MappingDO.class))).thenReturn(true);

        boolean result = manager.addVGroup(mappingDO);

        assertTrue(result);
    }

    @Test
    public void testAddVGroup_Failure() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        MappingDO mappingDO = new MappingDO();
        mappingDO.setVGroup("test-vgroup-2");
        mappingDO.setNamespace("test-namespace");
        mappingDO.setCluster("test-cluster");

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.insertMappingDO(any(MappingDO.class))).thenReturn(false);

        boolean result = manager.addVGroup(mappingDO);

        assertFalse(result);
    }

    @Test
    public void testAddVGroup_WithNull() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.insertMappingDO(any(MappingDO.class))).thenReturn(false);

        boolean result = manager.addVGroup(null);

        assertFalse(result);
    }

    @Test
    public void testAddVGroup_ThrowsException() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        MappingDO mappingDO = new MappingDO();
        mappingDO.setVGroup("test-vgroup-3");
        mappingDO.setNamespace("test-namespace");
        mappingDO.setCluster("test-cluster");

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.insertMappingDO(any(MappingDO.class)))
                .thenThrow(new SeataRuntimeException(ErrorCode.ERR_CONFIG, "Database error"));

        assertThrows(SeataRuntimeException.class, () -> manager.addVGroup(mappingDO));
    }

    @Test
    public void testRemoveVGroup_Success() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.deleteMappingDOByVGroup(any(String.class))).thenReturn(true);

        boolean result = manager.removeVGroup("test-vgroup-4");

        assertTrue(result);
    }

    @Test
    public void testRemoveVGroup_NotExists() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.deleteMappingDOByVGroup(any(String.class))).thenReturn(false);

        boolean result = manager.removeVGroup("non-existent-vgroup");

        assertFalse(result);
    }

    @Test
    public void testRemoveVGroup_WithEmptyString() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.deleteMappingDOByVGroup(eq(""))).thenReturn(false);

        boolean result = manager.removeVGroup("");

        assertFalse(result);
    }

    @Test
    public void testRemoveVGroup_ThrowsException() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.deleteMappingDOByVGroup(any(String.class)))
                .thenThrow(new SeataRuntimeException(ErrorCode.ERROR_SQL, "SQL error"));

        assertThrows(SeataRuntimeException.class, () -> manager.removeVGroup("test-vgroup-5"));
    }

    @Test
    public void testLoadVGroups_Success() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        Instance instance = Instance.getInstance();
        instance.setClusterName("test-cluster");

        List<MappingDO> mockList = new ArrayList<>();
        MappingDO mapping1 = new MappingDO();
        mapping1.setVGroup("vgroup-1");
        mapping1.setCluster("test-cluster");
        mockList.add(mapping1);

        MappingDO mapping2 = new MappingDO();
        mapping2.setVGroup("vgroup-2");
        mapping2.setCluster("test-cluster");
        mockList.add(mapping2);

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.queryMappingDO()).thenReturn(mockList);

        Map<String, Object> result = manager.loadVGroups();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("vgroup-1"));
        assertTrue(result.containsKey("vgroup-2"));
    }

    @Test
    public void testLoadVGroups_EmptyList() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.queryMappingDO()).thenReturn(new ArrayList<>());

        Map<String, Object> result = manager.loadVGroups();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testLoadVGroups_FilterByCluster() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        Instance instance = Instance.getInstance();
        instance.setClusterName("target-cluster");

        List<MappingDO> mockList = new ArrayList<>();
        // Matching cluster
        MappingDO mapping1 = new MappingDO();
        mapping1.setVGroup("vgroup-1");
        mapping1.setCluster("target-cluster");
        mockList.add(mapping1);

        // Different cluster
        MappingDO mapping2 = new MappingDO();
        mapping2.setVGroup("vgroup-2");
        mapping2.setCluster("other-cluster");
        mockList.add(mapping2);

        // Another matching cluster
        MappingDO mapping3 = new MappingDO();
        mapping3.setVGroup("vgroup-3");
        mapping3.setCluster("target-cluster");
        mockList.add(mapping3);

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.queryMappingDO()).thenReturn(mockList);

        Map<String, Object> result = manager.loadVGroups();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("vgroup-1"));
        assertTrue(result.containsKey("vgroup-3"));
        assertFalse(result.containsKey("vgroup-2"));
    }

    @Test
    public void testLoadVGroups_WithNullCluster() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        Instance instance = Instance.getInstance();
        instance.setClusterName("target-cluster");

        List<MappingDO> mockList = new ArrayList<>();
        // Normal mapping
        MappingDO mapping1 = new MappingDO();
        mapping1.setVGroup("vgroup-1");
        mapping1.setCluster("target-cluster");
        mockList.add(mapping1);

        // Null cluster
        MappingDO mapping2 = new MappingDO();
        mapping2.setVGroup("vgroup-2");
        mapping2.setCluster(null);
        mockList.add(mapping2);

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.queryMappingDO()).thenReturn(mockList);

        Map<String, Object> result = manager.loadVGroups();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("vgroup-1"));
        assertFalse(result.containsKey("vgroup-2"));
    }

    @Test
    public void testLoadVGroups_ThrowsException() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
        when(mockDAO.queryMappingDO()).thenThrow(new SeataRuntimeException(ErrorCode.ERR_CONFIG, "Database error"));

        assertThrows(SeataRuntimeException.class, manager::loadVGroups);
    }

    @Test
    public void testLoadVGroups_WithNullInstance() throws Exception {
        DataBaseVGroupMappingStoreManager manager = createMockedManager();

        try (MockedStatic<Instance> instanceMock = Mockito.mockStatic(Instance.class)) {
            instanceMock.when(Instance::getInstance).thenReturn(null);

            List<MappingDO> mockList = new ArrayList<>();
            MappingDO mapping1 = new MappingDO();
            mapping1.setVGroup("vgroup-1");
            mapping1.setCluster("test-cluster");
            mockList.add(mapping1);

            VGroupMappingDataBaseDAO mockDAO = getMockDAO(manager);
            when(mockDAO.queryMappingDO()).thenReturn(mockList);

            assertThrows(NullPointerException.class, manager::loadVGroups);
        }
    }

    // ==================== Helper Methods ====================

    private DataBaseVGroupMappingStoreManager createMockedManager() throws Exception {
        // Create a DataBaseVGroupMappingStoreManager with mocked DAO
        DataBaseVGroupMappingStoreManager manager = new DataBaseVGroupMappingStoreManager();

        // Replace the internal DAO with a mock using reflection
        VGroupMappingDataBaseDAO mockDAO = mock(VGroupMappingDataBaseDAO.class);
        Field daoField = DataBaseVGroupMappingStoreManager.class.getDeclaredField("vGroupMappingDataBaseDAO");
        daoField.setAccessible(true);
        daoField.set(manager, mockDAO);

        return manager;
    }

    private VGroupMappingDataBaseDAO getMockDAO(DataBaseVGroupMappingStoreManager manager) throws Exception {
        Field daoField = DataBaseVGroupMappingStoreManager.class.getDeclaredField("vGroupMappingDataBaseDAO");
        daoField.setAccessible(true);
        return (VGroupMappingDataBaseDAO) daoField.get(manager);
    }
}
