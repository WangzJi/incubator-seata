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
package org.apache.seata.server.cluster.raft.execute;

import org.apache.seata.common.store.LockMode;
import org.apache.seata.common.store.SessionMode;
import org.apache.seata.common.util.NetUtil;
import org.apache.seata.config.ConfigurationCache;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.store.MappingDO;
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.cluster.raft.execute.vgroup.VGroupAddExecute;
import org.apache.seata.server.cluster.raft.execute.vgroup.VGroupRemoveExecute;
import org.apache.seata.server.cluster.raft.sync.msg.RaftVGroupSyncMsg;
import org.apache.seata.server.lock.LockerManagerFactory;
import org.apache.seata.server.session.SessionHolder;
import org.apache.seata.server.storage.raft.store.RaftVGroupMappingStoreManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

/**
 * Unit tests for VGroup execute operations (Add and Remove).
 */
class VGroupExecuteTest extends BaseSpringBootTest {

    private static final String TEST_VGROUP = "test-vgroup";
    private static final String TEST_UNIT = "test-unit";

    @BeforeAll
    public static void setUp(ApplicationContext context) {
        System.setProperty("server.raft.serverAddr", NetUtil.getLocalIp() + ":9091");
        SessionHolder.init(SessionMode.RAFT);
        LockerManagerFactory.destroy();
        LockerManagerFactory.init(LockMode.RAFT);
    }

    @AfterAll
    public static void destroy() {
        // Clear configuration
        ConfigurationCache.clear();
        System.clearProperty("server.raft.serverAddr");

        // Destroy SessionHolder and LockerManagerFactory
        SessionHolder.destroy();
        SessionHolder.init(null);
        LockerManagerFactory.destroy();
    }

    @Test
    public void testVGroupAdd() throws Throwable {
        MappingDO mappingDO = createMappingDO(TEST_VGROUP, TEST_UNIT);

        VGroupAddExecute execute = new VGroupAddExecute();
        RaftVGroupSyncMsg syncMsg = new RaftVGroupSyncMsg();
        syncMsg.setMappingDO(mappingDO);

        Boolean success = execute.execute(syncMsg);
        Assertions.assertTrue(success);

        // Verify the vgroup was added
        RaftVGroupMappingStoreManager manager =
                (RaftVGroupMappingStoreManager) SessionHolder.getRootVGroupMappingManager();
        MappingDO retrieved = manager.loadVGroupsByUnit(TEST_UNIT).get(TEST_VGROUP);
        Assertions.assertNotNull(retrieved);
        Assertions.assertEquals(TEST_VGROUP, retrieved.getVGroup());
        Assertions.assertEquals(TEST_UNIT, retrieved.getUnit());
    }

    @Test
    public void testVGroupRemove() throws Throwable {
        // First add a vgroup
        MappingDO mappingDO = createMappingDO(TEST_VGROUP + "-remove", TEST_UNIT);
        RaftVGroupMappingStoreManager manager =
                (RaftVGroupMappingStoreManager) SessionHolder.getRootVGroupMappingManager();
        manager.localAddVGroup(mappingDO);

        // Verify it was added
        MappingDO retrieved = manager.loadVGroupsByUnit(TEST_UNIT).get(TEST_VGROUP + "-remove");
        Assertions.assertNotNull(retrieved);

        // Now remove it
        VGroupRemoveExecute execute = new VGroupRemoveExecute();
        RaftVGroupSyncMsg syncMsg = new RaftVGroupSyncMsg();
        syncMsg.setMappingDO(mappingDO);

        Boolean success = execute.execute(syncMsg);
        Assertions.assertTrue(success);

        // Verify it was removed
        retrieved = manager.loadVGroupsByUnit(TEST_UNIT).get(TEST_VGROUP + "-remove");
        Assertions.assertNull(retrieved);
    }

