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
package org.apache.seata.core.rpc.processor.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.seata.core.protocol.AbstractMessage;
import org.apache.seata.core.protocol.MergedWarpMessage;
import org.apache.seata.core.protocol.ResultCode;
import org.apache.seata.core.protocol.RpcMessage;
import org.apache.seata.core.protocol.transaction.BranchRegisterRequest;
import org.apache.seata.core.protocol.transaction.BranchRegisterResponse;
import org.apache.seata.core.protocol.transaction.GlobalBeginRequest;
import org.apache.seata.core.protocol.transaction.GlobalBeginResponse;
import org.apache.seata.core.rpc.RemotingServer;
import org.apache.seata.core.rpc.RpcContext;
import org.apache.seata.core.rpc.TransactionMessageHandler;
import org.apache.seata.core.rpc.netty.ChannelManager;
import org.apache.seata.core.rpc.netty.NettyPoolKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for ServerOnRequestProcessor
 */
public class ServerOnRequestProcessorTest {

    private ServerOnRequestProcessor processor;
    private RemotingServer remotingServer;
    private TransactionMessageHandler transactionMessageHandler;
    private ChannelHandlerContext ctx;
    private Channel channel;
    private RpcContext rpcContext;

    @BeforeEach
    public void setUp() throws Exception {
        remotingServer = mock(RemotingServer.class);
        transactionMessageHandler = mock(TransactionMessageHandler.class);
        processor = new ServerOnRequestProcessor(remotingServer, transactionMessageHandler);

        ctx = mock(ChannelHandlerContext.class);
        channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        // Register channel
        rpcContext = new RpcContext();
        rpcContext.setChannel(channel);
        rpcContext.setClientRole(NettyPoolKey.TransactionRole.TMROLE);
        rpcContext.setApplicationId("test-app");
        rpcContext.setTransactionServiceGroup("test-group");
        rpcContext.setVersion("1.5.0");

        java.lang.reflect.Field identifiedChannelsField = ChannelManager.class.getDeclaredField("IDENTIFIED_CHANNELS");
        identifiedChannelsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Channel, RpcContext> identifiedChannels =
                (ConcurrentHashMap<Channel, RpcContext>) identifiedChannelsField.get(null);
        identifiedChannels.put(channel, rpcContext);
    }

