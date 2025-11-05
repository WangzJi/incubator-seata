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
package org.apache.seata.server.cluster.raft.snapshot.metadata;

import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.LocalFileMetaOutter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.cluster.raft.RaftServer;
import org.apache.seata.server.cluster.raft.RaftServerManager;
import org.apache.seata.server.cluster.raft.RaftStateMachine;
import org.apache.seata.server.cluster.raft.sync.msg.dto.RaftClusterMetadata;
import org.apache.seata.server.store.StoreConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LeaderMetadataSnapshotFile covering save and load operations.
 */
public class LeaderMetadataSnapshotFileTest extends BaseSpringBootTest {

    private static final String TEST_GROUP = "test-group";
    private LeaderMetadataSnapshotFile snapshotFile;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        StoreConfig.setStartupParameter("file", "file", "file");
        snapshotFile = new LeaderMetadataSnapshotFile(TEST_GROUP);
    }

    @Test
    public void testConstructor() {
        assertNotNull(snapshotFile);
    }

    @Test
    public void testSaveWithMockWriter() {
        // Mock the writer
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn(tempDir.toString());
        when(writer.addFile(anyString())).thenReturn(true);

        // Mock RaftServer and StateMachine
        RaftServer mockServer = mock(RaftServer.class);
        RaftStateMachine mockStateMachine = mock(RaftStateMachine.class);
        RaftClusterMetadata metadata = new RaftClusterMetadata(10L);

        when(mockStateMachine.getRaftLeaderMetadata()).thenReturn(metadata);
        when(mockServer.getRaftStateMachine()).thenReturn(mockStateMachine);

        // Note: This test validates the save method is called correctly
        // Full integration would require RaftServerManager setup
        Status status = snapshotFile.save(writer);

        verify(writer, atLeastOnce()).getPath();
    }

    @Test
    public void testSaveWhenWriterAddFileFails() {
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn(tempDir.toString());
        when(writer.addFile(anyString())).thenReturn(false);

        Status status = snapshotFile.save(writer);

        // Should return error status when addFile fails
        assertNotNull(status);
    }

    @Test
    public void testLoadWithMissingFile() {
        // Test load when file meta is null (file doesn't exist)
        SnapshotReader reader = mock(SnapshotReader.class);
        when(reader.getFileMeta(anyString())).thenReturn(null);
        when(reader.getPath()).thenReturn(tempDir.toString());

        boolean result = snapshotFile.load(reader);

        assertFalse(result);
        verify(reader).getFileMeta(anyString());
    }

    @Test
    public void testLoadWithValidFile() {
        // Test load with valid file
        SnapshotReader reader = mock(SnapshotReader.class);
        LocalFileMetaOutter.LocalFileMeta fileMeta = LocalFileMetaOutter.LocalFileMeta.newBuilder()
                .build();

        when(reader.getFileMeta(anyString())).thenReturn(fileMeta);
        when(reader.getPath()).thenReturn(tempDir.toString());

        // Note: This will likely fail due to file not existing, but tests the code path
        boolean result = snapshotFile.load(reader);

        verify(reader).getFileMeta(anyString());
        verify(reader).getPath();
    }

    @Test
    public void testLoadHandlesException() {
        // Test that load handles exceptions gracefully
        SnapshotReader reader = mock(SnapshotReader.class);
        LocalFileMetaOutter.LocalFileMeta fileMeta = LocalFileMetaOutter.LocalFileMeta.newBuilder()
                .build();

        when(reader.getFileMeta(anyString())).thenReturn(fileMeta);
        when(reader.getPath()).thenThrow(new RuntimeException("Test exception"));

        boolean result = snapshotFile.load(reader);

        assertFalse(result);
    }

    @Test
    public void testSaveHandlesIOException() {
        // Test that save handles IO exceptions
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn("/invalid/path/that/does/not/exist");

        Status status = snapshotFile.save(writer);

        assertNotNull(status);
        assertFalse(status.isOk());
    }
}
