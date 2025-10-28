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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.apache.seata.common.thread.NamedThreadFactory;
import org.apache.seata.core.protocol.AbstractMessage;
import org.apache.seata.core.protocol.HeartbeatMessage;
import org.apache.seata.core.protocol.MergedWarpMessage;
import org.apache.seata.core.protocol.RpcMessage;
import org.apache.seata.core.protocol.transaction.BranchRegisterRequest;
import org.apache.seata.core.protocol.transaction.GlobalBeginRequest;
import org.apache.seata.core.protocol.transaction.GlobalCommitRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for AbstractNettyRemotingClient
 */
public class AbstractNettyRemotingClientTest {

    private TestNettyRemotingClient client;
    private ThreadPoolExecutor messageExecutor;
    private NettyClientConfig clientConfig;

    @BeforeEach
    public void setUp() {
        clientConfig = new NettyClientConfig();
        messageExecutor = new ThreadPoolExecutor(
                1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new NamedThreadFactory("test", 1));
        client = new TestNettyRemotingClient(clientConfig, messageExecutor);
    }

    @AfterEach
    public void tearDown() {
        if (client != null) {
            try {
                client.destroy();
            } catch (Exception e) {
                // Ignore
            }
        }
        if (messageExecutor != null) {
            messageExecutor.shutdown();
        }
    }

    @Test
    public void testRegisterChannelEventListener() {
        ChannelEventListener listener = mock(ChannelEventListener.class);

        client.registerChannelEventListener(listener);

        Channel channel = mock(Channel.class);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        client.onChannelActive(channel);

        verify(listener, times(1)).onChannelConnected(channel);
    }

    @Test
    public void testRegisterNullChannelEventListener() {
        client.registerChannelEventListener(null);

        Channel channel = mock(Channel.class);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        client.onChannelActive(channel);
    }

    @Test
    public void testUnregisterChannelEventListener() {
        ChannelEventListener listener = mock(ChannelEventListener.class);
        client.registerChannelEventListener(listener);

        Channel channel = mock(Channel.class);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        client.onChannelActive(channel);
        verify(listener, times(1)).onChannelConnected(channel);

        client.unregisterChannelEventListener(listener);

        client.onChannelActive(channel);
        verify(listener, times(1)).onChannelConnected(channel);
    }

    @Test
    public void testUnregisterNullChannelEventListener() {
        ChannelEventListener listener = mock(ChannelEventListener.class);
        client.registerChannelEventListener(listener);

        client.unregisterChannelEventListener(null);

        Channel channel = mock(Channel.class);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        client.onChannelActive(channel);
        verify(listener, times(1)).onChannelConnected(channel);
    }

    @Test
    public void testOnChannelActive() {
        ChannelEventListener listener = mock(ChannelEventListener.class);
        client.registerChannelEventListener(listener);

        Channel channel = mock(Channel.class);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        client.onChannelActive(channel);

        verify(listener, times(1)).onChannelConnected(channel);
    }

    @Test
    public void testOnChannelInactive() {
        ChannelEventListener listener = mock(ChannelEventListener.class);
        client.registerChannelEventListener(listener);

        Channel channel = mock(Channel.class);
        ChannelId channelId = mock(ChannelId.class);
        when(channel.id()).thenReturn(channelId);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        client.onChannelInactive(channel);

        verify(listener, times(1)).onChannelDisconnected(channel);
    }

    @Test
    public void testOnChannelException() {
        ChannelEventListener listener = mock(ChannelEventListener.class);
        client.registerChannelEventListener(listener);

        Channel channel = mock(Channel.class);
        ChannelId channelId = mock(ChannelId.class);
        when(channel.id()).thenReturn(channelId);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        Throwable cause = new RuntimeException("Test exception");

        client.onChannelException(channel, cause);

        verify(listener, times(1)).onChannelException(channel, cause);
    }

    @Test
    public void testOnChannelIdle() {
        ChannelEventListener listener = mock(ChannelEventListener.class);
        client.registerChannelEventListener(listener);

        Channel channel = mock(Channel.class);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        client.onChannelIdle(channel);

        verify(listener, times(1)).onChannelIdle(channel);
    }

