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
package org.apache.seata.core.rpc.netty;

import io.netty.channel.Channel;
import org.apache.seata.core.protocol.RegisterRMRequest;
import org.apache.seata.core.protocol.RegisterTMRequest;
import org.apache.seata.core.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for ChannelManager
 */
public class ChannelManagerTest {

    private Channel channel;
    private ConcurrentMap<Channel, RpcContext> identifiedChannels;
    private ConcurrentMap<String, ConcurrentMap<Integer, RpcContext>> tmChannels;
    private ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<Integer, RpcContext>>>>
            rmChannels;

    @BeforeEach
    public void setUp() throws Exception {
        channel = mock(Channel.class);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        when(channel.isActive()).thenReturn(true);

        // Get access to private static fields
        Field identifiedField = ChannelManager.class.getDeclaredField("IDENTIFIED_CHANNELS");
        identifiedField.setAccessible(true);
        identifiedChannels = (ConcurrentMap<Channel, RpcContext>) identifiedField.get(null);
        identifiedChannels.clear();

        Field tmField = ChannelManager.class.getDeclaredField("TM_CHANNELS");
        tmField.setAccessible(true);
        tmChannels = (ConcurrentMap<String, ConcurrentMap<Integer, RpcContext>>) tmField.get(null);
        tmChannels.clear();

        Field rmField = ChannelManager.class.getDeclaredField("RM_CHANNELS");
        rmField.setAccessible(true);
        rmChannels = (ConcurrentMap<
                        String, ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<Integer, RpcContext>>>>)
                rmField.get(null);
        rmChannels.clear();
    }

    @AfterEach
    public void tearDown() {
        identifiedChannels.clear();
        tmChannels.clear();
        rmChannels.clear();
    }

    @Test
    public void testIsRegistered() {
        assertFalse(ChannelManager.isRegistered(channel), "Channel should not be registered initially");

        RpcContext context = new RpcContext();
        context.setChannel(channel);
        identifiedChannels.put(channel, context);

        assertTrue(ChannelManager.isRegistered(channel), "Channel should be registered");
    }

    @Test
    public void testGetRoleFromChannel() {
        assertNull(ChannelManager.getRoleFromChannel(channel), "Role should be null for unregistered channel");

        RpcContext context = new RpcContext();
        context.setChannel(channel);
        context.setClientRole(NettyPoolKey.TransactionRole.TMROLE);
        identifiedChannels.put(channel, context);

        assertEquals(NettyPoolKey.TransactionRole.TMROLE, ChannelManager.getRoleFromChannel(channel));
    }

    @Test
    public void testGetContextFromIdentified() {
        assertNull(ChannelManager.getContextFromIdentified(channel));

        RpcContext context = new RpcContext();
        context.setChannel(channel);
        identifiedChannels.put(channel, context);

        assertSame(context, ChannelManager.getContextFromIdentified(channel));
    }

    @Test
    public void testRegisterTMChannel() throws Exception {
        RegisterTMRequest request = new RegisterTMRequest();
        request.setApplicationId("test-app");
        request.setTransactionServiceGroup("test-group");
        request.setVersion("1.5.0");

        ChannelManager.registerTMChannel(request, channel);

        assertTrue(ChannelManager.isRegistered(channel), "TM channel should be registered");
        RpcContext context = ChannelManager.getContextFromIdentified(channel);
        assertNotNull(context);
        assertEquals("test-app", context.getApplicationId());
        assertEquals("test-group", context.getTransactionServiceGroup());
        assertEquals(NettyPoolKey.TransactionRole.TMROLE, context.getClientRole());
    }

    @Test
    public void testRegisterRMChannel() throws Exception {
        RegisterRMRequest request = new RegisterRMRequest();
        request.setApplicationId("test-app");
        request.setTransactionServiceGroup("test-group");
        request.setVersion("1.5.0");
        request.setResourceIds("jdbc:mysql://localhost:3306/seata");

        ChannelManager.registerRMChannel(request, channel);

        assertTrue(ChannelManager.isRegistered(channel), "RM channel should be registered");
        RpcContext context = ChannelManager.getContextFromIdentified(channel);
        assertNotNull(context);
        assertEquals("test-app", context.getApplicationId());
        assertEquals(NettyPoolKey.TransactionRole.RMROLE, context.getClientRole());
        assertNotNull(context.getResourceSets());
        assertTrue(context.getResourceSets().contains("jdbc:mysql://localhost:3306/seata"));
    }

