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
import org.apache.seata.common.result.SingleResult;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.console.entity.param.GlobalLockParam;
import org.apache.seata.server.console.entity.vo.GlobalLockVO;
import org.apache.seata.server.lock.LockManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test for GlobalLockRedisServiceImpl
 * Integration test that requires real Redis instance
 * Set -DredisCaseEnabled=true to run these tests
 */
@EnabledIfSystemProperty(named = "redisCaseEnabled", matches = "true")
@TestPropertySource(properties = {"lockMode=redis", "sessionMode=file"})
class GlobalLockRedisServiceImplTest extends BaseSpringBootTest {

    @Autowired
    private GlobalLockRedisServiceImpl globalLockRedisService;

    @Autowired
    private LockManager lockManager;

    @BeforeEach
    void setUp() {
        // Inject lockManager to service (already autowired by Spring)
        ReflectionTestUtils.setField(globalLockRedisService, "lockManager", lockManager);
    }

    @Test
    void queryByXidSuccessTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setXid("test-xid-001");

        PageResult<GlobalLockVO> result = globalLockRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(2, result.getData().size());
        Assertions.assertEquals(2, result.getTotal());
    }

    @Test
    void queryByXidEmptyResultTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setXid("test-xid-002");

        PageResult<GlobalLockVO> result = globalLockRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(0, result.getData().size());
        Assertions.assertEquals(0, result.getTotal());
    }

    @Test
    void queryByRowKeySuccessTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setTableName("tb_order");
        param.setPk("1");
        param.setResourceId("jdbc:mysql://localhost:3306/test");

        PageResult<GlobalLockVO> result = globalLockRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(1, result.getData().size());
        Assertions.assertEquals(1, result.getTotal());
        Assertions.assertEquals("test-xid-001", result.getData().get(0).getXid());
        Assertions.assertEquals("tb_order", result.getData().get(0).getTableName());
    }

    @Test
    void queryByRowKeyEmptyResultTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setTableName("tb_order");
        param.setPk("999");
        param.setResourceId("jdbc:mysql://localhost:3306/test");

        PageResult<GlobalLockVO> result = globalLockRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(0, result.getData().size());
        Assertions.assertEquals(0, result.getTotal());
    }

    @Test
    void queryParameterErrorTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setPageNum(1);
        param.setPageSize(10);
        param.setTableName("tb_order");

        PageResult<GlobalLockVO> result = globalLockRedisService.query(param);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isSuccess());
        Assertions.assertEquals(FrameworkErrorCode.ParameterRequired.getErrCode(), result.getCode());
        Assertions.assertTrue(result.getMessage().contains("only three parameters"));
    }

    @Test
    void queryInvalidPageNumTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setPageNum(0);
        param.setPageSize(10);
        param.setXid("test-xid-001");

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, () -> globalLockRedisService.query(param));
        Assertions.assertNotNull(exception.getMessage());
    }

    @Test
    void queryInvalidPageSizeTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setPageNum(1);
        param.setPageSize(0);
        param.setXid("test-xid-001");

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, () -> globalLockRedisService.query(param));
        Assertions.assertNotNull(exception.getMessage());
    }

    @Test
    void deleteLockSuccessTest() throws TransactionException {
        GlobalLockParam param = new GlobalLockParam();
        param.setXid("test-xid-001");
        param.setBranchId("2001");
        param.setTableName("tb_order");
        param.setPk("1");
        param.setResourceId("jdbc:mysql://localhost:3306/test");

        SingleResult<Void> result = globalLockRedisService.deleteLock(param);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
    }

    @Test
    void deleteLockMissingXidTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setBranchId("2001");
        param.setTableName("tb_order");
        param.setPk("1");
        param.setResourceId("jdbc:mysql://localhost:3306/test");

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, () -> globalLockRedisService.deleteLock(param));
        Assertions.assertNotNull(exception.getMessage());
    }

    @Test
    void deleteLockMissingBranchIdTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setXid("test-xid-001");
        param.setTableName("tb_order");
        param.setPk("1");
        param.setResourceId("jdbc:mysql://localhost:3306/test");

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, () -> globalLockRedisService.deleteLock(param));
        Assertions.assertNotNull(exception.getMessage());
    }

    @Test
    void deleteLockMissingTableNameTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setXid("test-xid-001");
        param.setBranchId("2001");
        param.setPk("1");
        param.setResourceId("jdbc:mysql://localhost:3306/test");

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, () -> globalLockRedisService.deleteLock(param));
        Assertions.assertNotNull(exception.getMessage());
    }

    @Test
    void deleteLockMissingPkTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setXid("test-xid-001");
        param.setBranchId("2001");
        param.setTableName("tb_order");
        param.setResourceId("jdbc:mysql://localhost:3306/test");

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, () -> globalLockRedisService.deleteLock(param));
        Assertions.assertNotNull(exception.getMessage());
    }

    @Test
    void deleteLockMissingResourceIdTest() {
        GlobalLockParam param = new GlobalLockParam();
        param.setXid("test-xid-001");
        param.setBranchId("2001");
        param.setTableName("tb_order");
        param.setPk("1");

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, () -> globalLockRedisService.deleteLock(param));
        Assertions.assertNotNull(exception.getMessage());
    }
}
