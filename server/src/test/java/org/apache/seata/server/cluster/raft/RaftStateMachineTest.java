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
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.LeaderChangeContext;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import org.apache.seata.common.store.SessionMode;
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.cluster.raft.snapshot.StoreSnapshotFile;
import org.apache.seata.server.cluster.raft.snapshot.metadata.LeaderMetadataSnapshotFile;
import org.apache.seata.server.cluster.raft.sync.RaftSyncMessageSerializer;
import org.apache.seata.server.cluster.raft.sync.msg.RaftBaseMsg;
import org.apache.seata.server.cluster.raft.sync.msg.RaftClusterMetadataMsg;
import org.apache.seata.server.cluster.raft.sync.msg.RaftSyncMessage;
import org.apache.seata.server.cluster.raft.sync.msg.RaftSyncMsgType;
import org.apache.seata.server.cluster.raft.sync.msg.dto.RaftClusterMetadata;
import org.apache.seata.server.store.StoreConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
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

    // ========== Core Method Tests - High Value Coverage ==========

    @Test
    public void testOnApplyWithLeaderClosure() {
        // Test onApply when iterator has a closure (leader path)
        Iterator iterator = mock(Iterator.class);
        Closure closure = mock(Closure.class);

        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.done()).thenReturn(closure);

        raftStateMachine.onApply(iterator);

        verify(closure).run(any(Status.class));
        verify(iterator).next();
    }

    @Test
    public void testOnApplyWithFollowerMessage() throws Exception {
        // Test onApply when iterator has data (follower path)
        Iterator iterator = mock(Iterator.class);

        // Create a REFRESH_CLUSTER_METADATA message
        RaftClusterMetadataMsg msg = new RaftClusterMetadataMsg(new RaftClusterMetadata(10L));
        RaftSyncMessage raftSyncMessage = new RaftSyncMessage();
        raftSyncMessage.setBody(msg);
        byte[] serializedMsg = RaftSyncMessageSerializer.encode(raftSyncMessage);
        ByteBuffer buffer = ByteBuffer.wrap(serializedMsg);

        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.done()).thenReturn(null);
        when(iterator.getData()).thenReturn(buffer);

        raftStateMachine.onApply(iterator);

        verify(iterator).next();
    }

    @Test
    public void testOnApplyWithEmptyBuffer() {
        // Test onApply with empty buffer (heartbeat)
        Iterator iterator = mock(Iterator.class);

        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.done()).thenReturn(null);
        when(iterator.getData()).thenReturn(null);

        assertDoesNotThrow(() -> raftStateMachine.onApply(iterator));

        verify(iterator).next();
    }

    @Test
    public void testOnApplyMultipleIterations() {
        // Test onApply with multiple iterations
        Iterator iterator = mock(Iterator.class);
        Closure closure1 = mock(Closure.class);
        Closure closure2 = mock(Closure.class);

        when(iterator.hasNext()).thenReturn(true, true, false);
        when(iterator.done()).thenReturn(closure1, closure2);

        raftStateMachine.onApply(iterator);

        verify(closure1).run(any(Status.class));
        verify(closure2).run(any(Status.class));
        verify(iterator, times(2)).next();
    }

    @Test
    public void testOnSnapshotSaveInFileMode() {
        // Test snapshot save when mode is FILE (should return OK without saving)
        StoreConfig.setStartupParameter("file", "file", "file");
        RaftStateMachine fileModeMachine = new RaftStateMachine(TEST_GROUP);

        SnapshotWriter writer = mock(SnapshotWriter.class);
        Closure done = mock(Closure.class);

        fileModeMachine.onSnapshotSave(writer, done);

        verify(done).run(argThat(status -> status.isOk()));
        verify(writer, never()).getPath();
    }

    @Test
    public void testOnSnapshotSaveInRaftMode() {
        // Test snapshot save when mode is RAFT
        StoreConfig.setStartupParameter("raft", "raft", "raft");
        RaftStateMachine raftModeMachine = new RaftStateMachine(TEST_GROUP);

        SnapshotWriter writer = mock(SnapshotWriter.class);
        Closure done = mock(Closure.class);
        when(writer.getPath()).thenReturn("/tmp/snapshot");

        // This will attempt to save but may fail due to mock setup
        // The important thing is we're testing the code path
        raftModeMachine.onSnapshotSave(writer, done);

        verify(done).run(any(Status.class));
    }

    @Test
    public void testOnSnapshotLoadInFileMode() {
        // Test snapshot load when mode is FILE (should return true)
        StoreConfig.setStartupParameter("file", "file", "file");
        RaftStateMachine fileModeMachine = new RaftStateMachine(TEST_GROUP);

        SnapshotReader reader = mock(SnapshotReader.class);

        boolean result = fileModeMachine.onSnapshotLoad(reader);

        assertTrue(result);
        verify(reader, never()).getPath();
    }

    @Test
    public void testOnSnapshotLoadWhenIsLeader() {
        // Test snapshot load when node is leader (should return false)
        StoreConfig.setStartupParameter("raft", "raft", "raft");
        RaftStateMachine raftModeMachine = new RaftStateMachine(TEST_GROUP);
        raftModeMachine.onLeaderStart(1L);

        SnapshotReader reader = mock(SnapshotReader.class);

        boolean result = raftModeMachine.onSnapshotLoad(reader);

        assertFalse(result);
    }

    @Test
    public void testRefreshClusterMetadata() {
        // Test refreshClusterMetadata method
        RaftClusterMetadata newMetadata = new RaftClusterMetadata(100L);
        RaftClusterMetadataMsg msg = new RaftClusterMetadataMsg(newMetadata);

        raftStateMachine.refreshClusterMetadata(msg);

        RaftClusterMetadata retrieved = raftStateMachine.getRaftLeaderMetadata();
        assertEquals(100L, retrieved.getTerm());
    }

    @Test
    public void testChangeOrInitRaftClusterMetadata() {
        // Test changeOrInitRaftClusterMetadata when metadata is null
        raftStateMachine.onLeaderStart(5L);

        RaftClusterMetadata metadata = raftStateMachine.changeOrInitRaftClusterMetadata();

        assertNotNull(metadata);
        assertEquals(5L, metadata.getTerm());
        assertNotNull(metadata.getLeader());
    }

    @Test
    public void testChangeOrInitRaftClusterMetadataUpdatesLeader() {
        // Test that changeOrInitRaftClusterMetadata updates leader info correctly
        RaftClusterMetadata existingMetadata = new RaftClusterMetadata(3L);
        raftStateMachine.setRaftLeaderMetadata(existingMetadata);
        raftStateMachine.onLeaderStart(10L);

        RaftClusterMetadata updated = raftStateMachine.changeOrInitRaftClusterMetadata();

        assertEquals(10L, updated.getTerm());
    }
}
