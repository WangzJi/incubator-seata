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
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.core.model.TransactionManager;
import org.apache.seata.core.rpc.netty.RmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.mockserver.ProtocolTestConstants;
import org.apache.seata.core.rpc.netty.mockserver.RmClientTest;
import org.apache.seata.core.rpc.netty.mockserver.TmClientTest;
import org.apache.seata.mockserver.MockServer;
import org.apache.seata.rm.DefaultResourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCC Concurrent Integration Test
 *
 * Tests TCC transaction behavior under concurrent conditions.
 */
public class TccConcurrentIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TccConcurrentIntegrationTest.class);

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
     * Test concurrent TCC transactions commit
     */
    @Test
    public void testConcurrentCommit() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                        try {
                            startLatch.await(); // Wait for all threads to be ready

                            String xid = tm.begin(
                                    ProtocolTestConstants.APPLICATION_ID,
                                    ProtocolTestConstants.SERVICE_GROUP,
                                    "concurrent-commit-" + index,
                                    60000);

                            rm.branchRegister(
                                    BranchType.TCC,
                                    RESOURCE_ID,
                                    "1",
                                    xid,
                                    "{\"thread\":" + index + "}",
                                    String.valueOf(index));

                            GlobalStatus status = tm.commit(xid);

                            if (status == GlobalStatus.Committed) {
                                successCount.incrementAndGet();
                                LOGGER.info("Thread {} committed successfully", index);
                            } else {
                                failCount.incrementAndGet();
                                LOGGER.warn("Thread {} commit returned status: {}", index, status);
                            }
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                            LOGGER.error("Thread {} failed with exception", index, e);
                        } finally {
                            endLatch.countDown();
                        }
                    })
                    .start();
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);

        Assertions.assertTrue(completed, "All threads should complete within timeout");
        Assertions.assertEquals(
                threadCount, successCount.get(), "All concurrent transactions should commit successfully");
        Assertions.assertEquals(0, failCount.get(), "No transactions should fail");
    }

    /**
     * Test concurrent TCC transactions rollback
     */
    @Test
    public void testConcurrentRollback() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                        try {
                            startLatch.await();

                            String xid = tm.begin(
                                    ProtocolTestConstants.APPLICATION_ID,
                                    ProtocolTestConstants.SERVICE_GROUP,
                                    "concurrent-rollback-" + index,
                                    60000);

                            rm.branchRegister(BranchType.TCC, RESOURCE_ID, "1", xid, "{}", String.valueOf(index));

                            GlobalStatus status = tm.rollback(xid);

                            if (status == GlobalStatus.Rollbacked) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            LOGGER.error("Thread {} rollback failed", index, e);
                        } finally {
                            endLatch.countDown();
                        }
                    })
                    .start();
        }

        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);

        Assertions.assertTrue(completed);
        Assertions.assertEquals(threadCount, successCount.get(), "All concurrent rollbacks should succeed");
    }

    /**
     * Test mixed concurrent commit and rollback operations
     */
    @Test
    public void testConcurrentMixedOperations() throws InterruptedException {
        int commitCount = 3;
        int rollbackCount = 3;
        int totalCount = commitCount + rollbackCount;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalCount);
        AtomicInteger commitSuccess = new AtomicInteger(0);
        AtomicInteger rollbackSuccess = new AtomicInteger(0);

        // Commit threads
        for (int i = 0; i < commitCount; i++) {
            final int index = i;
            new Thread(() -> {
                        try {
                            startLatch.await();
                            String xid = tm.begin(
                                    ProtocolTestConstants.APPLICATION_ID,
                                    ProtocolTestConstants.SERVICE_GROUP,
                                    "mixed-commit-" + index,
                                    60000);
                            rm.branchRegister(BranchType.TCC, RESOURCE_ID, "1", xid, "{}", "c" + index);
                            if (tm.commit(xid) == GlobalStatus.Committed) {
                                commitSuccess.incrementAndGet();
                            }
                        } catch (Exception e) {
                            LOGGER.error("Commit thread {} failed", index, e);
                        } finally {
                            endLatch.countDown();
                        }
                    })
                    .start();
        }

        // Rollback threads
        for (int i = 0; i < rollbackCount; i++) {
            final int index = i;
            new Thread(() -> {
                        try {
                            startLatch.await();
                            String xid = tm.begin(
                                    ProtocolTestConstants.APPLICATION_ID,
                                    ProtocolTestConstants.SERVICE_GROUP,
                                    "mixed-rollback-" + index,
                                    60000);
                            rm.branchRegister(BranchType.TCC, RESOURCE_ID, "1", xid, "{}", "r" + index);
                            if (tm.rollback(xid) == GlobalStatus.Rollbacked) {
                                rollbackSuccess.incrementAndGet();
                            }
                        } catch (Exception e) {
                            LOGGER.error("Rollback thread {} failed", index, e);
                        } finally {
                            endLatch.countDown();
                        }
                    })
                    .start();
        }

        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);

        Assertions.assertTrue(completed);
        Assertions.assertEquals(commitCount, commitSuccess.get());
        Assertions.assertEquals(rollbackCount, rollbackSuccess.get());
    }
}
