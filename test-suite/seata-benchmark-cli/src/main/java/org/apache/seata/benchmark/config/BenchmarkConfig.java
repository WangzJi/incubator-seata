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
package org.apache.seata.benchmark.config;

/**
 * Benchmark configuration
 */
public class BenchmarkConfig {

    private String server = "127.0.0.1:8091";
    private String mode = "AT";
    private int targetTps = 100;
    private int threads = 10;
    private int duration = 60;
    private int warmupDuration = 0;
    private String applicationId = "benchmark-app";
    private String txServiceGroup = "default_tx_group";
    private int rollbackPercentage = 2;
    private int branches = 0;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getTargetTps() {
        return targetTps;
    }

    public void setTargetTps(int targetTps) {
        this.targetTps = targetTps;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getWarmupDuration() {
        return warmupDuration;
    }

    public void setWarmupDuration(int warmupDuration) {
        this.warmupDuration = warmupDuration;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getTxServiceGroup() {
        return txServiceGroup;
    }

    public void setTxServiceGroup(String txServiceGroup) {
        this.txServiceGroup = txServiceGroup;
    }

    public int getRollbackPercentage() {
        return rollbackPercentage;
    }

    public void setRollbackPercentage(int rollbackPercentage) {
        this.rollbackPercentage = rollbackPercentage;
    }

    public int getBranches() {
        return branches;
    }

    public void setBranches(int branches) {
        this.branches = branches;
    }

    public void validate() {
        if (!"AT".equalsIgnoreCase(mode) && !"TCC".equalsIgnoreCase(mode) && !"SAGA".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unsupported mode: " + mode + ". Only AT, TCC, and SAGA are supported.");
        }
        if (server == null || server.trim().isEmpty()) {
            throw new IllegalArgumentException("server cannot be empty");
        }
        if (targetTps <= 0) {
            throw new IllegalArgumentException("targetTps must be positive");
        }
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive");
        }
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be positive");
        }
        if (warmupDuration < 0) {
            throw new IllegalArgumentException("warmupDuration cannot be negative");
        }
        if (applicationId == null || applicationId.trim().isEmpty()) {
            throw new IllegalArgumentException("applicationId cannot be empty");
        }
        if (txServiceGroup == null || txServiceGroup.trim().isEmpty()) {
            throw new IllegalArgumentException("txServiceGroup cannot be empty");
        }
        if (rollbackPercentage < 0 || rollbackPercentage > 100) {
            throw new IllegalArgumentException("rollbackPercentage must be between 0 and 100");
        }
        if (branches < 0) {
            throw new IllegalArgumentException("branches cannot be negative");
        }
    }
}
