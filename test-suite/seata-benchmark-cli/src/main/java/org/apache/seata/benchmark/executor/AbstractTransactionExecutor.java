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

import org.apache.seata.benchmark.BenchmarkConstants;
import org.apache.seata.benchmark.config.BenchmarkConfig;
import org.apache.seata.benchmark.model.TransactionRecord;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.tm.api.GlobalTransaction;
import org.apache.seata.tm.api.GlobalTransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Abstract base class for transaction executors implementing common transaction handling logic
 */
public abstract class AbstractTransactionExecutor implements TransactionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTransactionExecutor.class);
    protected static final Random RANDOM = new Random();

    protected final BenchmarkConfig config;

    protected AbstractTransactionExecutor(BenchmarkConfig config) {
        this.config = config;
    }

    /**
     * Get the transaction name for this executor
     *
     * @return transaction name
     */
    protected abstract String getTransactionName();

    /**
     * Get the number of branches for this transaction
     *
     * @return number of branches
     */
    protected abstract int getBranchCount();

    /**
     * Execute the business logic for this transaction
     *
     * @throws Exception if execution fails
     */
    protected abstract void executeBusinessLogic() throws Exception;

    /**
     * Get the logger for the concrete executor class
     *
     * @return logger instance
     */
    protected abstract Logger getLogger();

    @Override
    public final TransactionRecord execute() {
        GlobalTransaction tx = GlobalTransactionContext.getCurrentOrCreate();
        long startTime = System.currentTimeMillis();
        String xid = null;
        String status = "Unknown";
        int branchCount = getBranchCount();
        boolean success = false;

        try {
            tx.begin(BenchmarkConstants.TRANSACTION_TIMEOUT_MS, getTransactionName());
            xid = tx.getXid();

            executeBusinessLogic();

            if (shouldRollback()) {
                tx.rollback();
                status = "Rollbacked";
            } else {
                tx.commit();
                status = "Committed";
                success = true;
            }

        } catch (TransactionException e) {
            getLogger().warn("Transaction failed: {}", e.getMessage());
            status = "Failed";
            rollbackTransaction(tx);

        } catch (Exception e) {
            getLogger().warn("Unexpected error during transaction execution: {}", e.getMessage(), e);
            status = "Failed";
            rollbackTransaction(tx);
        }

        long duration = System.currentTimeMillis() - startTime;
        return new TransactionRecord(xid, status, duration, branchCount, success);
    }

    /**
     * Determine if the transaction should be rolled back based on rollback percentage
     *
     * @return true if should rollback
     */
    protected boolean shouldRollback() {
        return RANDOM.nextInt(100) < config.getRollbackPercentage();
    }

    /**
     * Attempt to rollback a transaction
     *
     * @param tx the global transaction to rollback
     */
    private void rollbackTransaction(GlobalTransaction tx) {
        try {
            GlobalStatus status = tx.getStatus();
            if (status != GlobalStatus.Rollbacked && status != GlobalStatus.RollbackFailed) {
                tx.rollback();
            }
        } catch (TransactionException rollbackEx) {
            getLogger().warn("Rollback failed: {}", rollbackEx.getMessage());
        }
    }
}