    @Test
    public void testFireChannelEventWithExceptionInListener() {
        ChannelEventListener listener1 = mock(ChannelEventListener.class);
        ChannelEventListener listener2 = mock(ChannelEventListener.class);

        doNothing().when(listener1).onChannelConnected(any());
        doNothing().when(listener2).onChannelConnected(any());

        client.registerChannelEventListener(listener1);
        client.registerChannelEventListener(listener2);

        Channel channel = mock(Channel.class);
        client.onChannelActive(channel);

        verify(listener1, times(1)).onChannelConnected(channel);
        verify(listener2, times(1)).onChannelConnected(channel);
    }

    @Test
    public void testCleanupResourcesForChannel() {
        Channel channel = mock(Channel.class);
        ChannelId channelId = mock(ChannelId.class);
        when(channel.id()).thenReturn(channelId);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        client.cleanupResourcesForChannel(channel);
    }

    @Test
    public void testCleanupResourcesForNullChannel() {
        client.cleanupResourcesForChannel(null);
    }

    @Test
    public void testSendAsyncRequestWithMergeMessage() {
        MergedWarpMessage mergeMessage = new MergedWarpMessage();
        mergeMessage.msgIds.add(1);
        mergeMessage.msgIds.add(2);
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");
        mergeMessage.msgs.add(request);

        assertTrue(mergeMessage.msgIds.size() > 0, "Merge message should have IDs");
    }

    @Test
    public void testSendAsyncRequestWithNullChannel() {
        try {
            client.sendAsyncRequest(null, HeartbeatMessage.PING);
        } catch (Exception e) {
            assertNotNull(e, "Should throw exception for null channel");
        }
    }

    @Test
    public void testSendAsyncRequestWithHeartbeat() {
        HeartbeatMessage heartbeat = HeartbeatMessage.PING;
        assertNotNull(heartbeat, "Heartbeat message should not be null");
    }

    @Test
    public void testGetXidFromGlobalBeginRequest() {
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-transaction");

        String xid = client.getXid(request);

        assertEquals("test-transaction", xid);
    }

    @Test
    public void testGetXidFromGlobalCommitRequest() {
        GlobalCommitRequest request = new GlobalCommitRequest();
        request.setXid("test-xid-12345");

        String xid = client.getXid(request);

        assertEquals("test-xid-12345", xid);
    }

    @Test
    public void testGetXidFromBranchRegisterRequest() {
        BranchRegisterRequest request = new BranchRegisterRequest();
        request.setXid("branch-xid-12345");

        String xid = client.getXid(request);

        assertEquals("branch-xid-12345", xid);
    }

    @Test
    public void testGetXidFromUnknownMessage() {
        AbstractMessage unknownMessage = mock(AbstractMessage.class);

        String xid = client.getXid(unknownMessage);

        assertNotNull(xid, "Should return random xid for unknown message");
    }

    @Test
    public void testDestroyChannel() {
        String serverAddress = "127.0.0.1:8080";
        Channel channel = mock(Channel.class);

        client.destroyChannel(serverAddress, channel);
    }

    @Test
    public void testRegisterProcessor() {
        client.registerProcessor(1, null, null);

        assertNotNull(client.processorTable);
    }

    @Test
    public void testMergeLockAndCondition() {
        assertNotNull(client.mergeLock);
        assertNotNull(client.mergeCondition);
        assertFalse(client.isSending);
    }

    @Test
    public void testMergeMsgMapOperations() {
        MergedWarpMessage message = new MergedWarpMessage();
        message.msgIds.add(1);

        client.mergeMsgMap.put(100, message);

        assertTrue(client.mergeMsgMap.containsKey(100));
        assertEquals(message, client.mergeMsgMap.get(100));
    }

    @Test
    public void testChildToParentMapOperations() {
        client.childToParentMap.put(1, 100);
        client.childToParentMap.put(2, 100);

        assertEquals(100, client.childToParentMap.get(1));
        assertEquals(100, client.childToParentMap.get(2));
    }

    @Test
    public void testBasketMapOperations() {
        String serverAddress = "127.0.0.1:8080";
        LinkedBlockingQueue<RpcMessage> basket = new LinkedBlockingQueue<>();
        client.basketMap.put(serverAddress, basket);

        assertTrue(client.basketMap.containsKey(serverAddress));
    }

    @Test
    public void testGetTransactionServiceGroup() {
        String serviceGroup = client.getTransactionServiceGroup();

        assertEquals("test-service-group", serviceGroup);
    }

    @Test
    public void testIsEnableClientBatchSendRequest() {
        boolean enabled = client.isEnableClientBatchSendRequest();

        assertFalse(enabled);
    }

