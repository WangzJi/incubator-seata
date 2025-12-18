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
package org.apache.seata.benchmark.executor;

import org.apache.seata.benchmark.config.BenchmarkConfig;
import org.apache.seata.benchmark.model.TransactionRecord;
import org.apache.seata.benchmark.saga.BenchmarkServiceInvoker;
import org.apache.seata.benchmark.saga.InventorySagaService;
import org.apache.seata.benchmark.saga.OrderSagaService;
import org.apache.seata.benchmark.saga.PaymentSagaService;
import org.apache.seata.benchmark.saga.SimpleSpelExpressionFactory;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.saga.engine.StateMachineEngine;
import org.apache.seata.saga.engine.config.AbstractStateMachineConfig;
import org.apache.seata.saga.engine.expression.ExpressionFactoryManager;
import org.apache.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import org.apache.seata.saga.statelang.domain.DomainConstants;
import org.apache.seata.saga.statelang.domain.ExecutionStatus;
import org.apache.seata.saga.statelang.domain.StateMachineInstance;
import org.apache.seata.tm.api.GlobalTransaction;
import org.apache.seata.tm.api.GlobalTransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Saga mode transaction executor supporting both mock and real modes
 * - branches == 0: Mock mode (simplified Saga simulation without state machine)
 * - branches > 0: Real mode (state machine engine with compensation support)
 */