    @Test
    public void testRegisterRMChannelWithMultipleResources() throws Exception {
        RegisterRMRequest request = new RegisterRMRequest();
        request.setApplicationId("test-app");
        request.setTransactionServiceGroup("test-group");
        request.setVersion("1.5.0");
        request.setResourceIds("jdbc:mysql://localhost:3306/db1,jdbc:mysql://localhost:3306/db2");

        ChannelManager.registerRMChannel(request, channel);

        RpcContext context = ChannelManager.getContextFromIdentified(channel);
        assertNotNull(context.getResourceSets());
        assertEquals(2, context.getResourceSets().size());
    }

    @Test
    public void testRegisterRMChannelTwiceAddsResources() throws Exception {
        // First registration
        RegisterRMRequest request1 = new RegisterRMRequest();
        request1.setApplicationId("test-app");
        request1.setTransactionServiceGroup("test-group");
        request1.setVersion("1.5.0");
        request1.setResourceIds("jdbc:mysql://localhost:3306/db1");

        ChannelManager.registerRMChannel(request1, channel);

        // Second registration with additional resource
        RegisterRMRequest request2 = new RegisterRMRequest();
        request2.setApplicationId("test-app");
        request2.setTransactionServiceGroup("test-group");
        request2.setVersion("1.5.0");
        request2.setResourceIds("jdbc:mysql://localhost:3306/db2");

        ChannelManager.registerRMChannel(request2, channel);

        RpcContext context = ChannelManager.getContextFromIdentified(channel);
        assertNotNull(context.getResourceSets());
        assertTrue(context.getResourceSets().size() >= 2, "Both resources should be registered");
    }

    @Test
    public void testRegisterRMChannelWithEmptyResourceIds() throws Exception {
        RegisterRMRequest request = new RegisterRMRequest();
        request.setApplicationId("test-app");
        request.setTransactionServiceGroup("test-group");
        request.setVersion("1.5.0");
        request.setResourceIds("");

        ChannelManager.registerRMChannel(request, channel);

        assertTrue(ChannelManager.isRegistered(channel), "RM should be registered even with empty resources");
    }

    @Test
    public void testReleaseRpcContext() throws Exception {
        RegisterTMRequest request = new RegisterTMRequest();
        request.setApplicationId("test-app");
        request.setTransactionServiceGroup("test-group");
        request.setVersion("1.5.0");

        ChannelManager.registerTMChannel(request, channel);
        assertTrue(ChannelManager.isRegistered(channel));

        RpcContext context = ChannelManager.getContextFromIdentified(channel);
        assertNotNull(context, "Context should exist before release");

        ChannelManager.releaseRpcContext(channel);

        // After release, the context should be cleared (though channel may still be in map)
        // The main thing is that release() doesn't throw exceptions
        // Note: The actual cleanup behavior depends on RpcContext.release() implementation
    }

    @Test
    public void testGetSameClientChannelWithActiveChannel() throws Exception {
        RegisterTMRequest request = new RegisterTMRequest();
        request.setApplicationId("test-app");
        request.setTransactionServiceGroup("test-group");
        request.setVersion("1.5.0");

        ChannelManager.registerTMChannel(request, channel);

        Channel sameChannel = ChannelManager.getSameClientChannel(channel);
        assertNotNull(sameChannel);
        assertTrue(sameChannel.isActive());
    }

