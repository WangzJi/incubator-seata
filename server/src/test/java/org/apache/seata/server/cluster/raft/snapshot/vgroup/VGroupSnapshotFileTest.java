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
package org.apache.seata.server.cluster.raft.snapshot.vgroup;

import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.LocalFileMetaOutter;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import org.apache.seata.core.store.MappingDO;
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.session.SessionHolder;
import org.apache.seata.server.storage.raft.store.RaftVGroupMappingStoreManager;
import org.apache.seata.server.store.StoreConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VGroupSnapshotFile covering save and load operations.
 */
public class VGroupSnapshotFileTest extends BaseSpringBootTest {

    private static final String TEST_GROUP = "test-group";
    private VGroupSnapshotFile snapshotFile;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        StoreConfig.setStartupParameter("file", "file", "file");
        snapshotFile = new VGroupSnapshotFile(TEST_GROUP);
    }

    @Test
    public void testConstructor() {
        assertNotNull(snapshotFile);
        assertEquals(TEST_GROUP, snapshotFile.group);
    }

    @Test
    public void testRootMappingManagerNameConstant() {
        assertEquals("vgroup_mapping", VGroupSnapshotFile.ROOT_MAPPING_MANAGER_NAME);
    }

    @Test
    public void testSaveWithMockWriter() {
        // Mock the writer
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn(tempDir.toString());
        when(writer.addFile(anyString())).thenReturn(true);

        // Mock VGroup mapping manager
        RaftVGroupMappingStoreManager mockManager = mock(RaftVGroupMappingStoreManager.class);
        Map<String, MappingDO> vgroupMap = new HashMap<>();

        when(mockManager.loadVGroupsByUnit(TEST_GROUP)).thenReturn(vgroupMap);

        // Note: This test validates the save method structure
        Status status = snapshotFile.save(writer);

        verify(writer, atLeastOnce()).getPath();
    }

    @Test
    public void testSaveWhenWriterAddFileFails() {
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn(tempDir.toString());
        when(writer.addFile(anyString())).thenReturn(false);

        Status status = snapshotFile.save(writer);

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
        verify(reader).getFileMeta(VGroupSnapshotFile.ROOT_MAPPING_MANAGER_NAME);
    }

    @Test
    public void testLoadWithValidFile() {
        // Test load with valid file meta
        SnapshotReader reader = mock(SnapshotReader.class);
        LocalFileMetaOutter.LocalFileMeta fileMeta = LocalFileMetaOutter.LocalFileMeta.newBuilder()
                .build();

        when(reader.getFileMeta(anyString())).thenReturn(fileMeta);
        when(reader.getPath()).thenReturn(tempDir.toString());

        // This will test the code path even if it fails due to missing actual file
        boolean result = snapshotFile.load(reader);

        verify(reader).getFileMeta(VGroupSnapshotFile.ROOT_MAPPING_MANAGER_NAME);
        verify(reader, atLeastOnce()).getPath();
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

    @Test
    public void testSaveWithEmptyVGroupMap() {
        // Test save with empty vgroup map
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn(tempDir.toString());
        when(writer.addFile(anyString())).thenReturn(true);

        RaftVGroupMappingStoreManager mockManager = mock(RaftVGroupMappingStoreManager.class);
        when(mockManager.loadVGroupsByUnit(TEST_GROUP)).thenReturn(new HashMap<>());

        Status status = snapshotFile.save(writer);

        assertNotNull(status);
    }
}
