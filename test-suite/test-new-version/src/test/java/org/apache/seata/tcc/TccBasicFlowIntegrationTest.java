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

import org.apache.seata.common.ConfigurationKeys;
import org.apache.seata.common.ConfigurationTestHelper;
import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.core.model.TransactionManager;
import org.apache.seata.core.rpc.netty.RmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.mockserver.ProtocolTestConstants;
import org.apache.seata.core.rpc.netty.mockserver.RmClientTest;
import org.apache.seata.core.rpc.netty.mockserver.TmClientTest;
import org.apache.seata.mockserver.MockCoordinator;
import org.apache.seata.mockserver.MockServer;
import org.apache.seata.rm.DefaultResourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCC Basic Flow Integration Test
 *
 * Tests the basic TCC transaction flow including commit and rollback scenarios.
 */
public class TccBasicFlowIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TccBasicFlowIntegrationTest.class);

    private static final String RESOURCE_ID = "mock-action";

    private TransactionManager tm;
    private DefaultResourceManager rm;

    @BeforeAll
    public static void beforeAll() {
        ConfigurationFactory.reload();
        ConfigurationTestHelper.putConfig(
                ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL, String.valueOf(ProtocolTestConstants.MOCK_SERVER_PORT));
        MockServer.start(ProtocolTestConstants.MOCK_SERVER_PORT);
        TmNettyRemotingClient.getInstance().destroy();
        RmNettyRemotingClient.getInstance().destroy();
    }

    @AfterAll
    public static void afterAll() {
        ConfigurationTestHelper.removeConfig(ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL);
        TmNettyRemotingClient.getInstance().destroy();
        RmNettyRemotingClient.getInstance().destroy();
    }

    @BeforeEach
    public void setUp() {
        tm = TmClientTest.getTm();
        rm = RmClientTest.getRm(RESOURCE_ID);
    }

    /**
     * Test TCC commit success scenario
     */
    @Test
    public void testTccCommitSuccess() throws TransactionException {
        // 1. Begin global transaction
        String xid = tm.begin(
                ProtocolTestConstants.APPLICATION_ID, ProtocolTestConstants.SERVICE_GROUP, "tcc-commit-test", 60000);
        LOGGER.info("Begin global transaction, xid: {}", xid);

        // 2. Register TCC branch
        Long branchId = rm.branchRegister(
                BranchType.TCC, RESOURCE_ID, "1", xid, "{\"action\":\"transfer\",\"amount\":100}", "1");
        LOGGER.info("Branch registered, branchId: {}", branchId);
        Assertions.assertTrue(branchId > 0, "Branch ID should be positive");

        // 3. Commit global transaction
        GlobalStatus status = tm.commit(xid);
        LOGGER.info("Global transaction committed, status: {}", status);

        // 4. Verify result
        Assertions.assertEquals(GlobalStatus.Committed, status, "Transaction should be committed");
    }

    /**
     * Test TCC rollback success scenario
     */
    @Test
    public void testTccRollbackSuccess() throws TransactionException {
        // 1. Begin global transaction
        String xid = tm.begin(
                ProtocolTestConstants.APPLICATION_ID, ProtocolTestConstants.SERVICE_GROUP, "tcc-rollback-test", 60000);
        LOGGER.info("Begin global transaction, xid: {}", xid);

        // 2. Register TCC branch
        Long branchId = rm.branchRegister(
                BranchType.TCC, RESOURCE_ID, "1", xid, "{\"action\":\"transfer\",\"amount\":100}", "1");
        LOGGER.info("Branch registered, branchId: {}", branchId);

        // 3. Rollback global transaction
        GlobalStatus status = tm.rollback(xid);
        LOGGER.info("Global transaction rolled back, status: {}", status);

        // 4. Verify result
        Assertions.assertEquals(GlobalStatus.Rollbacked, status, "Transaction should be rolled back");
    }

    /**
     * Test TCC commit with retry scenario
     */
    @Test
    public void testTccCommitWithRetry() throws TransactionException {
        // 1. Begin global transaction
        String xid = tm.begin(
                ProtocolTestConstants.APPLICATION_ID,
                ProtocolTestConstants.SERVICE_GROUP,
                "tcc-commit-retry-test",
                60000);

        // 2. Set expected retry count
        MockCoordinator.getInstance().setExpectedRetry(xid, 2);

        // 3. Register TCC branch
        Long branchId = rm.branchRegister(BranchType.TCC, RESOURCE_ID, "1", xid, "{}", "1");

        // 4. Commit global transaction
        GlobalStatus status = tm.commit(xid);

        // 5. Verify result
        Assertions.assertEquals(GlobalStatus.Committed, status);
    }

    /**
     * Test TCC rollback with retry scenario
     */
    @Test
    public void testTccRollbackWithRetry() throws TransactionException {
        // 1. Begin global transaction
        String xid = tm.begin(
                ProtocolTestConstants.APPLICATION_ID,
                ProtocolTestConstants.SERVICE_GROUP,
                "tcc-rollback-retry-test",
                60000);

        // 2. Set expected retry count
        MockCoordinator.getInstance().setExpectedRetry(xid, 2);

        // 3. Register TCC branch
        Long branchId = rm.branchRegister(BranchType.TCC, RESOURCE_ID, "1", xid, "{}", "1");

        // 4. Rollback global transaction
        GlobalStatus status = tm.rollback(xid);

        // 5. Verify result
        Assertions.assertEquals(GlobalStatus.Rollbacked, status);
    }

    /**
     * Test multiple TCC branches commit
     */
    @Test
    public void testMultipleBranchCommit() throws TransactionException {
        // 1. Begin global transaction
        String xid = tm.begin(
                ProtocolTestConstants.APPLICATION_ID, ProtocolTestConstants.SERVICE_GROUP, "multi-branch-test", 60000);

        // 2. Register multiple TCC branches
        Long branchId1 = rm.branchRegister(BranchType.TCC, RESOURCE_ID, "1", xid, "{\"branch\":1}", "1");
        Long branchId2 = rm.branchRegister(BranchType.TCC, RESOURCE_ID, "1", xid, "{\"branch\":2}", "2");
        Long branchId3 = rm.branchRegister(BranchType.TCC, RESOURCE_ID, "1", xid, "{\"branch\":3}", "3");

        LOGGER.info("Registered 3 branches: {}, {}, {}", branchId1, branchId2, branchId3);

        // 3. All branches should have positive IDs
        Assertions.assertTrue(branchId1 > 0);
        Assertions.assertTrue(branchId2 > 0);
        Assertions.assertTrue(branchId3 > 0);

        // 4. Commit global transaction
        GlobalStatus status = tm.commit(xid);

        // 5. Verify all branches committed
        Assertions.assertEquals(
                GlobalStatus.Committed, status, "Transaction with multiple branches should be committed");
    }
}