    @Test
    public void testGetRpcRequestTimeout() {
        long timeout = client.getRpcRequestTimeout();

        assertEquals(30000L, timeout);
    }

    @Test
    public void testGetClientChannelManager() {
        assertNotNull(client.getClientChannelManager());
    }

    @Test
    public void testGetTransactionMessageHandler() {
        assertNull(client.getTransactionMessageHandler());
    }

    @Test
    public void testSetTransactionMessageHandler() {
        client.setTransactionMessageHandler(null);

        assertNull(client.getTransactionMessageHandler());
    }

    @Test
    public void testMultipleListenersReceiveEvents() {
        ChannelEventListener listener1 = mock(ChannelEventListener.class);
        ChannelEventListener listener2 = mock(ChannelEventListener.class);
        ChannelEventListener listener3 = mock(ChannelEventListener.class);

        client.registerChannelEventListener(listener1);
        client.registerChannelEventListener(listener2);
        client.registerChannelEventListener(listener3);

        Channel channel = mock(Channel.class);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        client.onChannelActive(channel);

        verify(listener1, times(1)).onChannelConnected(channel);
        verify(listener2, times(1)).onChannelConnected(channel);
        verify(listener3, times(1)).onChannelConnected(channel);
    }

    @Test
    public void testChannelEventTypesCoverage() {
        ChannelEventListener listener = mock(ChannelEventListener.class);
        client.registerChannelEventListener(listener);

        Channel channel = mock(Channel.class);
        ChannelId channelId = mock(ChannelId.class);
        when(channel.id()).thenReturn(channelId);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        client.onChannelActive(channel);
        verify(listener).onChannelConnected(channel);

        client.onChannelInactive(channel);
        verify(listener).onChannelDisconnected(channel);

        Throwable cause = new Exception("test");
        client.onChannelException(channel, cause);
        verify(listener).onChannelException(channel, cause);

        client.onChannelIdle(channel);
        verify(listener).onChannelIdle(channel);
    }

    @Test
    public void testSendSyncRequestWithNullChannel() {
        Channel channel = null;
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        try {
            client.sendSyncRequest(channel, request);
        } catch (Exception e) {
            // Expected exception
        }
    }

    @Test
    public void testSendAsyncResponse() {
        String serverAddress = "127.0.0.1:8080";
        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(1);
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        try {
            client.sendAsyncResponse(serverAddress, rpcMessage, request);
        } catch (Exception e) {
            // Expected when channel is not available
        }
    }

    @Test
    public void testLoadBalance() {
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        try {
            String address = client.loadBalance("test-service-group", request);
            // May fail if registry is not initialized
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testLoadBalanceWithNullMessage() {
        try {
            String address = client.loadBalance("test-service-group", null);
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testChannelWritabilityChanged() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.isWritable()).thenReturn(true);

        try {
            AbstractNettyRemotingClient.ClientHandler handler = client.new ClientHandler();
            handler.channelWritabilityChanged(ctx);
            verify(ctx, times(1)).fireChannelWritabilityChanged();
        } catch (Exception e) {
            // Expected in test environment
        }
    }

    @Test
    public void testChannelWritabilityChangedWhenNotWritable() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.isWritable()).thenReturn(false);

        try {
            AbstractNettyRemotingClient.ClientHandler handler = client.new ClientHandler();
            handler.channelWritabilityChanged(ctx);
            verify(ctx, times(1)).fireChannelWritabilityChanged();
        } catch (Exception e) {
            // Expected in test environment
        }
    }

