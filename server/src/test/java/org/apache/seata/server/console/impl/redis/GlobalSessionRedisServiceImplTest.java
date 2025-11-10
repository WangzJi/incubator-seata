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
package org.apache.seata.server.console.impl.redis;

import org.apache.seata.common.exception.FrameworkErrorCode;
import org.apache.seata.common.result.PageResult;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.console.entity.param.GlobalSessionParam;
import org.apache.seata.server.console.entity.vo.GlobalSessionVO;
import org.apache.seata.server.session.GlobalSession;
import org.apache.seata.server.storage.redis.store.RedisTransactionStoreManager;
import org.apache.seata.server.storage.redis.store.RedisTransactionStoreManagerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for GlobalSessionRedisServiceImpl
 * Integration test that requires real Redis instance
 * Set -DredisCaseEnabled=true to run these tests
 */
@EnabledIfSystemProperty(named = "redisCaseEnabled", matches = "true")
@TestPropertySource(properties = {"sessionMode=redis", "lockMode=file"})
class GlobalSessionRedisServiceImplTest extends BaseSpringBootTest {

    @Autowired
    private GlobalSessionRedisServiceImpl globalSessionRedisService;

    private RedisTransactionStoreManager redisTransactionStoreManager;

    @BeforeEach
    void setUp() {
        redisTransactionStoreManager = RedisTransactionStoreManagerFactory.getInstance();
    }

    @Test
    void testQuery_allSessions() {
        GlobalSessionParam param = new GlobalSessionParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setWithBranch(false);

        List<GlobalSession> globalSessions = new ArrayList<>();
        GlobalSession session1 = mock(GlobalSession.class);
        when(session1.getXid()).thenReturn("test-xid-001");
        when(session1.getStatus()).thenReturn(GlobalStatus.Begin);
        when(session1.getApplicationId()).thenReturn("test-app");
        when(session1.getTransactionServiceGroup()).thenReturn("default_tx_group");
        when(session1.getTransactionName()).thenReturn("test-transaction");
        when(session1.getTimeout()).thenReturn(60000);
        when(session1.getBeginTime()).thenReturn(System.currentTimeMillis());

        GlobalSession session2 = mock(GlobalSession.class);
        when(session2.getXid()).thenReturn("test-xid-002");
        when(session2.getStatus()).thenReturn(GlobalStatus.Begin);
        when(session2.getApplicationId()).thenReturn("test-app");
        when(session2.getTransactionServiceGroup()).thenReturn("default_tx_group");
        when(session2.getTransactionName()).thenReturn("test-transaction-2");
        when(session2.getTimeout()).thenReturn(60000);
        when(session2.getBeginTime()).thenReturn(System.currentTimeMillis());

        globalSessions.add(session1);
        globalSessions.add(session2);

        PageResult<GlobalSessionVO> result = globalSessionRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(2, result.getData().size());
        Assertions.assertEquals(2, result.getTotal());
        Assertions.assertEquals(1, result.getPageNum());
        Assertions.assertEquals(10, result.getPageSize());
    }

    @Test
    void testQuery_byXid() {
        GlobalSessionParam param = new GlobalSessionParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setXid("test-xid-001");
        param.setWithBranch(false);

        List<GlobalSession> globalSessions = new ArrayList<>();
        GlobalSession session = mock(GlobalSession.class);
        when(session.getXid()).thenReturn("test-xid-001");
        when(session.getStatus()).thenReturn(GlobalStatus.Begin);
        when(session.getApplicationId()).thenReturn("test-app");
        when(session.getTransactionServiceGroup()).thenReturn("default_tx_group");
        when(session.getTransactionName()).thenReturn("test-transaction");
        when(session.getTimeout()).thenReturn(60000);
        when(session.getBeginTime()).thenReturn(System.currentTimeMillis());

        globalSessions.add(session);

        PageResult<GlobalSessionVO> result = globalSessionRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(1, result.getData().size());
        Assertions.assertEquals(1, result.getTotal());
        Assertions.assertEquals("test-xid-001", result.getData().get(0).getXid());
    }

