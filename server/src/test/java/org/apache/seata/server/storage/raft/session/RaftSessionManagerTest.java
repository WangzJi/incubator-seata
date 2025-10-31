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
package org.apache.seata.server.storage.raft.session;

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

import java.io.IOException;

public class RaftSessionManagerTest extends BaseSpringBootTest {

    private RaftSessionManager raftSessionManager;

    @BeforeEach
    public void setUp() throws IOException {
        raftSessionManager = new RaftSessionManager("test-raft");
    }

    @Test
    public void testGetName() {
        Assertions.assertEquals("test-raft", raftSessionManager.getName());
    }

    @Test
    public void testSetName() {
        raftSessionManager.setName("new-name");
        Assertions.assertEquals("new-name", raftSessionManager.getName());
    }

    @Test
    public void testAddGlobalSession() throws TransactionException {
        GlobalSession session = createGlobalSession();
        raftSessionManager.addGlobalSession(session);
        GlobalSession foundSession = raftSessionManager.findGlobalSession(session.getXid());
        Assertions.assertNotNull(foundSession);
        Assertions.assertEquals(session.getXid(), foundSession.getXid());
    }

    @Test
    public void testFindGlobalSession() throws TransactionException {
        GlobalSession session = createGlobalSession();
        raftSessionManager.addGlobalSession(session);
        GlobalSession foundSession = raftSessionManager.findGlobalSession(session.getXid());
        Assertions.assertNotNull(foundSession);
        Assertions.assertEquals(session.getXid(), foundSession.getXid());
        Assertions.assertEquals(session.getTransactionId(), foundSession.getTransactionId());
    }

    @Test
    public void testRemoveGlobalSession() throws TransactionException {
        GlobalSession session = createGlobalSession();
        raftSessionManager.addGlobalSession(session);
        Assertions.assertNotNull(raftSessionManager.findGlobalSession(session.getXid()));

        raftSessionManager.removeGlobalSession(session);
        GlobalSession removedSession = raftSessionManager.findGlobalSession(session.getXid());
        Assertions.assertNull(removedSession);
    }

    @Test
    public void testRemoveGlobalSessionWithBranches() throws TransactionException {
        GlobalSession session = createGlobalSession();
        raftSessionManager.addGlobalSession(session);

        raftSessionManager.removeGlobalSession(session);
        GlobalSession removedSession = raftSessionManager.findGlobalSession(session.getXid());
        Assertions.assertNull(removedSession);
    }

    @Test
    public void testDestroy() {
        Assertions.assertDoesNotThrow(() -> raftSessionManager.destroy());
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

    private BranchSession createBranchSession(GlobalSession globalSession) {
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(globalSession.getXid());
        branchSession.setTransactionId(globalSession.getTransactionId());
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