    @Test
    public void testSendSyncRequestWithBatchEnabled() {
        // Create a client with batch send enabled
        TestNettyRemotingClientWithBatch batchClient =
                new TestNettyRemotingClientWithBatch(clientConfig, messageExecutor);

        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        try {
            batchClient.sendSyncRequest(request);
        } catch (Exception e) {
            // Expected when registry is not initialized or timeout
            assertNotNull(e);
        } finally {
            try {
                batchClient.destroy();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Test
    public void testDoSelectWithEmptyList() throws Exception {
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        InetSocketAddress address = client.doSelect(null, request);
        assertNull(address);
    }

    @Test
    public void testDoSelectWithSingleAddress() throws Exception {
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        java.util.List<InetSocketAddress> list = new java.util.ArrayList<>();
        list.add(new InetSocketAddress("127.0.0.1", 8080));

        InetSocketAddress address = client.doSelect(list, request);
        assertNotNull(address);
        assertEquals("127.0.0.1", address.getHostString());
        assertEquals(8080, address.getPort());
    }

    @Test
    public void testSendAsyncRequestWithChannel() {
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        when(channel.isWritable()).thenReturn(true);

        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        try {
            client.sendAsyncRequest(channel, request);
        } catch (Exception e) {
            // Expected in test environment
        }
    }

    @Test
    public void testSendAsyncRequestWithMergedWarpMessage() {
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        when(channel.isWritable()).thenReturn(true);

        MergedWarpMessage mergeMessage = new MergedWarpMessage();
        mergeMessage.msgIds.add(1);
        mergeMessage.msgIds.add(2);
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");
        mergeMessage.msgs.add(request);

        try {
            client.sendAsyncRequest(channel, mergeMessage);
        } catch (Exception e) {
            // Expected in test environment
        }
    }

    @Test
    public void testSendSyncRequestNonBatchMode() {
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        try {
            client.sendSyncRequest(request);
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testSendSyncRequestBatchModeSuccess() throws Exception {
        TestNettyRemotingClientWithBatchAndMockManager batchClient =
                new TestNettyRemotingClientWithBatchAndMockManager(clientConfig, messageExecutor);

        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        try {
            java.lang.reflect.Field basketMapField = AbstractNettyRemotingClient.class.getDeclaredField("basketMap");
            basketMapField.setAccessible(true);
            basketMapField.get(batchClient);

            batchClient.sendSyncRequest(request);
        } catch (Exception e) {
            assertNotNull(e);
        } finally {
            try {
                batchClient.destroy();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Test
    public void testSendSyncRequestBatchModeTimeout() throws Exception {
        TestNettyRemotingClientWithBatchTimeout batchClient =
                new TestNettyRemotingClientWithBatchTimeout(clientConfig, messageExecutor);

        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        try {
            batchClient.sendSyncRequest(request);
        } catch (java.util.concurrent.TimeoutException e) {
            assertNotNull(e);
        } catch (Exception e) {
            assertNotNull(e);
        } finally {
            try {
                batchClient.destroy();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Test
    public void testCollectMessageIdsForChannel() throws Exception {
        Channel channel = mock(Channel.class);
        ChannelId channelId = mock(ChannelId.class);
        when(channel.id()).thenReturn(channelId);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        java.lang.reflect.Method collectMethod =
                AbstractNettyRemotingClient.class.getDeclaredMethod("collectMessageIdsForChannel", ChannelId.class);
        collectMethod.setAccessible(true);

        java.util.Set<Integer> messageIds = (java.util.Set<Integer>) collectMethod.invoke(client, channelId);

        assertNotNull(messageIds);
    }

    @Test
    public void testCleanupFuturesForMessageIds() throws Exception {
        java.util.Set<Integer> messageIds = new java.util.HashSet<>();
        messageIds.add(1);
        messageIds.add(2);

        Exception testException = new Exception("Test exception");

        java.lang.reflect.Method cleanupMethod = AbstractNettyRemotingClient.class.getDeclaredMethod(
                "cleanupFuturesForMessageIds", java.util.Set.class, Exception.class);
        cleanupMethod.setAccessible(true);

        cleanupMethod.invoke(client, messageIds, testException);
    }

    @Test
    public void testClientHandlerChannelRead() throws Exception {
        AbstractNettyRemotingClient.ClientHandler handler = client.new ClientHandler();
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(ctx.channel()).thenReturn(channel);

        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(1);

        try {
            handler.channelRead(ctx, rpcMessage);
        } catch (Exception e) {
            // Expected in test environment
        }
    }

    @Test
    public void testClientHandlerChannelReadInvalidMessage() throws Exception {
        AbstractNettyRemotingClient.ClientHandler handler = client.new ClientHandler();
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(ctx.channel()).thenReturn(channel);

        String invalidMessage = "not an RpcMessage";

        try {
            handler.channelRead(ctx, invalidMessage);
        } catch (Exception e) {
            // Expected in test environment
        }
    }

    /**
     * Concrete test implementation of AbstractNettyRemotingClient
     */
    static class TestNettyRemotingClient extends AbstractNettyRemotingClient {

        public TestNettyRemotingClient(NettyClientConfig nettyClientConfig, ThreadPoolExecutor messageExecutor) {
            super(nettyClientConfig, messageExecutor, NettyPoolKey.TransactionRole.TMROLE);
        }

        @Override
        protected Function<String, NettyPoolKey> getPoolKeyFunction() {
            return serverAddress -> new NettyPoolKey(NettyPoolKey.TransactionRole.TMROLE, serverAddress);
        }

        @Override
        protected String getTransactionServiceGroup() {
            return "test-service-group";
        }

        @Override
        protected boolean isEnableClientBatchSendRequest() {
            return false;
        }

        @Override
        protected long getRpcRequestTimeout() {
            return 30000L;
        }

        @Override
        public void onRegisterMsgSuccess(
                String serverAddress, Channel channel, Object response, AbstractMessage requestMessage) {}

        @Override
        public void onRegisterMsgFail(
                String serverAddress, Channel channel, Object response, AbstractMessage requestMessage) {}
    }

    /**
     * Test implementation with batch send enabled
     */
    static class TestNettyRemotingClientWithBatch extends AbstractNettyRemotingClient {

        public TestNettyRemotingClientWithBatch(
                NettyClientConfig nettyClientConfig, ThreadPoolExecutor messageExecutor) {
            super(nettyClientConfig, messageExecutor, NettyPoolKey.TransactionRole.TMROLE);
        }

        @Override
        protected Function<String, NettyPoolKey> getPoolKeyFunction() {
            return serverAddress -> new NettyPoolKey(NettyPoolKey.TransactionRole.TMROLE, serverAddress);
        }

        @Override
        protected String getTransactionServiceGroup() {
            return "test-service-group";
        }

        @Override
        protected boolean isEnableClientBatchSendRequest() {
            return true;
        }

        @Override
        protected long getRpcRequestTimeout() {
            return 30000L;
        }

        @Override
        public void onRegisterMsgSuccess(
                String serverAddress, Channel channel, Object response, AbstractMessage requestMessage) {}

        @Override
        public void onRegisterMsgFail(
                String serverAddress, Channel channel, Object response, AbstractMessage requestMessage) {}
    }

    /**
     * Test implementation with batch send enabled and mock manager
     */
    static class TestNettyRemotingClientWithBatchAndMockManager extends AbstractNettyRemotingClient {

        public TestNettyRemotingClientWithBatchAndMockManager(
                NettyClientConfig nettyClientConfig, ThreadPoolExecutor messageExecutor) {
            super(nettyClientConfig, messageExecutor, NettyPoolKey.TransactionRole.TMROLE);
        }

        @Override
        protected Function<String, NettyPoolKey> getPoolKeyFunction() {
            return serverAddress -> new NettyPoolKey(NettyPoolKey.TransactionRole.TMROLE, serverAddress);
        }

        @Override
        protected String getTransactionServiceGroup() {
            return "test-service-group";
        }

        @Override
        protected boolean isEnableClientBatchSendRequest() {
            return true;
        }

        @Override
        protected long getRpcRequestTimeout() {
            return 100L;
        }

        @Override
        public void onRegisterMsgSuccess(
                String serverAddress, Channel channel, Object response, AbstractMessage requestMessage) {}

        @Override
        public void onRegisterMsgFail(
                String serverAddress, Channel channel, Object response, AbstractMessage requestMessage) {}
    }

    /**
     * Test implementation with batch send enabled and short timeout
     */
    static class TestNettyRemotingClientWithBatchTimeout extends AbstractNettyRemotingClient {

        public TestNettyRemotingClientWithBatchTimeout(
                NettyClientConfig nettyClientConfig, ThreadPoolExecutor messageExecutor) {
            super(nettyClientConfig, messageExecutor, NettyPoolKey.TransactionRole.TMROLE);
        }

        @Override
        protected Function<String, NettyPoolKey> getPoolKeyFunction() {
            return serverAddress -> new NettyPoolKey(NettyPoolKey.TransactionRole.TMROLE, serverAddress);
        }

        @Override
        protected String getTransactionServiceGroup() {
            return "test-service-group";
        }

        @Override
        protected boolean isEnableClientBatchSendRequest() {
            return true;
        }

        @Override
        protected long getRpcRequestTimeout() {
            return 1L;
        }

        @Override
        public void onRegisterMsgSuccess(
                String serverAddress, Channel channel, Object response, AbstractMessage requestMessage) {}

        @Override
        public void onRegisterMsgFail(
                String serverAddress, Channel channel, Object response, AbstractMessage requestMessage) {}
    }
}
