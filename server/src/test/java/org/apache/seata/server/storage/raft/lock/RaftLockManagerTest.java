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
package org.apache.seata.server.storage.raft.lock;

import org.apache.seata.common.XID;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.BranchStatus;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.session.BranchSession;
import org.apache.seata.server.session.GlobalSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RaftLockManagerTest extends BaseSpringBootTest {

    private RaftLockManager raftLockManager;

    @BeforeEach
    public void setUp() {
        raftLockManager = new RaftLockManager();
    }

    @Test
    public void testLocalReleaseGlobalSessionLock() throws TransactionException {
        GlobalSession globalSession = createGlobalSession();
        boolean result = raftLockManager.localReleaseGlobalSessionLock(globalSession);
        Assertions.assertTrue(result);
    }

    @Test
    public void testLocalReleaseLock() throws TransactionException {
        BranchSession branchSession = createBranchSession();
        boolean result = raftLockManager.localReleaseLock(branchSession);
        Assertions.assertTrue(result);
    }

    @Test
    public void testLocalReleaseGlobalSessionLockWithNull() throws TransactionException {
        GlobalSession globalSession = createGlobalSession();
        globalSession.setXid(null);
        boolean result = raftLockManager.localReleaseGlobalSessionLock(globalSession);
        Assertions.assertTrue(result);
    }

    @Test
    public void testLocalReleaseLockWithInvalidBranchSession() throws TransactionException {
        BranchSession branchSession = createBranchSession();
        branchSession.setXid(null);
        boolean result = raftLockManager.localReleaseLock(branchSession);
        Assertions.assertTrue(result);
    }

    private GlobalSession createGlobalSession() {
        GlobalSession session = GlobalSession.createGlobalSession("test-app", "test-group", "test-tx", 60000);
        String xid = XID.generateXID(session.getTransactionId());
        session.setXid(xid);
        session.setStatus(GlobalStatus.Begin);
        session.setBeginTime(System.currentTimeMillis());
        session.setApplicationData("test-data");
        return session;
    }

    private BranchSession createBranchSession() {
        BranchSession branchSession = new BranchSession();
        String xid = XID.generateXID(12345L);
        branchSession.setXid(xid);
        branchSession.setTransactionId(12345L);
        branchSession.setBranchId(1L);
        branchSession.setResourceGroupId("test-group");
        branchSession.setResourceId("test-resource");
        branchSession.setLockKey("test:1");
        branchSession.setBranchType(BranchType.AT);
        branchSession.setStatus(BranchStatus.Registered);
        branchSession.setClientId("test-client:127.0.0.1:8080");
        branchSession.setApplicationData("test-branch-data");
        return branchSession;
    }
}
