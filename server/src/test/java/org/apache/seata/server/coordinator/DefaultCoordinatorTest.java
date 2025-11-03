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
package org.apache.seata.server.coordinator;

import io.netty.channel.Channel;
import org.apache.seata.common.DefaultValues;
import org.apache.seata.common.XID;
import org.apache.seata.common.loader.EnhancedServiceLoader;
import org.apache.seata.common.store.SessionMode;
import org.apache.seata.common.util.NetUtil;
import org.apache.seata.common.util.ReflectionUtil;
import org.apache.seata.config.Configuration;
import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.core.constants.ConfigurationKeys;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.BranchStatus;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.core.protocol.AbstractResultMessage;
import org.apache.seata.core.protocol.RpcMessage;
import org.apache.seata.core.protocol.transaction.BranchCommitRequest;
import org.apache.seata.core.protocol.transaction.BranchCommitResponse;
import org.apache.seata.core.protocol.transaction.BranchReportRequest;
import org.apache.seata.core.protocol.transaction.BranchReportResponse;
import org.apache.seata.core.protocol.transaction.BranchRollbackRequest;
import org.apache.seata.core.protocol.transaction.BranchRollbackResponse;
import org.apache.seata.core.protocol.transaction.GlobalBeginRequest;
import org.apache.seata.core.protocol.transaction.GlobalBeginResponse;
import org.apache.seata.core.protocol.transaction.GlobalLockQueryRequest;
import org.apache.seata.core.protocol.transaction.GlobalLockQueryResponse;
import org.apache.seata.core.rpc.RemotingServer;
import org.apache.seata.core.rpc.RpcContext;
import org.apache.seata.core.rpc.processor.RemotingProcessor;
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.metrics.MetricsManager;
import org.apache.seata.server.session.BranchSession;
import org.apache.seata.server.session.GlobalSession;
import org.apache.seata.server.session.SessionHolder;
import org.apache.seata.server.util.StoreUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.apache.seata.common.ConfigurationKeys.XAER_NOTA_RETRY_TIMEOUT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The type DefaultCoordinator test.
 *
 */
public class DefaultCoordinatorTest extends BaseSpringBootTest {
    private static DefaultCoordinator defaultCoordinator;

    private static final String applicationId = "demo-child-app";

    private static final String txServiceGroup = "default_tx_group";

    private static final String txName = "tx-1";

    private static final int timeout = 3000;

    private static final String resourceId = "tb_1";

    private static final String clientId = "c_1";

    private static final String lockKeys_1 = "tb_1:11";

    private static final String lockKeys_2 = "tb_1:12";

    private static final String applicationData = "{\"data\":\"test\"}";

    private static DefaultCore core;

    private static final Configuration CONFIG = ConfigurationFactory.getInstance();

    private static RemotingServer remotingServer;

    @BeforeAll
    public static void beforeClass(ApplicationContext context) throws Exception {
        EnhancedServiceLoader.unload(AbstractCore.class);
        XID.setIpAddress(NetUtil.getLocalIp());
        remotingServer = new MockServerMessageSender();
        defaultCoordinator = DefaultCoordinator.getInstance(remotingServer);
        defaultCoordinator.setRemotingServer(remotingServer);
        core = new DefaultCore(remotingServer);
    }

    @BeforeEach
    public void tearUp() throws IOException {
        deleteAndCreateDataFile();
        // 每次测试前重新初始化 core 以清除之前的 mock
        core = new DefaultCore(remotingServer);
    }

    @Test
    public void branchCommit() throws TransactionException {
        BranchStatus result = null;
        String xid = null;
        GlobalSession globalSession = null;
        try {
            xid = core.begin(applicationId, txServiceGroup, txName, timeout);
            Long branchId = core.branchRegister(BranchType.AT, resourceId, clientId, xid, applicationData, lockKeys_1);
            globalSession = SessionHolder.findGlobalSession(xid);
            result = core.branchCommit(globalSession, globalSession.getBranch(branchId));
        } catch (TransactionException e) {
            Assertions.fail(e.getMessage());
        }
        Assertions.assertEquals(result, BranchStatus.PhaseTwo_Committed);
        globalSession = SessionHolder.findGlobalSession(xid);
        Assertions.assertNotNull(globalSession);
        globalSession.end();
    }