    @Test
    void testQuery_byStatus() {
        GlobalSessionParam param = new GlobalSessionParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setStatus(GlobalStatus.Begin.getCode());
        param.setWithBranch(false);

        List<GlobalSession> globalSessions = new ArrayList<>();
        GlobalSession session = mock(GlobalSession.class);
        when(session.getXid()).thenReturn("test-xid-001");
        when(session.getStatus()).thenReturn(GlobalStatus.Begin);
        when(session.getApplicationId()).thenReturn("test-app");
        when(session.getTransactionServiceGroup()).thenReturn("default_tx_group");
        when(session.getTransactionName()).thenReturn("test-transaction");
        when(session.getTimeout()).thenReturn(60000);
        when(session.getBeginTime()).thenReturn(System.currentTimeMillis());

        globalSessions.add(session);

        PageResult<GlobalSessionVO> result = globalSessionRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(1, result.getData().size());
        Assertions.assertEquals(1, result.getTotal());
    }

    @Test
    void testQuery_byXidAndStatus() {
        GlobalSessionParam param = new GlobalSessionParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setXid("test-xid-001");
        param.setStatus(GlobalStatus.Begin.getCode());
        param.setWithBranch(false);

        List<GlobalSession> globalSessions = new ArrayList<>();
        GlobalSession session = mock(GlobalSession.class);
        when(session.getXid()).thenReturn("test-xid-001");
        when(session.getStatus()).thenReturn(GlobalStatus.Begin);
        when(session.getApplicationId()).thenReturn("test-app");
        when(session.getTransactionServiceGroup()).thenReturn("default_tx_group");
        when(session.getTransactionName()).thenReturn("test-transaction");
        when(session.getTimeout()).thenReturn(60000);
        when(session.getBeginTime()).thenReturn(System.currentTimeMillis());

        globalSessions.add(session);

        PageResult<GlobalSessionVO> result = globalSessionRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(1, result.getData().size());
        Assertions.assertEquals(1, result.getTotal());
    }

    @Test
    void testQuery_emptyResult() {
        GlobalSessionParam param = new GlobalSessionParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setXid("non-existent-test-xid-999");
        param.setWithBranch(false);

        PageResult<GlobalSessionVO> result = globalSessionRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(0, result.getData().size());
        Assertions.assertEquals(0, result.getTotal());
    }

    @Test
    void testQuery_timeRangeNotSupported() {
        GlobalSessionParam param = new GlobalSessionParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setTimeStart(System.currentTimeMillis() - 86400000L);
        param.setTimeEnd(System.currentTimeMillis());

        PageResult<GlobalSessionVO> result = globalSessionRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isSuccess());
        Assertions.assertEquals(FrameworkErrorCode.ParameterRequired.getErrCode(), result.getCode());
        Assertions.assertTrue(result.getMessage().contains("not supported according to time range query"));
    }

    @Test
    void testQuery_withBranch() {
        GlobalSessionParam param = new GlobalSessionParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setXid("test-xid-001");
        param.setWithBranch(true);

        List<GlobalSession> globalSessions = new ArrayList<>();
        GlobalSession session = mock(GlobalSession.class);
        when(session.getXid()).thenReturn("test-xid-001");
        when(session.getStatus()).thenReturn(GlobalStatus.Begin);
        when(session.getApplicationId()).thenReturn("test-app");
        when(session.getTransactionServiceGroup()).thenReturn("default_tx_group");
        when(session.getTransactionName()).thenReturn("test-transaction");
        when(session.getTimeout()).thenReturn(60000);
        when(session.getBeginTime()).thenReturn(System.currentTimeMillis());

        globalSessions.add(session);

        PageResult<GlobalSessionVO> result = globalSessionRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(1, result.getData().size());
    }

    @Test
    void testQuery_invalidPageNum() {
        GlobalSessionParam param = new GlobalSessionParam();
        param.setPageNum(0);
        param.setPageSize(10);

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, () -> globalSessionRedisService.query(param));
        Assertions.assertNotNull(exception.getMessage());
    }

    @Test
    void testQuery_invalidPageSize() {
        GlobalSessionParam param = new GlobalSessionParam();
        param.setPageNum(1);
        param.setPageSize(0);

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, () -> globalSessionRedisService.query(param));
        Assertions.assertNotNull(exception.getMessage());
    }

    @Test
    void testQuery_allSessionsEmptyResult() {
        GlobalSessionParam param = new GlobalSessionParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setWithBranch(false);

        List<GlobalSession> globalSessions = new ArrayList<>();

        PageResult<GlobalSessionVO> result = globalSessionRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(0, result.getData().size());
        Assertions.assertEquals(0, result.getTotal());
    }
}
