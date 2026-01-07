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
package org.apache.seata.tcc;

import org.apache.seata.common.ConfigurationKeys;
import org.apache.seata.common.ConfigurationTestHelper;
import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.core.model.TransactionManager;
import org.apache.seata.core.rpc.netty.RmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.mockserver.ProtocolTestConstants;
import org.apache.seata.core.rpc.netty.mockserver.RmClientTest;
import org.apache.seata.core.rpc.netty.mockserver.TmClientTest;
import org.apache.seata.mockserver.MockServer;
import org.apache.seata.rm.DefaultResourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCC Business Parameter Integration Test
 * 
 * Tests TCC transaction business parameter passing and context handling.
 */
public class TccBusinessParamIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TccBusinessParamIntegrationTest.class);

    private static final String RESOURCE_ID = "mock-action";

    private TransactionManager tm;
    private DefaultResourceManager rm;

    @BeforeAll
    public static void beforeAll() {
        ConfigurationFactory.reload();
        ConfigurationTestHelper.putConfig(
                ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL, 
                String.valueOf(ProtocolTestConstants.MOCK_SERVER_PORT));
        MockServer.start(ProtocolTestConstants.MOCK_SERVER_PORT);
        TmNettyRemotingClient.getInstance().destroy();
        RmNettyRemotingClient.getInstance().destroy();
    }

    @AfterAll
    public static void afterAll() {
        ConfigurationTestHelper.removeConfig(ConfigurationKeys.SERVER_SERVICE_PORT_CAMEL);
        TmNettyRemotingClient.getInstance().destroy();
        RmNettyRemotingClient.getInstance().destroy();
    }

    @BeforeEach
    public void setUp() {
        tm = TmClientTest.getTm();
        rm = RmClientTest.getRm(RESOURCE_ID);
    }

    /**
     * Test passing simple JSON parameters
     */
    @Test
    public void testSimpleJsonParams() throws TransactionException {
        String xid = tm.begin(
                ProtocolTestConstants.APPLICATION_ID,
                ProtocolTestConstants.SERVICE_GROUP,
                "json-params-test",
                60000);
        
        String applicationData = "{\"orderId\":\"123456\",\"amount\":100.50,\"userId\":\"user001\"}";
        
        Long branchId = rm.branchRegister(
                BranchType.TCC,
                RESOURCE_ID,
                "1",
                xid,
                applicationData,
                "1");
        
        Assertions.assertTrue(branchId > 0);
        
        GlobalStatus status = tm.commit(xid);
        Assertions.assertEquals(GlobalStatus.Committed, status);
        
        LOGGER.info("Simple JSON params test passed with applicationData: {}", applicationData);
    }

    /**
     * Test passing complex nested JSON parameters
     */
    @Test
    public void testComplexNestedParams() throws TransactionException {
        String xid = tm.begin(
                ProtocolTestConstants.APPLICATION_ID,
                ProtocolTestConstants.SERVICE_GROUP,
                "nested-params-test",
                60000);
        
        String applicationData = "{" +
                "\"order\":{\"id\":\"ORD001\",\"items\":[{\"sku\":\"SKU001\",\"qty\":2}]}," +
                "\"customer\":{\"name\":\"John\",\"address\":{\"city\":\"Beijing\"}}" +
                "}";
        
        Long branchId = rm.branchRegister(
                BranchType.TCC,
                RESOURCE_ID,
                "1",
                xid,
                applicationData,
                "1");
        
        Assertions.assertTrue(branchId > 0);
        
        GlobalStatus status = tm.commit(xid);
        Assertions.assertEquals(GlobalStatus.Committed, status);
        
        LOGGER.info("Complex nested params test passed");
    }

    /**
     * Test passing empty parameters
     */
    @Test
    public void testEmptyParams() throws TransactionException {
        String xid = tm.begin(
                ProtocolTestConstants.APPLICATION_ID,
                ProtocolTestConstants.SERVICE_GROUP,
                "empty-params-test",
                60000);
        
        Long branchId = rm.branchRegister(
                BranchType.TCC,
                RESOURCE_ID,
                "1",
                xid,
                "",  // Empty application data
                "1");
        
        Assertions.assertTrue(branchId > 0);
        
        GlobalStatus status = tm.commit(xid);
        Assertions.assertEquals(GlobalStatus.Committed, status);
    }

    /**
     * Test passing null parameters
     */
    @Test
    public void testNullParams() throws TransactionException {
        String xid = tm.begin(
                ProtocolTestConstants.APPLICATION_ID,
                ProtocolTestConstants.SERVICE_GROUP,
                "null-params-test",
                60000);
        
        Long branchId = rm.branchRegister(
                BranchType.TCC,
                RESOURCE_ID,
                "1",
                xid,
                null,  // Null application data
                "1");
        
        Assertions.assertTrue(branchId > 0);
        
        GlobalStatus status = tm.commit(xid);
        Assertions.assertEquals(GlobalStatus.Committed, status);
    }

    /**
     * Test passing special characters in parameters
     */
    @Test
    public void testSpecialCharacterParams() throws TransactionException {
        String xid = tm.begin(
                ProtocolTestConstants.APPLICATION_ID,
                ProtocolTestConstants.SERVICE_GROUP,
                "special-char-test",
                60000);
        
        String applicationData = "{\"message\":\"Hello, 世界! \\\"quoted\\\" & <tag>\"}";
        
        Long branchId = rm.branchRegister(
                BranchType.TCC,
                RESOURCE_ID,
                "1",
                xid,
                applicationData,
                "1");
        
        Assertions.assertTrue(branchId > 0);
        
        GlobalStatus status = tm.commit(xid);
        Assertions.assertEquals(GlobalStatus.Committed, status);
        
        LOGGER.info("Special character params test passed");
    }
}
