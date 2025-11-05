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

import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.LeaderChangeContext;
import org.apache.seata.common.store.SessionMode;
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.cluster.raft.snapshot.StoreSnapshotFile;
import org.apache.seata.server.cluster.raft.snapshot.metadata.LeaderMetadataSnapshotFile;
import org.apache.seata.server.cluster.raft.sync.msg.dto.RaftClusterMetadata;
import org.apache.seata.server.store.StoreConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RaftStateMachineTest extends BaseSpringBootTest {

    private RaftStateMachine raftStateMachine;
    private static final String TEST_GROUP = "test-group";

    @BeforeEach
    public void setUp() {
        StoreConfig.setStartupParameter("file", "file", "file");
        raftStateMachine = new RaftStateMachine(TEST_GROUP);
    }

    @AfterEach
    public void tearDown() {
        StoreConfig.setStartupParameter("file", "file", "file");
    }

    @Test
    public void testConstructorInitializesBasicFields() {
        assertNotNull(raftStateMachine);
        assertFalse(raftStateMachine.isLeader());
        assertEquals(-1, raftStateMachine.getCurrentTerm().get());
    }

    @Test
    public void testOnLeaderStartUpdatesLeaderTerm() {
        long term = 5L;
        assertFalse(raftStateMachine.isLeader());

        raftStateMachine.onLeaderStart(term);

        assertTrue(raftStateMachine.isLeader());
        assertEquals(term, raftStateMachine.getCurrentTerm().get());
    }

    @Test
    public void testOnLeaderStopResetsLeaderTerm() {
        // First become leader
        raftStateMachine.onLeaderStart(5L);
        assertTrue(raftStateMachine.isLeader());

        // Then stop being leader
        raftStateMachine.onLeaderStop(Status.OK());

        assertFalse(raftStateMachine.isLeader());
    }

    @Test
    public void testOnLeaderStartMultipleTimes() {
        raftStateMachine.onLeaderStart(1L);
        assertTrue(raftStateMachine.isLeader());

        raftStateMachine.onLeaderStart(2L);
        assertTrue(raftStateMachine.isLeader());
        assertEquals(2L, raftStateMachine.getCurrentTerm().get());
    }

    @Test
    public void testOnStartFollowingUpdatesCurrentTerm() {
        LeaderChangeContext ctx = new LeaderChangeContext(null, 1L, Status.OK());

        raftStateMachine.onStartFollowing(ctx);

        assertEquals(1L, raftStateMachine.getCurrentTerm().get());
    }

    @Test
    public void testOnStopFollowingDoesNotThrow() {
        LeaderChangeContext ctx = new LeaderChangeContext(null, 1L, Status.OK());

        assertDoesNotThrow(() -> raftStateMachine.onStopFollowing(ctx));
    }

    @Test
    public void testIsLeaderReturnsFalseInitially() {
        assertFalse(raftStateMachine.isLeader());
    }

    @Test
    public void testIsLeaderReturnsTrueAfterOnLeaderStart() {
        raftStateMachine.onLeaderStart(1L);
        assertTrue(raftStateMachine.isLeader());
    }

    @Test
    public void testIsLeaderReturnsFalseAfterOnLeaderStop() {
        raftStateMachine.onLeaderStart(1L);
        raftStateMachine.onLeaderStop(Status.OK());
        assertFalse(raftStateMachine.isLeader());
    }

    @Test
    public void testGetCurrentTermInitialValue() {
        assertEquals(-1, raftStateMachine.getCurrentTerm().get());
    }

    @Test
    public void testGetCurrentTermAfterLeaderStart() {
        raftStateMachine.onLeaderStart(10L);
        assertEquals(10L, raftStateMachine.getCurrentTerm().get());
    }

    @Test
    public void testRegistryStoreSnapshotFile() throws Exception {
        LeaderMetadataSnapshotFile snapshotFile = new LeaderMetadataSnapshotFile(TEST_GROUP);
        raftStateMachine.registryStoreSnapshotFile(snapshotFile);

        Field snapshotFilesField = RaftStateMachine.class.getDeclaredField("snapshotFiles");
        snapshotFilesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<StoreSnapshotFile> snapshotFiles = (List<StoreSnapshotFile>) snapshotFilesField.get(raftStateMachine);

        assertTrue(snapshotFiles.size() >= 2); // At least LeaderMetadataSnapshotFile from constructor + new one
    }

    @Test
    public void testGetAndSetRaftLeaderMetadata() {
        RaftClusterMetadata metadata = new RaftClusterMetadata(100L);
        raftStateMachine.setRaftLeaderMetadata(metadata);

        RaftClusterMetadata retrieved = raftStateMachine.getRaftLeaderMetadata();
        assertEquals(100L, retrieved.getTerm());
    }

    @Test
    public void testMultipleLeaderStarts() {
        for (int i = 1; i <= 5; i++) {
            raftStateMachine.onLeaderStart(i);
            assertTrue(raftStateMachine.isLeader());
            assertEquals(i, raftStateMachine.getCurrentTerm().get());
        }
    }

    @Test
    public void testLeaderStartStopCycle() {
        raftStateMachine.onLeaderStart(1L);
        assertTrue(raftStateMachine.isLeader());

        raftStateMachine.onLeaderStop(Status.OK());
        assertFalse(raftStateMachine.isLeader());

        raftStateMachine.onLeaderStart(2L);
        assertTrue(raftStateMachine.isLeader());
        assertEquals(2L, raftStateMachine.getCurrentTerm().get());
    }

    @Test
    public void testConstructorInRaftMode() {
        StoreConfig.setStartupParameter(SessionMode.RAFT.getName(), SessionMode.RAFT.getName(),
                SessionMode.RAFT.getName());

        RaftStateMachine raftModeStateMachine = new RaftStateMachine("raft-group");

        assertNotNull(raftModeStateMachine);
        assertFalse(raftModeStateMachine.isLeader());
    }

    @Test
    public void testFollowerStartWithDifferentTerms() {
        LeaderChangeContext ctx1 = new LeaderChangeContext(null, 5L, Status.OK());
        raftStateMachine.onStartFollowing(ctx1);
        assertEquals(5L, raftStateMachine.getCurrentTerm().get());

        LeaderChangeContext ctx2 = new LeaderChangeContext(null, 10L, Status.OK());
        raftStateMachine.onStartFollowing(ctx2);
        assertEquals(10L, raftStateMachine.getCurrentTerm().get());
    }

    @Test
    public void testLeaderTermProgression() {
        assertEquals(-1, raftStateMachine.getCurrentTerm().get());

        raftStateMachine.onLeaderStart(1L);
        assertEquals(1L, raftStateMachine.getCurrentTerm().get());

        raftStateMachine.onLeaderStart(5L);
        assertEquals(5L, raftStateMachine.getCurrentTerm().get());

        raftStateMachine.onLeaderStop(Status.OK());
        assertEquals(5L, raftStateMachine.getCurrentTerm().get()); // currentTerm should remain
    }
}