    @AfterEach
    public void tearDown() {
        try {
            ChannelManager.releaseRpcContext(channel);
            processor.destroy();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    public void testProcessSingleMessage() throws Exception {
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        GlobalBeginResponse response = new GlobalBeginResponse();
        response.setResultCode(ResultCode.Success);
        response.setXid("127.0.0.1:8091:12345");

        when(transactionMessageHandler.onRequest(any(GlobalBeginRequest.class), any(RpcContext.class)))
                .thenReturn(response);

        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(1);
        rpcMessage.setBody(request);

        processor.process(ctx, rpcMessage);

        verify(transactionMessageHandler).onRequest(eq(request), eq(rpcContext));
        verify(remotingServer).sendAsyncResponse(eq(rpcMessage), eq(channel), eq(response));
    }

    @Test
    public void testProcessMergedWarpMessage() throws Exception {
        // Create merged message
        MergedWarpMessage mergedMessage = new MergedWarpMessage();
        List<AbstractMessage> msgs = new ArrayList<>();
        List<Integer> msgIds = new ArrayList<>();

        GlobalBeginRequest req1 = new GlobalBeginRequest();
        req1.setTransactionName("tx1");
        msgs.add(req1);
        msgIds.add(1);

        BranchRegisterRequest req2 = new BranchRegisterRequest();
        req2.setXid("127.0.0.1:8091:12345");
        req2.setResourceId("jdbc:mysql://localhost:3306/seata");
        msgs.add(req2);
        msgIds.add(2);

        mergedMessage.msgs = msgs;
        mergedMessage.msgIds = msgIds;

        // Setup responses
        GlobalBeginResponse resp1 = new GlobalBeginResponse();
        resp1.setResultCode(ResultCode.Success);

        BranchRegisterResponse resp2 = new BranchRegisterResponse();
        resp2.setResultCode(ResultCode.Success);

        when(transactionMessageHandler.onRequest(eq(req1), any(RpcContext.class)))
                .thenReturn(resp1);
        when(transactionMessageHandler.onRequest(eq(req2), any(RpcContext.class)))
                .thenReturn(resp2);

        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(100);
        rpcMessage.setBody(mergedMessage);

        processor.process(ctx, rpcMessage);

        // Verify transaction handler was called for each message
        verify(transactionMessageHandler, atLeastOnce()).onRequest(any(AbstractMessage.class), any(RpcContext.class));

        // Verify response was sent
        verify(remotingServer, atLeastOnce()).sendAsyncResponse(any(RpcMessage.class), eq(channel), any());
    }

    @Test
    public void testProcessUnregisteredChannel() throws Exception {
        Channel unregisteredChannel = mock(Channel.class);
        ChannelHandlerContext unregisteredCtx = mock(ChannelHandlerContext.class);

        when(unregisteredCtx.channel()).thenReturn(unregisteredChannel);
        when(unregisteredChannel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.2", 8081));

        GlobalBeginRequest request = new GlobalBeginRequest();
        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(1);
        rpcMessage.setBody(request);

        processor.process(unregisteredCtx, rpcMessage);

        // Verify channel is closed
        verify(unregisteredCtx).disconnect();
        verify(unregisteredCtx).close();
    }

    @Test
    public void testProcessNonAbstractMessage() throws Exception {
        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(1);
        rpcMessage.setBody("Not an AbstractMessage");

        // Should handle gracefully without processing
        processor.process(ctx, rpcMessage);

        // Transaction handler should not be called
        verify(transactionMessageHandler, org.mockito.Mockito.never()).onRequest(any(), any());
    }

    @Test
    public void testProcessWithException() throws Exception {
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test-tx");

        when(transactionMessageHandler.onRequest(any(GlobalBeginRequest.class), any(RpcContext.class)))
                .thenThrow(new RuntimeException("Processing error"));

        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(1);
        rpcMessage.setBody(request);

        // Should handle exception gracefully
        try {
            processor.process(ctx, rpcMessage);
        } catch (Exception e) {
            // Exception may be thrown but should be handled
        }

        verify(transactionMessageHandler).onRequest(eq(request), eq(rpcContext));
    }

    @Test
    public void testProcessEmptyMergedMessage() throws Exception {
        MergedWarpMessage mergedMessage = new MergedWarpMessage();
        mergedMessage.msgs = new ArrayList<>();
        mergedMessage.msgIds = new ArrayList<>();

        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(100);
        rpcMessage.setBody(mergedMessage);

        processor.process(ctx, rpcMessage);

        // Should handle empty merged message
        verify(remotingServer, atLeastOnce()).sendAsyncResponse(any(), eq(channel), any());
    }

    @Test
    public void testProcessMergedMessageWithMultipleTypes() throws Exception {
        MergedWarpMessage mergedMessage = new MergedWarpMessage();
        List<AbstractMessage> msgs = new ArrayList<>();
        List<Integer> msgIds = new ArrayList<>();

        // Add various message types
        for (int i = 0; i < 5; i++) {
            GlobalBeginRequest req = new GlobalBeginRequest();
            req.setTransactionName("tx" + i);
            msgs.add(req);
            msgIds.add(i);

            GlobalBeginResponse resp = new GlobalBeginResponse();
            resp.setResultCode(ResultCode.Success);
            when(transactionMessageHandler.onRequest(eq(req), any(RpcContext.class)))
                    .thenReturn(resp);
        }

        mergedMessage.msgs = msgs;
        mergedMessage.msgIds = msgIds;

        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(100);
        rpcMessage.setBody(mergedMessage);

        processor.process(ctx, rpcMessage);

        // Verify all messages were processed
        verify(transactionMessageHandler, org.mockito.Mockito.atLeast(5))
                .onRequest(any(AbstractMessage.class), any(RpcContext.class));
    }

    @Test
    public void testDestroyProcessor() {
        // Test destroy doesn't throw exception
        processor.destroy();

        // Should be able to call destroy multiple times
        processor.destroy();
    }

    @Test
    public void testProcessWithDifferentResponseTypes() throws Exception {
        BranchRegisterRequest request = new BranchRegisterRequest();
        request.setXid("127.0.0.1:8091:12345");
        request.setResourceId("jdbc:mysql://localhost:3306/seata");

        BranchRegisterResponse response = new BranchRegisterResponse();
        response.setResultCode(ResultCode.Success);
        response.setBranchId(1L);

        when(transactionMessageHandler.onRequest(any(BranchRegisterRequest.class), any(RpcContext.class)))
                .thenReturn(response);

        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(1);
        rpcMessage.setBody(request);

        processor.process(ctx, rpcMessage);

        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(remotingServer).sendAsyncResponse(eq(rpcMessage), eq(channel), responseCaptor.capture());

        assertTrue(responseCaptor.getValue() instanceof BranchRegisterResponse);
        BranchRegisterResponse capturedResponse = (BranchRegisterResponse) responseCaptor.getValue();
        assertEquals(1L, capturedResponse.getBranchId());
    }
}
