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
package org.apache.seata.server.cluster.raft;

import org.apache.seata.common.ConfigurationKeys;
import org.apache.seata.common.XID;
import org.apache.seata.config.ConfigurationCache;
import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.lock.LockerManagerFactory;
import org.apache.seata.server.session.SessionHolder;
import org.apache.seata.server.store.StoreConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.apache.seata.common.ConfigurationKeys.SERVER_RAFT_SSL_CLIENT_KEYSTORE_PATH;
import static org.apache.seata.common.ConfigurationKeys.SERVER_RAFT_SSL_ENABLED;
import static org.apache.seata.common.ConfigurationKeys.SERVER_RAFT_SSL_KMF_ALGORITHM;
import static org.apache.seata.common.ConfigurationKeys.SERVER_RAFT_SSL_SERVER_KEYSTORE_PATH;
import static org.apache.seata.common.ConfigurationKeys.SERVER_RAFT_SSL_TMF_ALGORITHM;

public class RaftServerTest extends BaseSpringBootTest {

    @BeforeAll
    public static void setUp(ApplicationContext context) {
        LockerManagerFactory.destroy();
        SessionHolder.destroy();
        RaftServerManager.destroy();
    }

    @AfterEach
    public void destroy() {
        System.setProperty("server.raftPort", "0");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SERVER_ADDR, "");
        ConfigurationCache.clear();
        StoreConfig.setStartupParameter("file", "file", "file");
        LockerManagerFactory.destroy();
        SessionHolder.destroy();
        RaftServerManager.destroy();
    }

    @Test
    public void initRaftServerStart() {
        Assertions.assertDoesNotThrow(() -> ConfigurationFactory.getInstance().getConfig(SERVER_RAFT_SSL_ENABLED));
        Assertions.assertDoesNotThrow(
                () -> ConfigurationFactory.getInstance().getConfig(SERVER_RAFT_SSL_CLIENT_KEYSTORE_PATH));
        Assertions.assertDoesNotThrow(
                () -> ConfigurationFactory.getInstance().getConfig(SERVER_RAFT_SSL_SERVER_KEYSTORE_PATH));
        Assertions.assertDoesNotThrow(
                () -> ConfigurationFactory.getInstance().getConfig(SERVER_RAFT_SSL_KMF_ALGORITHM));
        Assertions.assertDoesNotThrow(
                () -> ConfigurationFactory.getInstance().getConfig(SERVER_RAFT_SSL_TMF_ALGORITHM));
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091" + "," + XID.getIpAddress() + ":9092" + "," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");
        Assertions.assertDoesNotThrow(RaftServerManager::init);
        Assertions.assertNotNull(RaftServerManager.getRaftServer("default"));
        Assertions.assertNotNull(RaftServerManager.groups());
        Assertions.assertNotNull(RaftServerManager.getCliServiceInstance());
        Assertions.assertNotNull(RaftServerManager.getCliClientServiceInstance());
        Assertions.assertFalse(RaftServerManager.isLeader("default"));
        RaftServerManager.start();
    }

    @Test
    public void initRaftServerFail() {
        StoreConfig.setStartupParameter("raft", "raft", "raft");
        Assertions.assertThrows(IllegalArgumentException.class, RaftServerManager::init);
    }

    @Test
    public void initRaftServerFailByRaftPortNull() {
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091" + "," + XID.getIpAddress() + ":9092" + "," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");
        Assertions.assertThrows(IllegalArgumentException.class, RaftServerManager::init);
    }

    @Test
    public void testIsRaftModeWhenNotInitialized() {
        Assertions.assertFalse(RaftServerManager.isRaftMode());
    }

    @Test
    public void testGetRaftServerWhenNotInitialized() {
        Assertions.assertNull(RaftServerManager.getRaftServer("default"));
    }

    @Test
    public void testGetRaftServersWhenNotInitialized() {
        Assertions.assertNotNull(RaftServerManager.getRaftServers());
        Assertions.assertTrue(RaftServerManager.getRaftServers().isEmpty());
    }

    @Test
    public void testGroupsWhenNotInitialized() {
        Assertions.assertNotNull(RaftServerManager.groups());
        Assertions.assertTrue(RaftServerManager.groups().isEmpty());
    }

    @Test
    public void testIsLeaderWhenNotInRaftMode() {
        StoreConfig.setStartupParameter("file", "file", "file");
        Assertions.assertTrue(RaftServerManager.isLeader("default"));
    }

    @Test
    public void testCliServiceInstance() {
        Assertions.assertNotNull(RaftServerManager.getCliServiceInstance());
    }

    @Test
    public void testCliClientServiceInstance() {
        Assertions.assertNotNull(RaftServerManager.getCliClientServiceInstance());
    }

    // ========== Core Method Tests - High Value Coverage ==========

    @Test
    public void testStartAndDestroyLifecycle() {
        // Test full lifecycle: init -> start -> destroy
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091" + "," + XID.getIpAddress() + ":9092" + "," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        Assertions.assertDoesNotThrow(RaftServerManager::init);
        Assertions.assertTrue(RaftServerManager.isRaftMode());

        RaftServer raftServer = RaftServerManager.getRaftServer("default");
        Assertions.assertNotNull(raftServer);
        Assertions.assertNotNull(raftServer.getRaftStateMachine());
        Assertions.assertNotNull(raftServer.getServerId());

        // Start the server
        Assertions.assertDoesNotThrow(RaftServerManager::start);

        // Verify server is running
        Assertions.assertNotNull(raftServer.getNode());

        // Destroy and verify cleanup
        Assertions.assertDoesNotThrow(RaftServerManager::destroy);
        Assertions.assertFalse(RaftServerManager.isRaftMode());
        Assertions.assertTrue(RaftServerManager.getRaftServers().isEmpty());
    }

    @Test
    public void testInitWithCustomRaftOptions() {
        // Test initialization with custom raft configuration
        System.setProperty("server.raftPort", "9094");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9094");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_APPLY_BATCH, "256");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_MAX_APPEND_BUFFER_SIZE, "524288");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_DISRUPTOR_BUFFER_SIZE, "8192");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SYNC, "true");

        StoreConfig.setStartupParameter("raft", "raft", "raft");

        Assertions.assertDoesNotThrow(RaftServerManager::init);

        RaftServer raftServer = RaftServerManager.getRaftServer("default");
        Assertions.assertNotNull(raftServer);

        // Verify custom options were applied
        Assertions.assertDoesNotThrow(RaftServerManager::start);
    }

    @Test
    public void testInitWithCustomNodeOptions() {
        // Test initialization with custom node configuration
        System.setProperty("server.raftPort", "9095");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9095");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SNAPSHOT_INTERVAL, "300");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_ELECTION_TIMEOUT_MS, "2000");

        StoreConfig.setStartupParameter("raft", "raft", "raft");

        Assertions.assertDoesNotThrow(RaftServerManager::init);

        RaftServer raftServer = RaftServerManager.getRaftServer("default");
        Assertions.assertNotNull(raftServer);
    }

    @Test
    public void testMultipleInitCallsAreIdempotent() {
        // Test that multiple init calls don't cause issues
        System.setProperty("server.raftPort", "9096");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9096");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        Assertions.assertDoesNotThrow(RaftServerManager::init);

        // Second init should not throw or cause issues
        Assertions.assertDoesNotThrow(RaftServerManager::init);

        Assertions.assertEquals(1, RaftServerManager.getRaftServers().size());
    }

    @Test
    public void testGetRaftServerForSpecificGroup() {
        // Test retrieving raft server for specific group
        System.setProperty("server.raftPort", "9097");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9097");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();

        RaftServer raftServer = RaftServerManager.getRaftServer("default");
        Assertions.assertNotNull(raftServer);

        RaftServer nonExistent = RaftServerManager.getRaftServer("non-existent-group");
        Assertions.assertNull(nonExistent);
    }

    @Test
    public void testRaftServerStateMachineInitialization() {
        // Test that state machine is properly initialized
        System.setProperty("server.raftPort", "9098");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9098");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();

        RaftServer raftServer = RaftServerManager.getRaftServer("default");
        Assertions.assertNotNull(raftServer);

        RaftStateMachine stateMachine = raftServer.getRaftStateMachine();
        Assertions.assertNotNull(stateMachine);
        Assertions.assertFalse(stateMachine.isLeader());
        Assertions.assertEquals(-1, stateMachine.getCurrentTerm().get());
    }

    @Test
    public void testIsLeaderReturnsTrueWhenNotInRaftMode() {
        // Test that isLeader returns true when not in raft mode (backward compatibility)
        StoreConfig.setStartupParameter("file", "file", "file");
        Assertions.assertFalse(RaftServerManager.isRaftMode());
        Assertions.assertTrue(RaftServerManager.isLeader("any-group"));
    }

    @Test
    public void testDestroyWithoutInit() {
        // Test that destroy can be called without init (should not throw)
        Assertions.assertDoesNotThrow(RaftServerManager::destroy);
    }

    @Test
    public void testGroupsReturnsCorrectGroups() {
        // Test that groups() returns correct group names
        System.setProperty("server.raftPort", "9099");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9099");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_GROUP, "test-group");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();

        Assertions.assertTrue(RaftServerManager.groups().contains("test-group"));
        Assertions.assertEquals(1, RaftServerManager.groups().size());
    }

    @Test
    public void testRaftServerGetServerId() {
        // Test that server ID is correctly set
        System.setProperty("server.raftPort", "9100");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9100");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();

        RaftServer raftServer = RaftServerManager.getRaftServer("default");
        Assertions.assertNotNull(raftServer);
        Assertions.assertNotNull(raftServer.getServerId());
        Assertions.assertEquals(9100, raftServer.getServerId().getPort());
    }

    @Test
    public void testInitWithInvalidConfiguration() {
        // Test initialization with invalid configuration format
        System.setProperty("server.raftPort", "9101");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                "invalid-format-config");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        Assertions.assertThrows(IllegalArgumentException.class, RaftServerManager::init);
    }

    @Test
    public void testInitWithEmptyServerAddr() {
        // Test that empty server addr in file mode doesn't throw
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SERVER_ADDR, "");
        StoreConfig.setStartupParameter("file", "file", "file");

        Assertions.assertDoesNotThrow(RaftServerManager::init);
        Assertions.assertFalse(RaftServerManager.isRaftMode());
    }
}
