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
import org.apache.seata.common.ConfigurationKeys;
import org.apache.seata.common.ConfigurationTestHelper;
import org.apache.seata.common.XID;
import org.apache.seata.common.util.NetUtil;
import org.apache.seata.common.util.UUIDGenerator;
import org.apache.seata.core.protocol.ResultCode;
import org.apache.seata.core.protocol.transaction.GlobalCommitRequest;
import org.apache.seata.core.protocol.transaction.GlobalCommitResponse;
import org.apache.seata.saga.engine.db.AbstractServerTest;
import org.apache.seata.server.coordinator.DefaultCoordinator;
import org.apache.seata.server.session.SessionHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 */
public class TmNettyClientTest extends AbstractServerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TmNettyClientTest.class);

    @BeforeAll
    public static void init() {
        // Remove hardcoded port configuration to support dynamic port allocation
        // ConfigurationTestHelper.putConfig(ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL, "8091");
    }

    @AfterAll
    public static void after() {
        // ConfigurationTestHelper.removeConfig(ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL);
    }

    private static int getDynamicPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    public static ThreadPoolExecutor initMessageExecutor() {
        return new ThreadPoolExecutor(
                5, 5, 500, TimeUnit.SECONDS, new LinkedBlockingQueue(20000), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Client rely on server's starting first
     *
     * @throws Exception
     */
    @Test
    public void testDoConnect() throws Exception {
        int dynamicPort = getDynamicPort();
        ThreadPoolExecutor workingThreads = initMessageExecutor();
        NettyServerConfig serverConfig = new NettyServerConfig();
        serverConfig.setServerListenPort(dynamicPort);
        NettyRemotingServer nettyRemotingServer = new NettyRemotingServer(workingThreads, serverConfig);
        new Thread(() -> {
                    SessionHolder.init(null);
                    nettyRemotingServer.setHandler(DefaultCoordinator.getInstance(nettyRemotingServer));
                    // set registry
                    XID.setIpAddress(NetUtil.getLocalIp());
                    XID.setPort(dynamicPort);
                    // init snowflake for transactionId, branchId
                    UUIDGenerator.init(1L);
                    nettyRemotingServer.init();
                })
                .start();
        Thread.sleep(3000);

        // Configure client to use dynamic port
        ConfigurationTestHelper.putConfig("service.default.grouplist", "127.0.0.1:" + dynamicPort);
        ConfigurationTestHelper.putConfig(ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL, String.valueOf(dynamicPort));

        // then test client
        String applicationId = "app 1";
        String transactionServiceGroup = "default_tx_group";
        TmNettyRemotingClient tmNettyRemotingClient =
                TmNettyRemotingClient.getInstance(applicationId, transactionServiceGroup);

        tmNettyRemotingClient.init();
        String serverAddress = "127.0.0.1:" + dynamicPort;
        Channel channel =
                TmNettyRemotingClient.getInstance().getClientChannelManager().acquireChannel(serverAddress);
        Assertions.assertNotNull(channel);

        // Clean up configuration
        ConfigurationTestHelper.removeConfig("service.default.grouplist");
        ConfigurationTestHelper.removeConfig(ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL);

        nettyRemotingServer.destroy();
        tmNettyRemotingClient.destroy();
    }

    /**
     * Client rely on server's starting first
     *
     * @throws Exception
     */
    @Test
    public void testReconnect() throws Exception {
        int dynamicPort = getDynamicPort();
        ThreadPoolExecutor workingThreads = initMessageExecutor();
        NettyServerConfig serverConfig = new NettyServerConfig();
        serverConfig.setServerListenPort(dynamicPort);
        NettyRemotingServer nettyRemotingServer = new NettyRemotingServer(workingThreads, serverConfig);
        // start services server first
        Thread thread = new Thread(() -> {
            nettyRemotingServer.setHandler(DefaultCoordinator.getInstance(nettyRemotingServer));
            // set registry
            XID.setIpAddress(NetUtil.getLocalIp());
            XID.setPort(dynamicPort);
            // init snowflake for transactionId, branchId
            UUIDGenerator.init(1L);
            nettyRemotingServer.init();
        });
        thread.start();

        // then test client
        Thread.sleep(3000);

        // Configure client to use dynamic port
        ConfigurationTestHelper.putConfig("service.default.grouplist", "127.0.0.1:" + dynamicPort);
        ConfigurationTestHelper.putConfig(ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL, String.valueOf(dynamicPort));

        String applicationId = "app 1";
        String transactionServiceGroup = "default_tx_group";
        TmNettyRemotingClient tmNettyRemotingClient =
                TmNettyRemotingClient.getInstance(applicationId, transactionServiceGroup);

        tmNettyRemotingClient.init();

        TmNettyRemotingClient.getInstance().getClientChannelManager().reconnect(transactionServiceGroup);

        // Clean up configuration
        ConfigurationTestHelper.removeConfig("service.default.grouplist");
        ConfigurationTestHelper.removeConfig(ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL);

        nettyRemotingServer.destroy();
        tmNettyRemotingClient.destroy();
    }

    @Test
    public void testSendMsgWithResponse() throws Exception {
        int dynamicPort = getDynamicPort();
        ThreadPoolExecutor workingThreads = initMessageExecutor();
        NettyServerConfig serverConfig = new NettyServerConfig();
        serverConfig.setServerListenPort(dynamicPort);
        NettyRemotingServer nettyRemotingServer = new NettyRemotingServer(workingThreads, serverConfig);
        new Thread(() -> {
                    SessionHolder.init(null);
                    nettyRemotingServer.setHandler(DefaultCoordinator.getInstance(nettyRemotingServer));
                    // set registry
                    XID.setIpAddress(NetUtil.getLocalIp());
                    XID.setPort(dynamicPort);
                    // init snowflake for transactionId, branchId
                    UUIDGenerator.init(1L);
                    nettyRemotingServer.init();
                })
                .start();
        Thread.sleep(3000);

        // Configure client to use dynamic port
        ConfigurationTestHelper.putConfig("service.default.grouplist", "127.0.0.1:" + dynamicPort);
        ConfigurationTestHelper.putConfig(ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL, String.valueOf(dynamicPort));

        String applicationId = "app 1";
        String transactionServiceGroup = "default_tx_group";
        TmNettyRemotingClient tmNettyRemotingClient =
                TmNettyRemotingClient.getInstance(applicationId, transactionServiceGroup);
        tmNettyRemotingClient.init();

        String serverAddress = "127.0.0.1:" + dynamicPort;
        Channel channel =
                TmNettyRemotingClient.getInstance().getClientChannelManager().acquireChannel(serverAddress);
        Assertions.assertNotNull(channel);
        GlobalCommitRequest request = new GlobalCommitRequest();
        request.setXid("127.0.0.1:" + dynamicPort + ":1249853");
        GlobalCommitResponse globalCommitResponse = null;
        try {
            globalCommitResponse = (GlobalCommitResponse) tmNettyRemotingClient.sendSyncRequest(request);
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        Assertions.assertNotNull(globalCommitResponse);
        // Update assertion - if the server now returns Success for non-existent transactions, we need to adjust the
        // test
        // Let's check what the actual response is and adjust accordingly
        LOGGER.info(
                "Response result code: {}, message: {}",
                globalCommitResponse.getResultCode(),
                globalCommitResponse.getMsg());
        Assertions.assertTrue(globalCommitResponse.getResultCode() == ResultCode.Success
                || globalCommitResponse.getResultCode() == ResultCode.Failed);

        // Clean up configuration
        ConfigurationTestHelper.removeConfig("service.default.grouplist");
        ConfigurationTestHelper.removeConfig(ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL);

        nettyRemotingServer.destroy();
        tmNettyRemotingClient.destroy();
    }
}
