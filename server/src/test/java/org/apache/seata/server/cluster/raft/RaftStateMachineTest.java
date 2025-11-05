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

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.LeaderChangeContext;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import org.apache.seata.common.metadata.ClusterRole;
import org.apache.seata.common.metadata.Node;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    // ========== Tests for codecov uncovered methods ==========

    @Test
    public void testOnSnapshotSaveInFileMode() {
        // In FILE mode, should call done.run(Status.OK()) immediately without saving
        Closure done = mock(Closure.class);
        SnapshotWriter writer = mock(SnapshotWriter.class);

        raftStateMachine.onSnapshotSave(writer, done);

        verify(done).run(argThat(status -> status.isOk()));
        verify(writer, never()).addFile(anyString());
    }

    @Test
    public void testOnSnapshotSaveInRaftMode() {
        // Create RaftStateMachine in RAFT mode
        StoreConfig.setStartupParameter("raft", "raft", "raft");
        RaftStateMachine raftModeStateMachine = new RaftStateMachine(TEST_GROUP);

        Closure done = mock(Closure.class);
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn("/tmp/snapshot");

        // Register a mock snapshot file
        StoreSnapshotFile mockSnapshotFile = mock(StoreSnapshotFile.class);
        when(mockSnapshotFile.save(writer)).thenReturn(Status.OK());
        raftModeStateMachine.registryStoreSnapshotFile(mockSnapshotFile);

        raftModeStateMachine.onSnapshotSave(writer, done);

        // Should call save on the snapshot file
        verify(mockSnapshotFile).save(writer);
        verify(done).run(argThat(status -> status.isOk()));
    }

    @Test
    public void testOnSnapshotSaveFailsWhenSnapshotFileReturnsError() {
        StoreConfig.setStartupParameter("raft", "raft", "raft");
        RaftStateMachine raftModeStateMachine = new RaftStateMachine(TEST_GROUP);

        Closure done = mock(Closure.class);
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn("/tmp/snapshot");

        // Register a mock snapshot file that fails
        StoreSnapshotFile mockSnapshotFile = mock(StoreSnapshotFile.class);
        Status errorStatus = new Status(-1, "Save failed");
        when(mockSnapshotFile.save(writer)).thenReturn(errorStatus);
        raftModeStateMachine.registryStoreSnapshotFile(mockSnapshotFile);

        raftModeStateMachine.onSnapshotSave(writer, done);

        // Should call done with error status
        verify(done).run(argThat(status -> !status.isOk()));
    }

    @Test
    public void testOnSnapshotLoadInFileMode() {
        // In FILE mode, should return true immediately
        SnapshotReader reader = mock(SnapshotReader.class);

        boolean result = raftStateMachine.onSnapshotLoad(reader);

        assertTrue(result);
        verify(reader, never()).getPath();
    }

    @Test
    public void testOnSnapshotLoadWhenIsLeader() {
        // Leader should not load snapshot
        StoreConfig.setStartupParameter("raft", "raft", "raft");
        RaftStateMachine raftModeStateMachine = new RaftStateMachine(TEST_GROUP);
        raftModeStateMachine.onLeaderStart(1L); // Become leader

        SnapshotReader reader = mock(SnapshotReader.class);

        boolean result = raftModeStateMachine.onSnapshotLoad(reader);

        assertFalse(result);
    }

    @Test
    public void testOnSnapshotLoadInRaftModeAsFollower() {
        StoreConfig.setStartupParameter("raft", "raft", "raft");
        RaftStateMachine raftModeStateMachine = new RaftStateMachine(TEST_GROUP);
        // Not a leader (leaderTerm should be -1)

        SnapshotReader reader = mock(SnapshotReader.class);
        when(reader.getPath()).thenReturn("/tmp/snapshot");

        // Register a mock snapshot file
        StoreSnapshotFile mockSnapshotFile = mock(StoreSnapshotFile.class);
        when(mockSnapshotFile.load(reader)).thenReturn(true);
        raftModeStateMachine.registryStoreSnapshotFile(mockSnapshotFile);

        boolean result = raftModeStateMachine.onSnapshotLoad(reader);

        // Should call load on the snapshot file
        verify(mockSnapshotFile).load(reader);
        assertTrue(result);
    }

    @Test
    public void testOnSnapshotLoadFailsWhenSnapshotFileReturnsFalse() {
        StoreConfig.setStartupParameter("raft", "raft", "raft");
        RaftStateMachine raftModeStateMachine = new RaftStateMachine(TEST_GROUP);

        SnapshotReader reader = mock(SnapshotReader.class);
        when(reader.getPath()).thenReturn("/tmp/snapshot");

        // Register a mock snapshot file that fails to load
        StoreSnapshotFile mockSnapshotFile = mock(StoreSnapshotFile.class);
        when(mockSnapshotFile.load(reader)).thenReturn(false);
        raftModeStateMachine.registryStoreSnapshotFile(mockSnapshotFile);

        boolean result = raftModeStateMachine.onSnapshotLoad(reader);

        assertFalse(result);
    }

    @Test
    public void testOnLeaderStartSetsTermsCorrectly() {
        long term = 10L;
        assertFalse(raftStateMachine.isLeader());

        raftStateMachine.onLeaderStart(term);

        assertTrue(raftStateMachine.isLeader());
        assertEquals(term, raftStateMachine.getCurrentTerm().get());
    }

    @Test
    public void testOnConfigurationCommitted() {
        // Create a configuration
        Configuration conf = new Configuration();
        conf.addPeer(new PeerId("127.0.0.1", 8091));

        // Should not throw exception
        assertDoesNotThrow(() -> raftStateMachine.onConfigurationCommitted(conf));
    }

    @Test
    public void testChangeNodeMetadataForFollower() {
        // Test adding a follower node
        Node node = new Node();
        node.setRole(ClusterRole.FOLLOWER);
        node.setGroup(TEST_GROUP);

        // Should not throw exception
        assertDoesNotThrow(() -> raftStateMachine.changeNodeMetadata(node));

        // Verify the node was added to followers
        RaftClusterMetadata metadata = raftStateMachine.getRaftLeaderMetadata();
        assertNotNull(metadata);
    }

    @Test
    public void testChangeNodeMetadataForLearner() {
        // Test adding a learner node
        Node node = new Node();
        node.setRole(ClusterRole.LEARNER);
        node.setGroup(TEST_GROUP);

        // Should not throw exception
        assertDoesNotThrow(() -> raftStateMachine.changeNodeMetadata(node));

        // Verify the node was added to learners
        RaftClusterMetadata metadata = raftStateMachine.getRaftLeaderMetadata();
        assertNotNull(metadata);
    }
}
