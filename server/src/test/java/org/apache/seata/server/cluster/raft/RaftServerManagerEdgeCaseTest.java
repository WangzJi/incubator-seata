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
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.lock.LockerManagerFactory;
import org.apache.seata.server.session.SessionHolder;
import org.apache.seata.server.store.StoreConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

/**
 * Edge case tests for RaftServerManager covering boundary conditions,
 * configuration validation, and error handling paths.
 */
public class RaftServerManagerEdgeCaseTest extends BaseSpringBootTest {

    @BeforeAll
    public static void setUp(ApplicationContext context) {
        LockerManagerFactory.destroy();
        SessionHolder.destroy();
        RaftServerManager.destroy();
    }

    @AfterEach
    public void destroy() {
        System.clearProperty("server.raftPort");
        System.clearProperty(ConfigurationKeys.SERVER_RAFT_SERVER_ADDR);
        System.clearProperty(ConfigurationKeys.SERVER_RAFT_GROUP);
        System.clearProperty(ConfigurationKeys.SERVER_RAFT_ELECTION_TIMEOUT_MS);
        System.clearProperty(ConfigurationKeys.SERVER_RAFT_SNAPSHOT_INTERVAL);
        System.clearProperty(ConfigurationKeys.SERVER_RAFT_APPLY_BATCH);
        System.clearProperty(ConfigurationKeys.SERVER_RAFT_MAX_APPEND_BUFFER_SIZE);
        System.clearProperty(ConfigurationKeys.SERVER_RAFT_DISRUPTOR_BUFFER_SIZE);
        System.clearProperty(ConfigurationKeys.SERVER_RAFT_MAX_REPLICATOR_INFLIGHT_MSGS);
        System.clearProperty(ConfigurationKeys.SERVER_RAFT_SYNC);
        ConfigurationCache.clear();
        StoreConfig.setStartupParameter("file", "file", "file");
        LockerManagerFactory.destroy();
        SessionHolder.destroy();
        RaftServerManager.destroy();
    }

    @Test
    public void testInitWithInvalidConfigurationFormat() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SERVER_ADDR, "invalid-format-no-port");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, RaftServerManager::init);
        Assertions.assertTrue(exception.getMessage().contains("fail to parse initConf"));
    }

    @Test
    public void testInitWithEmptyServerAddr() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SERVER_ADDR, "   ");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, RaftServerManager::init);
        Assertions.assertTrue(exception.getMessage().contains("Raft store mode must config")
                || exception.getMessage().contains("fail to parse"));
    }

    @Test
    public void testInitWithPortZeroButNoMatchingIp() {
        // Set up configuration with IPs that don't match the local machine
        System.setProperty("server.raftPort", "0");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                "192.0.2.1:9091,192.0.2.2:9092,192.0.2.3:9093" // TEST-NET-1 addresses that won't match
                );
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        // This should fail because no peer IP matches the local IP when port is 0
        Assertions.assertThrows(Exception.class, RaftServerManager::init);
    }

    @Test
    public void testIsRaftModeAfterSuccessfulInit() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();
        Assertions.assertTrue(RaftServerManager.isRaftMode());
    }

    @Test
    public void testGetRaftServerReturnsNullForNonExistentGroup() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();
        Assertions.assertNull(RaftServerManager.getRaftServer("non-existent-group"));
    }

    @Test
    public void testGetRaftServersAfterInit() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();
        Assertions.assertNotNull(RaftServerManager.getRaftServers());
        Assertions.assertFalse(RaftServerManager.getRaftServers().isEmpty());
    }

    @Test
    public void testGroupsAfterInit() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();
        Assertions.assertNotNull(RaftServerManager.groups());
        Assertions.assertFalse(RaftServerManager.groups().isEmpty());
        Assertions.assertTrue(RaftServerManager.groups().contains("default"));
    }

    @Test
    public void testInitWithCustomGroup() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_GROUP, "custom-group");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();
        Assertions.assertNotNull(RaftServerManager.getRaftServer("custom-group"));
        Assertions.assertTrue(RaftServerManager.groups().contains("custom-group"));
    }

    @Test
    public void testInitWithCustomElectionTimeout() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_ELECTION_TIMEOUT_MS, "2000");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        Assertions.assertDoesNotThrow(RaftServerManager::init);
    }

    @Test
    public void testInitWithCustomSnapshotInterval() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SNAPSHOT_INTERVAL, "300");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        Assertions.assertDoesNotThrow(RaftServerManager::init);
    }

    @Test
    public void testInitWithAllRaftOptionsConfigured() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_APPLY_BATCH, "64");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_MAX_APPEND_BUFFER_SIZE, "524288");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_DISRUPTOR_BUFFER_SIZE, "8192");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_MAX_REPLICATOR_INFLIGHT_MSGS, "512");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SYNC, "true");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        Assertions.assertDoesNotThrow(RaftServerManager::init);
    }

    @Test
    public void testIsLeaderReturnsTrueForNonRaftMode() {
        StoreConfig.setStartupParameter("file", "file", "file");
        Assertions.assertTrue(RaftServerManager.isLeader("any-group"));
    }

    @Test
    public void testIsLeaderReturnsFalseForNonExistentGroup() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();
        // Before leader election completes, should return false
        Assertions.assertFalse(RaftServerManager.isLeader("non-existent-group"));
    }

    @Test
    public void testDestroyResetsRaftMode() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();
        Assertions.assertTrue(RaftServerManager.isRaftMode());

        RaftServerManager.destroy();
        Assertions.assertFalse(RaftServerManager.isRaftMode());
    }

    @Test
    public void testDestroyClearsAllServers() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();
        Assertions.assertFalse(RaftServerManager.getRaftServers().isEmpty());

        RaftServerManager.destroy();
        Assertions.assertTrue(RaftServerManager.getRaftServers().isEmpty());
    }

    @Test
    public void testMultipleInitCallsAreIdempotent() {
        System.setProperty("server.raftPort", "9091");
        System.setProperty(
                ConfigurationKeys.SERVER_RAFT_SERVER_ADDR,
                XID.getIpAddress() + ":9091," + XID.getIpAddress() + ":9092," + XID.getIpAddress() + ":9093");
        StoreConfig.setStartupParameter("raft", "raft", "raft");

        RaftServerManager.init();
        int firstSize = RaftServerManager.getRaftServers().size();

        // Second init should not create duplicate servers
        RaftServerManager.init();
        int secondSize = RaftServerManager.getRaftServers().size();

        Assertions.assertEquals(firstSize, secondSize);
    }

    @Test
    public void testInitInFileModeDontInitializeRaft() {
        // When not in raft mode and no SERVER_RAFT_SERVER_ADDR configured
        StoreConfig.setStartupParameter("file", "file", "file");
        RaftServerManager.init();

        Assertions.assertFalse(RaftServerManager.isRaftMode());
        Assertions.assertTrue(RaftServerManager.getRaftServers().isEmpty());
    }
}