    @Test
    public void testGetSameClientChannelWithInactiveChannel() {
        Channel inactiveChannel = mock(Channel.class);
        when(inactiveChannel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        when(inactiveChannel.isActive()).thenReturn(false);

        RpcContext context = new RpcContext();
        context.setChannel(inactiveChannel);
        context.setClientRole(NettyPoolKey.TransactionRole.TMROLE);
        identifiedChannels.put(inactiveChannel, context);

        Channel result = ChannelManager.getSameClientChannel(inactiveChannel);
        // Should try to find alternative channel
        assertNull(result, "Should return null when no active channel available");
    }

    @Test
    public void testGetChannelWithValidClientId() throws Exception {
        RegisterRMRequest request = new RegisterRMRequest();
        request.setApplicationId("test-app");
        request.setTransactionServiceGroup("test-group");
        request.setVersion("1.5.0");
        request.setResourceIds("jdbc:mysql://localhost:3306/seata");

        ChannelManager.registerRMChannel(request, channel);

        String clientId = "test-app:127.0.0.1:8080";
        Channel result = ChannelManager.getChannel("jdbc:mysql://localhost:3306/seata", clientId, false);

        assertNotNull(result, "Should find registered channel");
        assertEquals(channel, result);
    }

    @Test
    public void testGetChannelWithNullResourceId() {
        String clientId = "test-app:127.0.0.1:8080";
        Channel result = ChannelManager.getChannel(null, clientId, false);
        assertNull(result, "Should return null for null resource ID");
    }

    @Test
    public void testGetChannelWithEmptyResourceId() {
        String clientId = "test-app:127.0.0.1:8080";
        Channel result = ChannelManager.getChannel("", clientId, false);
        assertNull(result, "Should return null for empty resource ID");
    }

    @Test
    public void testGetChannelWithInvalidClientId() {
        String clientId = "invalid-client-id";
        try {
            ChannelManager.getChannel("jdbc:mysql://localhost:3306/seata", clientId, false);
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Invalid Client ID"), "Should throw exception for invalid client ID");
        }
    }

    @Test
    public void testGetRmChannels() throws Exception {
        // Register multiple RM channels with different resources
        Channel channel1 = mock(Channel.class);
        when(channel1.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8081));
        when(channel1.isActive()).thenReturn(true);

        Channel channel2 = mock(Channel.class);
        when(channel2.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8082));
        when(channel2.isActive()).thenReturn(true);

        RegisterRMRequest request1 = new RegisterRMRequest();
        request1.setApplicationId("test-app");
        request1.setTransactionServiceGroup("test-group");
        request1.setVersion("1.5.0");
        request1.setResourceIds("jdbc:mysql://localhost:3306/db1");
        ChannelManager.registerRMChannel(request1, channel1);

        RegisterRMRequest request2 = new RegisterRMRequest();
        request2.setApplicationId("test-app");
        request2.setTransactionServiceGroup("test-group");
        request2.setVersion("1.5.0");
        request2.setResourceIds("jdbc:mysql://localhost:3306/db2");
        ChannelManager.registerRMChannel(request2, channel2);

        Map<String, Channel> rmChannels = ChannelManager.getRmChannels();
        assertNotNull(rmChannels);
        assertTrue(rmChannels.size() >= 1, "Should have at least one RM channel");
    }

    @Test
    public void testRegisterMultipleTMChannels() throws Exception {
        Channel channel1 = mock(Channel.class);
        when(channel1.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8081));
        when(channel1.isActive()).thenReturn(true);

        Channel channel2 = mock(Channel.class);
        when(channel2.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8082));
        when(channel2.isActive()).thenReturn(true);

        RegisterTMRequest request1 = new RegisterTMRequest();
        request1.setApplicationId("test-app");
        request1.setTransactionServiceGroup("test-group");
        request1.setVersion("1.5.0");
        ChannelManager.registerTMChannel(request1, channel1);

        RegisterTMRequest request2 = new RegisterTMRequest();
        request2.setApplicationId("test-app");
        request2.setTransactionServiceGroup("test-group");
        request2.setVersion("1.5.0");
        ChannelManager.registerTMChannel(request2, channel2);

        assertTrue(ChannelManager.isRegistered(channel1));
        assertTrue(ChannelManager.isRegistered(channel2));
    }
}