    @Disabled
    @ParameterizedTest
    @MethodSource("xidAndBranchIdProviderForRollback")
    public void branchRollback(String xid, Long branchId) {
        BranchStatus result = null;
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        try {
            result = core.branchRollback(globalSession, globalSession.getBranch(branchId));
        } catch (TransactionException e) {
            Assertions.fail(e.getMessage());
        }
        Assertions.assertEquals(result, BranchStatus.PhaseTwo_Rollbacked);
    }

    @Test
    public void handleRetryRollbackingTest() throws TransactionException, InterruptedException {

        String xid = core.begin(applicationId, txServiceGroup, txName, 10);
        Long branchId = core.branchRegister(BranchType.AT, "abcd", clientId, xid, applicationData, lockKeys_2);

        Assertions.assertNotNull(branchId);
        Thread.sleep(100);
        defaultCoordinator.timeoutCheck();
        defaultCoordinator.handleRetryRollbacking();

        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        Assertions.assertNull(globalSession);
    }

    @Test
    @EnabledOnJre({JRE.JAVA_8, JRE.JAVA_11
    }) // `ReflectionUtil.modifyStaticFinalField` does not supported java17 and above versions
    public void handleRetryRollbackingTimeOutTest()
            throws TransactionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        String xid = core.begin(applicationId, txServiceGroup, txName, 10);
        Long branchId = core.branchRegister(BranchType.AT, "abcd", clientId, xid, applicationData, lockKeys_2);

        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        Assertions.assertNotNull(globalSession);
        Assertions.assertNotNull(globalSession.getBranchSessions());
        Assertions.assertNotNull(branchId);