    @Test
    public void testVGroupAddMultiple() throws Throwable {
        // Test adding multiple vgroups
        VGroupAddExecute execute = new VGroupAddExecute();

        for (int i = 0; i < 5; i++) {
            String vgroup = TEST_VGROUP + "-multi-" + i;
            MappingDO mappingDO = createMappingDO(vgroup, TEST_UNIT);

            RaftVGroupSyncMsg syncMsg = new RaftVGroupSyncMsg();
            syncMsg.setMappingDO(mappingDO);

            Boolean success = execute.execute(syncMsg);
            Assertions.assertTrue(success);
        }

        // Verify all were added
        RaftVGroupMappingStoreManager manager =
                (RaftVGroupMappingStoreManager) SessionHolder.getRootVGroupMappingManager();

        for (int i = 0; i < 5; i++) {
            String vgroup = TEST_VGROUP + "-multi-" + i;
            MappingDO retrieved = manager.loadVGroupsByUnit(TEST_UNIT).get(vgroup);
            Assertions.assertNotNull(retrieved);
            Assertions.assertEquals(vgroup, retrieved.getVGroup());
        }
    }

    @Test
    public void testVGroupAddWithDifferentUnits() throws Throwable {
        // Test adding vgroups with different units
        VGroupAddExecute execute = new VGroupAddExecute();

        String vgroup1 = TEST_VGROUP + "-unit1";
        String vgroup2 = TEST_VGROUP + "-unit2";

        MappingDO mapping1 = createMappingDO(vgroup1, "unit1");
        MappingDO mapping2 = createMappingDO(vgroup2, "unit2");

        RaftVGroupSyncMsg syncMsg1 = new RaftVGroupSyncMsg();
        syncMsg1.setMappingDO(mapping1);

        RaftVGroupSyncMsg syncMsg2 = new RaftVGroupSyncMsg();
        syncMsg2.setMappingDO(mapping2);

        Assertions.assertTrue(execute.execute(syncMsg1));
        Assertions.assertTrue(execute.execute(syncMsg2));

        // Verify both were added with correct units
        RaftVGroupMappingStoreManager manager =
                (RaftVGroupMappingStoreManager) SessionHolder.getRootVGroupMappingManager();

        MappingDO retrieved1 = manager.loadVGroupsByUnit("unit1").get(vgroup1);
        Assertions.assertNotNull(retrieved1);
        Assertions.assertEquals("unit1", retrieved1.getUnit());

        MappingDO retrieved2 = manager.loadVGroupsByUnit("unit2").get(vgroup2);
        Assertions.assertNotNull(retrieved2);
        Assertions.assertEquals("unit2", retrieved2.getUnit());
    }

    @Test
    public void testVGroupRemoveNonExistent() throws Throwable {
        // Test removing a non-existent vgroup (should not throw exception)
        MappingDO mappingDO = createMappingDO("non-existent-vgroup", TEST_UNIT);

        VGroupRemoveExecute execute = new VGroupRemoveExecute();
        RaftVGroupSyncMsg syncMsg = new RaftVGroupSyncMsg();
        syncMsg.setMappingDO(mappingDO);

        // Should complete without exception
        Boolean success = execute.execute(syncMsg);
        Assertions.assertTrue(success);
    }

    @Test
    public void testVGroupAddAndRemoveCycle() throws Throwable {
        // Test add-remove cycle
        String vgroup = TEST_VGROUP + "-cycle";
        MappingDO mappingDO = createMappingDO(vgroup, TEST_UNIT);
        RaftVGroupMappingStoreManager manager =
                (RaftVGroupMappingStoreManager) SessionHolder.getRootVGroupMappingManager();

        // Add
        VGroupAddExecute addExecute = new VGroupAddExecute();
        RaftVGroupSyncMsg syncMsg = new RaftVGroupSyncMsg();
        syncMsg.setMappingDO(mappingDO);
        Assertions.assertTrue(addExecute.execute(syncMsg));

        MappingDO retrieved = manager.loadVGroupsByUnit(TEST_UNIT).get(vgroup);
        Assertions.assertNotNull(retrieved);

        // Remove
        VGroupRemoveExecute removeExecute = new VGroupRemoveExecute();
        Assertions.assertTrue(removeExecute.execute(syncMsg));

        retrieved = manager.loadVGroupsByUnit(TEST_UNIT).get(vgroup);
        Assertions.assertNull(retrieved);

        // Add again
        Assertions.assertTrue(addExecute.execute(syncMsg));

        retrieved = manager.loadVGroupsByUnit(TEST_UNIT).get(vgroup);
        Assertions.assertNotNull(retrieved);
    }

    private MappingDO createMappingDO(String vGroup, String unit) {
        MappingDO mappingDO = new MappingDO();
        mappingDO.setVGroup(vGroup);
        mappingDO.setUnit(unit);
        return mappingDO;
    }
}