public class SagaModeExecutor implements TransactionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SagaModeExecutor.class);
    private static final AtomicLong BUSINESS_KEY_COUNTER = new AtomicLong(0);

    private static final String SIMPLE_SAGA_NAME = "benchmarkSimpleSaga";
    private static final String ORDER_SAGA_NAME = "benchmarkOrderSaga";

    private final BenchmarkConfig config;
    private StateMachineEngine stateMachineEngine;
    private BenchmarkStateMachineConfig stateMachineConfig;

    public SagaModeExecutor(BenchmarkConfig config) {
        this.config = config;
    }

    private boolean isRealMode() {
        return config.getBranches() > 0;
    }

    @Override
    public void init() {
        if (isRealMode()) {
            initRealMode();
        } else {
            LOGGER.info("Saga mode executor initialized (simplified mock mode)");
            LOGGER.info("Note: Full Saga annotation support requires Spring framework integration");
        }
    }

    private void initRealMode() {
        LOGGER.info("Initializing Real Saga mode executor with state machine engine");

        try {
            // Create and configure state machine config
            stateMachineConfig = new BenchmarkStateMachineConfig();
            stateMachineConfig.setRollbackPercentage(config.getRollbackPercentage());
            stateMachineConfig.init();

            // Create state machine engine
            ProcessCtrlStateMachineEngine engine = new ProcessCtrlStateMachineEngine();
            engine.setStateMachineConfig(stateMachineConfig);
            this.stateMachineEngine = engine;

            LOGGER.info("Real Saga mode executor initialized");
            LOGGER.info("Available state machines: {}, {}", SIMPLE_SAGA_NAME, ORDER_SAGA_NAME);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Saga state machine engine", e);
        }
    }

    @Override
    public TransactionRecord execute() {
        if (isRealMode()) {
            return executeRealMode();
        } else {
            return executeMockMode();
        }
    }

    private TransactionRecord executeMockMode() {
        GlobalTransaction tx = GlobalTransactionContext.getCurrentOrCreate();
        long startTime = System.currentTimeMillis();
        String xid = null;
        String status = "Unknown";
        int branchCount = config.getBranches();
        boolean success = false;

        try {
            tx.begin(30000, "benchmark-saga-tx");
            xid = tx.getXid();

            // Simulate Saga actions (forward phase)
            for (int i = 0; i < branchCount; i++) {
                simulateSagaAction(i);
            }

            if (shouldRollback()) {
                // Simulate compensation (backward phase)
                for (int i = branchCount - 1; i >= 0; i--) {
                    simulateCompensation(i);
                }
                tx.rollback();
                status = "Compensated";
            } else {
                tx.commit();
                status = "Committed";
                success = true;
            }

        } catch (TransactionException e) {
            LOGGER.debug("Transaction failed: {}", e.getMessage());
            status = "Failed";
            try {
                if (tx.getStatus() != GlobalStatus.Rollbacked && tx.getStatus() != GlobalStatus.RollbackFailed) {
                    tx.rollback();
                }
            } catch (TransactionException rollbackEx) {
                LOGGER.debug("Rollback failed: {}", rollbackEx.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return new TransactionRecord(xid, status, duration, branchCount, success);
    }

    private TransactionRecord executeRealMode() {
        long startTime = System.currentTimeMillis();
        String businessKey = "benchmark-" + BUSINESS_KEY_COUNTER.incrementAndGet();
        String status = "Unknown";
        int branchCount = config.getBranches();
        boolean success = false;

        try {
            // Choose state machine based on branch count
            String stateMachineName = branchCount >= 3 ? ORDER_SAGA_NAME : SIMPLE_SAGA_NAME;

            // Prepare start parameters
            Map<String, Object> startParams = createStartParams();

            // Execute state machine
            StateMachineInstance instance = stateMachineEngine.startWithBusinessKey(
                    stateMachineName, stateMachineConfig.getDefaultTenantId(), businessKey, startParams);

            // Check execution result
            ExecutionStatus executionStatus = instance.getStatus();
            ExecutionStatus compensationStatus = instance.getCompensationStatus();

            if (ExecutionStatus.SU.equals(executionStatus)) {
                status = "Committed";
                success = true;
            } else if (ExecutionStatus.FA.equals(executionStatus)) {
                if (compensationStatus != null) {
                    if (ExecutionStatus.SU.equals(compensationStatus)) {
                        status = "Compensated";
                    } else {
                        status = "CompensationFailed";
                    }
                } else {
                    status = "Failed";
                }
            } else if (ExecutionStatus.UN.equals(executionStatus)) {
                status = "Unknown";
            } else {
                status = executionStatus != null ? executionStatus.name() : "Unknown";
            }

        } catch (Exception e) {
            LOGGER.debug("Saga execution failed: {}", e.getMessage());
            status = "Failed";
        }

        long duration = System.currentTimeMillis() - startTime;
        return new TransactionRecord(businessKey, status, duration, branchCount, success);
    }

    @SuppressWarnings("lgtm[java/insecure-randomness]")
    private Map<String, Object> createStartParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", "user-" + ThreadLocalRandom.current().nextInt(1000));
        params.put("productId", "product-" + ThreadLocalRandom.current().nextInt(100));
        params.put("quantity", ThreadLocalRandom.current().nextInt(10) + 1);
        params.put("amount", new BigDecimal(ThreadLocalRandom.current().nextInt(1000) + 100));
        params.put("accountId", "account-" + ThreadLocalRandom.current().nextInt(1000));
        return params;
    }

    private void simulateSagaAction(int branchId) {
        // Simulated Saga forward action
        // In real implementation, this would be a @CompensationBusinessAction annotated method
        LOGGER.trace("Executing Saga action for branch {}", branchId);
    }

    private void simulateCompensation(int branchId) {
        // Simulated Saga compensation action
        // In real implementation, this would be the compensationMethod
        LOGGER.trace("Executing compensation for branch {}", branchId);
    }

    @SuppressWarnings("lgtm[java/insecure-randomness]")
    private boolean shouldRollback() {
        return ThreadLocalRandom.current().nextInt(100) < config.getRollbackPercentage();
    }

    @Override
    public void destroy() {
        if (isRealMode()) {
            destroyRealMode();
        }
        LOGGER.info("Saga mode executor destroyed");
    }

    private void destroyRealMode() {
        LOGGER.info("Destroying Real Saga mode resources");
        // StateMachineEngine doesn't have a close method
        stateMachineEngine = null;
        stateMachineConfig = null;
    }

    /**
     * Custom StateMachineConfig for benchmark testing.
     */
    private static class BenchmarkStateMachineConfig extends AbstractStateMachineConfig {

        private int rollbackPercentage = 0;

        public void setRollbackPercentage(int rollbackPercentage) {
            this.rollbackPercentage = rollbackPercentage;
        }

        @Override
        public void init() throws Exception {
            // Load state machine definitions from classpath
            InputStream simpleSagaStream =
                    getClass().getClassLoader().getResourceAsStream("seata/saga/statelang/benchmark_simple_saga.json");
            InputStream orderSagaStream =
                    getClass().getClassLoader().getResourceAsStream("seata/saga/statelang/benchmark_order_saga.json");

            if (simpleSagaStream == null || orderSagaStream == null) {
                throw new RuntimeException("Failed to load Saga state machine definitions from classpath");
            }

            setStateMachineDefInputStreamArray(new InputStream[] {simpleSagaStream, orderSagaStream});

            // Initialize parent config
            super.init();

            // Register SpEL expression factory for parameter evaluation
            ExpressionFactoryManager expressionFactoryManager = getExpressionFactoryManager();
            SimpleSpelExpressionFactory spelExpressionFactory = new SimpleSpelExpressionFactory();
            // Register for default type (when expression doesn't start with $)
            expressionFactoryManager.putExpressionFactory(
                    ExpressionFactoryManager.DEFAULT_EXPRESSION_TYPE, spelExpressionFactory);
            // Register for empty type (when expression is like $.xxx where type is empty string)
            expressionFactoryManager.putExpressionFactory("", spelExpressionFactory);

            // Register benchmark services with the service invoker manager
            BenchmarkServiceInvoker serviceInvoker = new BenchmarkServiceInvoker();

            // Register services with configured rollback percentage
            // Divide rollback percentage by 3 for each service so total probability is approximately correct
            int serviceRollbackPct = Math.max(1, rollbackPercentage / 3);

            serviceInvoker.registerService("orderService", new OrderSagaService(serviceRollbackPct, 5));
            serviceInvoker.registerService("inventoryService", new InventorySagaService(serviceRollbackPct, 5));
            serviceInvoker.registerService("paymentService", new PaymentSagaService(serviceRollbackPct, 5));

            // Register the service invoker for different service types
            getServiceInvokerManager().putServiceInvoker(DomainConstants.SERVICE_TYPE_SPRING_BEAN, serviceInvoker);
        }
    }
}