        ReflectionUtil.modifyStaticFinalField(defaultCoordinator.getClass(), "MAX_ROLLBACK_RETRY_TIMEOUT", 10L);
        ReflectionUtil.modifyStaticFinalField(
                defaultCoordinator.getClass(), "ROLLBACK_RETRY_TIMEOUT_UNLOCK_ENABLE", false);
        TimeUnit.MILLISECONDS.sleep(100);
        globalSession.queueToRetryRollback();
        defaultCoordinator.handleRetryRollbacking();
        int lockSize = globalSession.getBranchSessions().get(0).getLockHolder().size();
        try {
            Assertions.assertTrue(lockSize > 0);
        } finally {
            globalSession.closeAndClean();
            ReflectionUtil.modifyStaticFinalField(
                    defaultCoordinator.getClass(),
                    "MAX_ROLLBACK_RETRY_TIMEOUT",
                    ConfigurationFactory.getInstance()
                            .getLong(
                                    ConfigurationKeys.MAX_ROLLBACK_RETRY_TIMEOUT,
                                    DefaultValues.DEFAULT_MAX_ROLLBACK_RETRY_TIMEOUT));
        }
    }

    @Test
    @EnabledOnJre({JRE.JAVA_8, JRE.JAVA_11
    }) // `ReflectionUtil.modifyStaticFinalField` does not supported java17 and above versions
    public void handleRetryRollbackingTimeOut_unlockTest()
            throws TransactionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        String xid = core.begin(applicationId, txServiceGroup, txName, 10);
        Long branchId = core.branchRegister(BranchType.AT, "abcd", clientId, xid, applicationData, lockKeys_2);

        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        Assertions.assertNotNull(globalSession);
        Assertions.assertNotNull(globalSession.getBranchSessions());
        Assertions.assertNotNull(branchId);

        ReflectionUtil.modifyStaticFinalField(defaultCoordinator.getClass(), "MAX_ROLLBACK_RETRY_TIMEOUT", 10L);
        ReflectionUtil.modifyStaticFinalField(
                defaultCoordinator.getClass(), "ROLLBACK_RETRY_TIMEOUT_UNLOCK_ENABLE", true);
        TimeUnit.MILLISECONDS.sleep(100);

        globalSession.queueToRetryRollback();
        defaultCoordinator.handleRetryRollbacking();

        int lockSize = globalSession.getBranchSessions().get(0).getLockHolder().size();
        try {
            Assertions.assertEquals(0, lockSize);
        } finally {
            globalSession.closeAndClean();
            ReflectionUtil.modifyStaticFinalField(
                    defaultCoordinator.getClass(),
                    "MAX_ROLLBACK_RETRY_TIMEOUT",
                    ConfigurationFactory.getInstance()
                            .getLong(
                                    ConfigurationKeys.MAX_ROLLBACK_RETRY_TIMEOUT,
                                    DefaultValues.DEFAULT_MAX_ROLLBACK_RETRY_TIMEOUT));
        }
    }

    @AfterAll
    public static void afterClass() throws Exception {

        Collection<GlobalSession> globalSessions =
                SessionHolder.getRootSessionManager().allSessions();
        Collection<GlobalSession> asyncGlobalSessions =
                SessionHolder.getRootSessionManager().allSessions();
        for (GlobalSession asyncGlobalSession : asyncGlobalSessions) {
            asyncGlobalSession.closeAndClean();
        }
        for (GlobalSession globalSession : globalSessions) {
            globalSession.closeAndClean();
        }
    }

    private static void deleteAndCreateDataFile() throws IOException {
        StoreUtil.deleteDataFile();
        SessionHolder.init(SessionMode.FILE);
    }

    @AfterEach
    public void tearDown() throws IOException {
        MetricsManager.get().getRegistry().clearUp();
        StoreUtil.deleteDataFile();
    }

    static Stream<Arguments> xidAndBranchIdProviderForRollback() throws Exception {
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        Long branchId = core.branchRegister(BranchType.AT, resourceId, clientId, xid, applicationData, lockKeys_2);
        return Stream.of(Arguments.of(xid, branchId));
    }

    @Test
    public void getInstanceSingletonTest() {
        DefaultCoordinator instance1 = DefaultCoordinator.getInstance();
        DefaultCoordinator instance2 = DefaultCoordinator.getInstance();
        Assertions.assertSame(instance1, instance2);
    }

    @Test
    public void doGlobalCommitNullSessionTest() throws TransactionException {
        boolean result = defaultCoordinator.doGlobalCommit(null, false);
        Assertions.assertTrue(result);
    }

    @Test
    public void doGlobalRollbackNullSessionTest() throws TransactionException {
        boolean result = defaultCoordinator.doGlobalRollback(null, false);
        Assertions.assertTrue(result);
    }

    @Test
    public void doBranchDeleteNullSessionTest() throws TransactionException {
        Boolean result = defaultCoordinator.doBranchDelete(null, null);
        Assertions.assertTrue(result);
    }

    @Test
    public void doBranchDeleteNullBranchTest() throws TransactionException {
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        Boolean result = defaultCoordinator.doBranchDelete(globalSession, null);
        Assertions.assertTrue(result);

        globalSession.end();
    }

    @Test
    public void timeoutCheckNoTimeoutTest() throws TransactionException {
        String xid = core.begin(applicationId, txServiceGroup, txName, 30000);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        Assertions.assertNotNull(globalSession);

        defaultCoordinator.timeoutCheck();

        GlobalSession afterCheck = SessionHolder.findGlobalSession(xid);
        Assertions.assertNotNull(afterCheck);
        Assertions.assertEquals(GlobalStatus.Begin, afterCheck.getStatus());

        globalSession.end();
    }

    @Test
    public void handleRetryCommittingNoSessionsTest() {
        defaultCoordinator.handleRetryCommitting();
    }

    @Test
    public void handleAsyncCommittingNoSessionsTest() {
        defaultCoordinator.handleAsyncCommitting();
    }

    @Test
    public void undoLogDelete_NoChannelsTest() {
        defaultCoordinator.undoLogDelete();
    }

    @Test
    public void onRequestValidRequestTest() {
        GlobalBeginRequest request = new GlobalBeginRequest();
        request.setTransactionName("test_tx");
        request.setTimeout(3000);

        RpcContext rpcContext = new RpcContext();
        rpcContext.setApplicationId(applicationId);
        rpcContext.setTransactionServiceGroup(txServiceGroup);
        rpcContext.setClientId(clientId);

        AbstractResultMessage response = defaultCoordinator.onRequest(request, rpcContext);
        Assertions.assertNotNull(response);
        Assertions.assertTrue(response instanceof GlobalBeginResponse);
    }

    @Test
    public void onResponseValidResponseTest() {
        GlobalBeginResponse response = new GlobalBeginResponse();
        response.setXid("test_xid");
        RpcContext rpcContext = new RpcContext();

        defaultCoordinator.onResponse(response, rpcContext);
    }

    @Test
    public void doBranchReportTest() throws TransactionException {
        // 创建全局事务和分支（不使用锁以避免冲突）
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        Long branchId =
                core.branchRegister(BranchType.AT, "resource_branch_report", clientId, xid, applicationData, null);

        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        Assertions.assertNotNull(globalSession);
        Assertions.assertNotNull(globalSession.getBranch(branchId));

        // 创建 BranchReportRequest
        BranchReportRequest request = new BranchReportRequest();
        request.setXid(xid);
        request.setBranchId(branchId);
        request.setBranchType(BranchType.AT);
        request.setStatus(BranchStatus.PhaseOne_Done);
        request.setApplicationData(applicationData);

        // 执行测试
        RpcContext rpcContext = new RpcContext();
        rpcContext.setApplicationId(applicationId);
        rpcContext.setTransactionServiceGroup(txServiceGroup);
        rpcContext.setClientId(clientId);

        BranchReportResponse response = defaultCoordinator.handle(request, rpcContext);

        // 验证结果
        Assertions.assertNotNull(response);

        // 清理
        globalSession.end();
    }

    @Test
    public void doLockCheckTest() throws TransactionException {
        // 创建带锁的全局事务，使用独特的 resourceId 避免冲突
        String testResourceId = "resource_lock_check";
        String testLockKey1 = "lock_check:1";
        String testLockKey2 = "lock_check:2";

        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        Long branchId =
                core.branchRegister(BranchType.AT, testResourceId, clientId, xid, applicationData, testLockKey1);

        Assertions.assertNotNull(branchId);

        // 测试可锁定场景（不同的lockKey）
        GlobalLockQueryRequest request1 = new GlobalLockQueryRequest();
        request1.setXid(xid);
        request1.setBranchType(BranchType.AT);
        request1.setResourceId(testResourceId);
        request1.setLockKey(testLockKey2);

        RpcContext rpcContext = new RpcContext();
        rpcContext.setApplicationId(applicationId);
        rpcContext.setTransactionServiceGroup(txServiceGroup);
        rpcContext.setClientId(clientId);

        GlobalLockQueryResponse response1 = defaultCoordinator.handle(request1, rpcContext);

        Assertions.assertNotNull(response1);
        Assertions.assertTrue(response1.isLockable());

        // 测试不可锁定场景（相同的lockKey）
        GlobalLockQueryRequest request2 = new GlobalLockQueryRequest();
        request2.setBranchType(BranchType.AT);
        request2.setResourceId(testResourceId);
        request2.setLockKey(testLockKey1);

        GlobalLockQueryResponse response2 = defaultCoordinator.handle(request2, rpcContext);

        Assertions.assertNotNull(response2);
        Assertions.assertFalse(response2.isLockable());

        // 清理
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        globalSession.end();
    }

    @Test
    public void doBranchRemoveAsyncNullSessionTest() {
        // 验证空会话不会抛出异常
        Assertions.assertDoesNotThrow(() -> defaultCoordinator.doBranchRemoveAsync(null, null));
    }

    @Test
    public void doBranchRemoveAllAsyncNullSessionTest() {
        // 验证空会话不会抛出异常
        Assertions.assertDoesNotThrow(() -> defaultCoordinator.doBranchRemoveAllAsync(null));
    }

    @Test
    public void branchRemoveTaskConstructorWithNullBranchTest() throws TransactionException {
        // 创建全局会话
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 测试用 null branchSession 创建 BranchRemoveTask 应该抛出异常
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new DefaultCoordinator.BranchRemoveTask(globalSession, null);
        });

        // 清理
        globalSession.end();
    }

    @Test
    public void branchRemoveTaskRunWithSingleBranchTest() throws TransactionException, InterruptedException {
        // 创建全局会话和分支（不使用锁以避免冲突）
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        Long branchId =
                core.branchRegister(BranchType.AT, "resource_remove_single", clientId, xid, applicationData, null);

        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        BranchSession branchSession = globalSession.getBranch(branchId);

        Assertions.assertNotNull(branchSession);
        Assertions.assertEquals(1, globalSession.getBranchSessions().size());

        // 创建并执行 BranchRemoveTask
        DefaultCoordinator.BranchRemoveTask task =
                new DefaultCoordinator.BranchRemoveTask(globalSession, branchSession);
        task.run();

        // 验证分支已被删除
        Assertions.assertNull(globalSession.getBranch(branchId));
        Assertions.assertEquals(0, globalSession.getBranchSessions().size());

        // 清理
        globalSession.end();
    }

    @Test
    public void branchRemoveTaskRunWithAllBranchesTest() throws TransactionException, InterruptedException {
        // 创建全局会话和多个分支（不使用锁以避免冲突）
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        core.branchRegister(BranchType.AT, "resource_remove_all_1", clientId, xid, applicationData, null);
        core.branchRegister(BranchType.AT, "resource_remove_all_2", clientId, xid, applicationData, null);

        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        Assertions.assertEquals(2, globalSession.getBranchSessions().size());

        // 创建并执行 BranchRemoveTask（删除所有分支）
        DefaultCoordinator.BranchRemoveTask task = new DefaultCoordinator.BranchRemoveTask(globalSession);
        task.run();

        // 验证所有分支已被删除
        Assertions.assertTrue(globalSession.getBranchSessions().isEmpty());

        // 清理
        globalSession.end();
    }

    @Test
    public void branchRemoveTaskRunWithNullGlobalSessionTest() {
        // 测试 null globalSession 不会抛出异常
        DefaultCoordinator.BranchRemoveTask task = new DefaultCoordinator.BranchRemoveTask(null);
        Assertions.assertDoesNotThrow(() -> task.run());
    }

    @Test
    public void handleCommittingByScheduledNoSessionsTest() {
        // 测试没有 Committing 状态的会话时不会抛出异常
        Assertions.assertDoesNotThrow(() -> defaultCoordinator.handleCommittingByScheduled());
    }

    @Test
    public void handleCommittingByScheduledWithSessionTest() throws TransactionException, InterruptedException {
        // 创建全局事务
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        core.branchRegister(BranchType.AT, "resource_committing_scheduled", clientId, xid, applicationData, null);

        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        Assertions.assertNotNull(globalSession);

        // 将会话状态改为 Committing
        globalSession.changeGlobalStatus(GlobalStatus.Committing);

        // 执行调度方法
        Assertions.assertDoesNotThrow(() -> defaultCoordinator.handleCommittingByScheduled());

        // 清理
        GlobalSession session = SessionHolder.findGlobalSession(xid);
        if (session != null) {
            session.end();
        }
    }

    @Test
    public void handleRollbackingByScheduledNoSessionsTest() {
        // 测试没有 Rollbacking 状态的会话时不会抛出异常
        Assertions.assertDoesNotThrow(() -> defaultCoordinator.handleRollbackingByScheduled());
    }

    @Test
    public void handleRollbackingByScheduledWithSessionTest() throws TransactionException, InterruptedException {
        // 创建全局事务
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        core.branchRegister(BranchType.AT, "resource_rollbacking_scheduled", clientId, xid, applicationData, null);

        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        Assertions.assertNotNull(globalSession);

        // 将会话状态改为 Rollbacking
        globalSession.changeGlobalStatus(GlobalStatus.Rollbacking);

        // 执行调度方法
        Assertions.assertDoesNotThrow(() -> defaultCoordinator.handleRollbackingByScheduled());

        // 清理
        GlobalSession session = SessionHolder.findGlobalSession(xid);
        if (session != null) {
            session.end();
        }
    }

    @Test
    public void handleEndStatesByScheduledNoSessionsTest() {
        // 测试没有结束状态的会话时不会抛出异常
        Assertions.assertDoesNotThrow(() -> defaultCoordinator.handleEndStatesByScheduled());
    }

    @Test
    public void handleEndStatesByScheduledWithCommittedSessionTest() throws TransactionException, InterruptedException {
        // 创建全局事务
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        core.branchRegister(BranchType.AT, "resource_end_committed", clientId, xid, applicationData, null);

        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        Assertions.assertNotNull(globalSession);

        // 将会话状态改为 Committed
        globalSession.changeGlobalStatus(GlobalStatus.Committed);
        // 清空分支以便可以进入 end 状态处理
        while (!globalSession.getBranchSessions().isEmpty()) {
            globalSession.removeBranch(globalSession.getBranchSessions().get(0));
        }

        // 执行调度方法
        Assertions.assertDoesNotThrow(() -> defaultCoordinator.handleEndStatesByScheduled());

        // 清理
        GlobalSession session = SessionHolder.findGlobalSession(xid);
        if (session != null && session.getStatus() != GlobalStatus.Finished) {
            session.end();
        }
    }

    @Test
    public void handleEndStatesByScheduledWithRollbackedSessionTest()
            throws TransactionException, InterruptedException {
        // 创建全局事务
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        core.branchRegister(BranchType.AT, "resource_end_rollbacked", clientId, xid, applicationData, null);

        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        Assertions.assertNotNull(globalSession);

        // 将会话状态改为 Rollbacked
        globalSession.changeGlobalStatus(GlobalStatus.Rollbacked);
        // 清空分支以便可以进入 end 状态处理
        while (!globalSession.getBranchSessions().isEmpty()) {
            globalSession.removeBranch(globalSession.getBranchSessions().get(0));
        }

        // 执行调度方法
        Assertions.assertDoesNotThrow(() -> defaultCoordinator.handleEndStatesByScheduled());

        // 清理
        GlobalSession session = SessionHolder.findGlobalSession(xid);
        if (session != null && session.getStatus() != GlobalStatus.Finished) {
            session.end();
        }
    }

    @Test
    public void doBranchDeleteSagaTypeTest() throws TransactionException {
        // 创建 SAGA 类型的全局会话
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建 SAGA 类型的分支会话
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.SAGA);
        branchSession.setResourceId("saga_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // 执行测试
        Boolean result = core.doBranchDelete(globalSession, branchSession);

        // 验证结果 - SAGA 类型应该直接返回 true
        Assertions.assertTrue(result);

        // 清理
        globalSession.end();
    }

    @Test
    public void doBranchDeleteATSuccessTest() throws TransactionException {
        // 创建全局会话
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建 AT 类型的分支会话
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.AT);
        branchSession.setResourceId("at_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // Mock AT Core 返回 PhaseTwo_Committed（AT 删除成功状态）
        AbstractCore mockATCore = mock(AbstractCore.class);
        when(mockATCore.branchDelete(any(GlobalSession.class), any(BranchSession.class)))
                .thenReturn(BranchStatus.PhaseTwo_Committed);
        core.mockCore(BranchType.AT, mockATCore);

        // 执行测试
        Boolean result = core.doBranchDelete(globalSession, branchSession);

        // 验证结果 - AT 分支删除成功应该返回 true
        Assertions.assertTrue(result);

        // 清理
        globalSession.end();
    }

    @Test
    public void doBranchDeleteATFailureTest() throws TransactionException {
        // 创建全局会话
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建 AT 类型的分支会话
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.AT);
        branchSession.setResourceId("at_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // Mock AT Core 返回 PhaseTwo_CommitFailed_Retryable（AT 删除失败状态）
        AbstractCore mockATCore = mock(AbstractCore.class);
        when(mockATCore.branchDelete(any(GlobalSession.class), any(BranchSession.class)))
                .thenReturn(BranchStatus.PhaseTwo_CommitFailed_Retryable);
        core.mockCore(BranchType.AT, mockATCore);

        // 执行测试
        Boolean result = core.doBranchDelete(globalSession, branchSession);

        // 验证结果 - AT 分支删除失败应该返回 false
        Assertions.assertFalse(result);

        // 清理
        globalSession.end();
    }

    @Test
    public void doBranchDeleteTCCSuccessTest() throws TransactionException {
        // 创建全局会话
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建 TCC 类型的分支会话
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.TCC);
        branchSession.setResourceId("tcc_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // Mock TCC Core 返回 PhaseTwo_Rollbacked（TCC 删除成功状态）
        AbstractCore mockTCCCore = mock(AbstractCore.class);
        when(mockTCCCore.branchDelete(any(GlobalSession.class), any(BranchSession.class)))
                .thenReturn(BranchStatus.PhaseTwo_Rollbacked);
        core.mockCore(BranchType.TCC, mockTCCCore);

        // 执行测试
        Boolean result = core.doBranchDelete(globalSession, branchSession);

        // 验证结果 - TCC 分支删除成功应该返回 true
        Assertions.assertTrue(result);

        // 清理
        globalSession.end();
    }

    @Test
    public void doBranchDeleteTCCFailureTest() throws TransactionException {
        // 创建全局会话
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建 TCC 类型的分支会话
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.TCC);
        branchSession.setResourceId("tcc_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // Mock TCC Core 返回 PhaseTwo_RollbackFailed_Retryable（TCC 删除失败状态）
        AbstractCore mockTCCCore = mock(AbstractCore.class);
        when(mockTCCCore.branchDelete(any(GlobalSession.class), any(BranchSession.class)))
                .thenReturn(BranchStatus.PhaseTwo_RollbackFailed_Retryable);
        core.mockCore(BranchType.TCC, mockTCCCore);

        // 执行测试
        Boolean result = core.doBranchDelete(globalSession, branchSession);

        // 验证结果 - TCC 分支删除失败应该返回 false
        Assertions.assertFalse(result);

        // 清理
        globalSession.end();
    }

    @Test
    public void doBranchDeleteXASuccessTest() throws TransactionException {
        // 创建全局会话
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建 XA 类型的分支会话
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.XA);
        branchSession.setResourceId("xa_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // Mock XA Core 返回 PhaseTwo_Rollbacked（XA 删除成功状态）
        AbstractCore mockXACore = mock(AbstractCore.class);
        when(mockXACore.branchDelete(any(GlobalSession.class), any(BranchSession.class)))
                .thenReturn(BranchStatus.PhaseTwo_Rollbacked);
        core.mockCore(BranchType.XA, mockXACore);

        // 执行测试
        Boolean result = core.doBranchDelete(globalSession, branchSession);

        // 验证结果 - XA 分支删除成功应该返回 true
        Assertions.assertTrue(result);

        // 清理
        globalSession.end();
    }

    @Test
    @EnabledOnJre({JRE.JAVA_8, JRE.JAVA_11
    }) // `ReflectionUtil.modifyStaticFinalField` does not supported java17 and above versions
    public void doBranchDeleteXAXaerNotaTimeoutTest()
            throws TransactionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        // 创建全局会话，设置较短的超时时间
        String xid = core.begin(applicationId, txServiceGroup, txName, 100);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建 XA 类型的分支会话
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.XA);
        branchSession.setResourceId("xa_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // Mock XA Core 返回 PhaseTwo_RollbackFailed_XAER_NOTA_Retryable
        AbstractCore mockXACore = mock(AbstractCore.class);
        when(mockXACore.branchDelete(any(GlobalSession.class), any(BranchSession.class)))
                .thenReturn(BranchStatus.PhaseTwo_RollbackFailed_XAER_NOTA_Retryable);
        core.mockCore(BranchType.XA, mockXACore);

        try {
            // 临时修改 RETRY_XAER_NOTA_TIMEOUT 为较小值以测试超时
            ReflectionUtil.modifyStaticFinalField(core.getClass(), "RETRY_XAER_NOTA_TIMEOUT", 10);

            // 等待超时
            Thread.sleep(150);

            // 执行测试
            Boolean result = core.doBranchDelete(globalSession, branchSession);

            // 验证结果 - XAER_NOTA 超时应该返回 true
            Assertions.assertTrue(result);
        } finally {
            // 恢复原始值
            ReflectionUtil.modifyStaticFinalField(
                    core.getClass(),
                    "RETRY_XAER_NOTA_TIMEOUT",
                    ConfigurationFactory.getInstance()
                            .getInt(XAER_NOTA_RETRY_TIMEOUT, DefaultValues.DEFAULT_XAER_NOTA_RETRY_TIMEOUT));
            // 清理
            globalSession.end();
        }
    }

    @Test
    public void doBranchDeleteXAXaerNotaNoTimeoutTest() throws TransactionException {
        // 创建全局会话，设置较长的超时时间
        String xid = core.begin(applicationId, txServiceGroup, txName, 30000);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建 XA 类型的分支会话
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.XA);
        branchSession.setResourceId("xa_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // Mock XA Core 返回 PhaseTwo_RollbackFailed_XAER_NOTA_Retryable
        AbstractCore mockXACore = mock(AbstractCore.class);
        when(mockXACore.branchDelete(any(GlobalSession.class), any(BranchSession.class)))
                .thenReturn(BranchStatus.PhaseTwo_RollbackFailed_XAER_NOTA_Retryable);
        core.mockCore(BranchType.XA, mockXACore);

        // 执行测试
        Boolean result = core.doBranchDelete(globalSession, branchSession);

        // 验证结果 - XAER_NOTA 未超时应该返回 false
        Assertions.assertFalse(result);

        // 清理
        globalSession.end();
    }

    @Test
    public void doBranchDeleteXAFailureTest() throws TransactionException {
        // 创建全局会话
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建 XA 类型的分支会话
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.XA);
        branchSession.setResourceId("xa_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // Mock XA Core 返回 PhaseTwo_RollbackFailed_Retryable（XA 删除失败状态）
        AbstractCore mockXACore = mock(AbstractCore.class);
        when(mockXACore.branchDelete(any(GlobalSession.class), any(BranchSession.class)))
                .thenReturn(BranchStatus.PhaseTwo_RollbackFailed_Retryable);
        core.mockCore(BranchType.XA, mockXACore);

        // 执行测试
        Boolean result = core.doBranchDelete(globalSession, branchSession);

        // 验证结果 - XA 分支删除失败应该返回 false
        Assertions.assertFalse(result);

        // 清理
        globalSession.end();
    }

    @Test
    public void doBranchDeleteUnretryableTest() throws TransactionException {
        // 创建全局会话
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建 AT 类型的分支会话
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.AT);
        branchSession.setResourceId("at_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // Mock Core 返回 PhaseTwo_RollbackFailed_Unretryable（不可重试状态）
        AbstractCore mockATCore = mock(AbstractCore.class);
        when(mockATCore.branchDelete(any(GlobalSession.class), any(BranchSession.class)))
                .thenReturn(BranchStatus.PhaseTwo_RollbackFailed_Unretryable);
        core.mockCore(BranchType.AT, mockATCore);

        // 执行测试
        Boolean result = core.doBranchDelete(globalSession, branchSession);

        // 验证结果 - 不可重试状态应该返回 true（停止重试并删除）
        Assertions.assertTrue(result);

        // 清理
        globalSession.end();
    }

    @Test
    public void doBranchDeleteGeneralFailureTest() throws TransactionException {
        // 创建全局会话
        String xid = core.begin(applicationId, txServiceGroup, txName, timeout);
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);

        // 创建一个未知类型的分支会话（使用 AT 类型但返回不匹配的状态）
        BranchSession branchSession = new BranchSession();
        branchSession.setXid(xid);
        branchSession.setBranchId(1L);
        branchSession.setBranchType(BranchType.AT);
        branchSession.setResourceId("at_resource");
        branchSession.setApplicationData(applicationData);

        globalSession.addBranch(branchSession);

        // Mock Core 返回 PhaseOne_Failed（通用失败状态）
        AbstractCore mockATCore = mock(AbstractCore.class);
        when(mockATCore.branchDelete(any(GlobalSession.class), any(BranchSession.class)))
                .thenReturn(BranchStatus.PhaseOne_Failed);
        core.mockCore(BranchType.AT, mockATCore);

        // 执行测试
        Boolean result = core.doBranchDelete(globalSession, branchSession);

        // 验证结果 - 通用失败场景应该返回 false
        Assertions.assertFalse(result);

        // 清理
        globalSession.end();
    }

    public static class MockServerMessageSender implements RemotingServer {

        @Override
        public Object sendSyncRequest(String resourceId, String clientId, Object message, boolean tryOtherApp)
                throws TimeoutException {
            if (message instanceof BranchCommitRequest) {
                final BranchCommitResponse branchCommitResponse = new BranchCommitResponse();
                branchCommitResponse.setBranchStatus(BranchStatus.PhaseTwo_Committed);
                return branchCommitResponse;
            } else if (message instanceof BranchRollbackRequest) {
                final BranchRollbackResponse branchRollbackResponse = new BranchRollbackResponse();
                branchRollbackResponse.setBranchStatus(BranchStatus.PhaseTwo_Rollbacked);
                return branchRollbackResponse;
            } else {
                return null;
            }
        }

        @Override
        public Object sendSyncRequest(Channel clientChannel, Object message) throws TimeoutException {
            return null;
        }

        @Override
        public void sendAsyncRequest(Channel channel, Object msg) {}

        @Override
        public void sendAsyncResponse(RpcMessage request, Channel channel, Object msg) {}

        @Override
        public void registerProcessor(int messageType, RemotingProcessor processor, ExecutorService executor) {}
    }
}
