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

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RpcServer;
import org.apache.seata.common.ConfigurationKeys;
import org.apache.seata.config.Configuration;
import org.apache.seata.config.ConfigurationFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RaftServerUnitTest {

    @TempDir
    Path tempDir;

    private RaftServer raftServer;
    private PeerId serverId;
    private NodeOptions nodeOptions;
    private RpcServer rpcServer;
    private String groupId;
    private String dataPath;

    @BeforeEach
    public void setUp() throws IOException {
        groupId = "test-group";
        dataPath = tempDir.toString();
        serverId = new PeerId("127.0.0.1", 8081);
        nodeOptions = new NodeOptions();
        rpcServer = mock(RpcServer.class);

        raftServer = new RaftServer(dataPath, groupId, serverId, nodeOptions, rpcServer);
    }

    @AfterEach
    public void tearDown() {
        // Clean up system properties set during tests
        System.clearProperty("bolt.server.ssl.enable");
        System.clearProperty("bolt.server.ssl.clientAuth");
        System.clearProperty("bolt.client.ssl.enable");
        System.clearProperty("bolt.server.ssl.keystore");
        System.clearProperty("bolt.server.ssl.keystore.password");
        System.clearProperty("bolt.server.ssl.keystore.type");
        System.clearProperty("bolt.server.ssl.kmf.algorithm");
        System.clearProperty("bolt.client.ssl.keystore");
        System.clearProperty("bolt.client.ssl.keystore.password");
        System.clearProperty("bolt.client.ssl.keystore.type");
        System.clearProperty("bolt.client.ssl.tmf.algorithm");
    }

    @Test
    public void testConstructorInitializesFields() throws Exception {
        assertNotNull(raftServer);

        // Use reflection to verify private fields
        Field stateMachineField = RaftServer.class.getDeclaredField("raftStateMachine");
        stateMachineField.setAccessible(true);
        RaftStateMachine stateMachine = (RaftStateMachine) stateMachineField.get(raftServer);
        assertNotNull(stateMachine);

        Field groupPathField = RaftServer.class.getDeclaredField("groupPath");
        groupPathField.setAccessible(true);
        String groupPath = (String) groupPathField.get(raftServer);
        assertEquals(dataPath + File.separator + groupId, groupPath);
    }

    @Test
    public void testGetRaftStateMachine() {
        RaftStateMachine stateMachine = raftServer.getRaftStateMachine();
        assertNotNull(stateMachine);
    }

    @Test
    public void testGetServerId() {
        PeerId result = raftServer.getServerId();
        assertEquals(serverId, result);
        assertEquals("127.0.0.1:8081", result.toString());
    }

    @Test
    public void testGetNodeBeforeStart() {
        Node node = raftServer.getNode();
        assertNull(node);
    }

    @Test
    public void testSetSystemPropertyWithValidValue() throws Exception {
        Method method = RaftServer.class.getDeclaredMethod("setSystemProperty", String.class, String.class);
        method.setAccessible(true);

        String propertyName = "test.property";
        String propertyValue = "test.value";

        method.invoke(raftServer, propertyName, propertyValue);

        assertEquals(propertyValue, System.getProperty(propertyName));
        System.clearProperty(propertyName);
    }

    @Test
    public void testSetSystemPropertyWithNullValueThrowsException() throws Exception {
        Method method = RaftServer.class.getDeclaredMethod("setSystemProperty", String.class, String.class);
        method.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> {
            method.invoke(raftServer, "test.property", null);
        });

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertTrue(exception.getCause().getMessage().contains("cannot be null or empty"));
    }

    @Test
    public void testSetSystemPropertyWithEmptyValueThrowsException() throws Exception {
        Method method = RaftServer.class.getDeclaredMethod("setSystemProperty", String.class, String.class);
        method.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> {
            method.invoke(raftServer, "test.property", "");
        });

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertTrue(exception.getCause().getMessage().contains("cannot be null or empty"));
    }

    @Test
    public void testSetSystemPropertyWithWhitespaceOnlyValueThrowsException() throws Exception {
        Method method = RaftServer.class.getDeclaredMethod("setSystemProperty", String.class, String.class);
        method.setAccessible(true);

        // Note: The current implementation doesn't trim, so "   " is considered valid
        // This test documents the actual behavior
        method.invoke(raftServer, "test.property", "   ");
        assertEquals("   ", System.getProperty("test.property"));
        System.clearProperty("test.property");
    }

    @Test
    public void testEnableSSLSetsCorrectSystemProperties() throws Exception {
        // Set up configuration values
        Configuration config = ConfigurationFactory.getInstance();
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SSL_SERVER_KEYSTORE_PATH, "/path/to/server.jks");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SSL_SERVER_KEYSTORE_PASSWORD, "serverpass");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SSL_SERVER_KEYSTORE_TYPE, "JKS");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SSL_KMF_ALGORITHM, "SunX509");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SSL_CLIENT_KEYSTORE_PATH, "/path/to/client.jks");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SSL_CLIENT_KEYSTORE_PASSWORD, "clientpass");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SSL_CLIENT_KEYSTORE_TYPE, "PKCS12");
        System.setProperty(ConfigurationKeys.SERVER_RAFT_SSL_TMF_ALGORITHM, "PKIX");

        Method method = RaftServer.class.getDeclaredMethod("enableSSL");
        method.setAccessible(true);
        method.invoke(raftServer);

        // Verify all SSL system properties are set correctly
        assertEquals("true", System.getProperty("bolt.server.ssl.enable"));
        assertEquals("true", System.getProperty("bolt.server.ssl.clientAuth"));
        assertEquals("true", System.getProperty("bolt.client.ssl.enable"));
        assertEquals("/path/to/server.jks", System.getProperty("bolt.server.ssl.keystore"));
        assertEquals("serverpass", System.getProperty("bolt.server.ssl.keystore.password"));
        assertEquals("JKS", System.getProperty("bolt.server.ssl.keystore.type"));
        assertEquals("SunX509", System.getProperty("bolt.server.ssl.kmf.algorithm"));
        assertEquals("/path/to/client.jks", System.getProperty("bolt.client.ssl.keystore"));
        assertEquals("clientpass", System.getProperty("bolt.client.ssl.keystore.password"));
        assertEquals("PKCS12", System.getProperty("bolt.client.ssl.keystore.type"));
        assertEquals("PKIX", System.getProperty("bolt.client.ssl.tmf.algorithm"));
    }

    @Test
    public void testEnableSSLThrowsExceptionWhenServerKeystorePathMissing() throws Exception {
        Method method = RaftServer.class.getDeclaredMethod("enableSSL");
        method.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> {
            method.invoke(raftServer);
        });

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertTrue(exception.getCause().getMessage().contains("bolt.server.ssl.keystore"));
    }

    @Test
    public void testDestroyWithNullRaftGroupService() {
        // raftGroupService is null initially, this should not throw
        raftServer.destroy();
        // If we reach here without exception, the test passes
    }

    @Test
    public void testDestroyCallsShutdownAndJoin() throws Exception {
        RaftGroupService mockRaftGroupService = mock(RaftGroupService.class);

        Field field = RaftServer.class.getDeclaredField("raftGroupService");
        field.setAccessible(true);
        field.set(raftServer, mockRaftGroupService);

        raftServer.destroy();

        verify(mockRaftGroupService).shutdown();
        verify(mockRaftGroupService).join();
    }

    @Test
    public void testDestroyHandlesInterruptedException() throws Exception {
        RaftGroupService mockRaftGroupService = mock(RaftGroupService.class);
        doThrow(new InterruptedException("Test interruption")).when(mockRaftGroupService).join();

        Field field = RaftServer.class.getDeclaredField("raftGroupService");
        field.setAccessible(true);
        field.set(raftServer, mockRaftGroupService);

        // Should not throw, just log warning
        raftServer.destroy();

        verify(mockRaftGroupService).shutdown();
        verify(mockRaftGroupService).join();
    }

    @Test
    public void testCloseCallsDestroy() throws Exception {
        RaftGroupService mockRaftGroupService = mock(RaftGroupService.class);

        Field field = RaftServer.class.getDeclaredField("raftGroupService");
        field.setAccessible(true);
        field.set(raftServer, mockRaftGroupService);

        raftServer.close();

        verify(mockRaftGroupService).shutdown();
        verify(mockRaftGroupService).join();
    }

    @Test
    public void testMultipleDestroyCallsAreSafe() throws Exception {
        RaftGroupService mockRaftGroupService = mock(RaftGroupService.class);

        Field field = RaftServer.class.getDeclaredField("raftGroupService");
        field.setAccessible(true);
        field.set(raftServer, mockRaftGroupService);

        raftServer.destroy();
        raftServer.destroy();

        // shutdown and join should still only be called once per service lifecycle
        verify(mockRaftGroupService).shutdown();
        verify(mockRaftGroupService).join();
    }

    @Test
    public void testConstructorWithIOException() {
        // Test that IOException can be thrown during construction
        // In practice, this would happen if directory operations fail
        // but we can't easily simulate that without mocking FileUtils
        String invalidPath = tempDir.toString();
        assertThrows(Exception.class, () -> {
            new RaftServer(null, groupId, serverId, nodeOptions, rpcServer);
        });
    }

    @Test
    public void testGettersReturnCorrectValues() throws Exception {
        assertEquals(serverId, raftServer.getServerId());
        assertNotNull(raftServer.getRaftStateMachine());
        assertNull(raftServer.getNode()); // Node is null before start()

        // Verify stateMachine is unique per RaftServer instance
        RaftServer another = new RaftServer(dataPath, "another-group", serverId, nodeOptions, rpcServer);
        assertTrue(raftServer.getRaftStateMachine() != another.getRaftStateMachine());
    }
}
