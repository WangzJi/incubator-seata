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
package org.apache.seata.benchmark;

import org.apache.seata.benchmark.config.BenchmarkConfig;
import org.apache.seata.benchmark.config.BenchmarkConfigLoader;
import org.apache.seata.benchmark.constant.BenchmarkConstants;
import org.apache.seata.benchmark.executor.ATModeExecutor;
import org.apache.seata.benchmark.executor.SagaModeExecutor;
import org.apache.seata.benchmark.executor.TCCModeExecutor;
import org.apache.seata.benchmark.executor.TransactionExecutor;
import org.apache.seata.benchmark.executor.WorkloadGenerator;
import org.apache.seata.benchmark.model.BenchmarkMetrics;
import org.apache.seata.benchmark.monitor.MetricsCollector;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.rpc.ShutdownHook;
import org.apache.seata.core.rpc.netty.RmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.apache.seata.rm.RMClient;
import org.apache.seata.tm.TMClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Seata Benchmark CLI Application
 */
@Command(
        name = "seata-benchmark",
        version = "Seata Benchmark CLI 1.0.0",
        description = "Command-line benchmark tool for Seata transaction modes",
        mixinStandardHelpOptions = true)
public class BenchmarkApplication implements Callable<Integer> {

    @Option(
            names = {"-s", "--server"},
            description = "Seata Server address (host:port)",
            required = false)
    private String server;

    @Option(
            names = {"-m", "--mode"},
            description = "Transaction mode: AT, TCC, or SAGA",
            required = false)
    private String mode;

    @Option(
            names = {"-t", "--tps"},
            description = "Target transactions per second (default: 100)")
    private Integer targetTps;

    @Option(
            names = {"--threads"},
            description = "Number of concurrent threads (default: 10)")
    private Integer threads;

    @Option(
            names = {"-d", "--duration"},
            description = "Benchmark duration in seconds (default: 60)")
    private Integer duration;

    @Option(
            names = {"--warmup-duration"},
            description = "Warmup duration in seconds (default: 0)")
    private Integer warmupDuration;

    @Option(
            names = {"--export-csv"},
            description = "Export metrics to CSV file")
    private String exportCsv;

    @Option(
            names = {"--application-id"},
            description = "Seata application ID (default: benchmark-app)")
    private String applicationId;

    @Option(
            names = {"--tx-service-group"},
            description = "Seata transaction service group (default: default_tx_group)")
    private String txServiceGroup;

    @Option(
            names = {"--rollback-percentage"},
            description = "Rollback percentage for fault injection (0-100, default: 2)")
    private Integer rollbackPercentage;

    @Option(
            names = {"--branches"},
            description = "Number of branch transactions (0=empty mode, >=1=real mode with actual execution)")
    private Integer branches;

    public static void main(String[] args) {
        // Parse server address from args before any Seata class loading
        String serverAddr = BenchmarkConstants.DEFAULT_SERVER_ADDRESS;
        String txGroup = BenchmarkConstants.DEFAULT_TX_GROUP;
        for (int i = 0; i < args.length; i++) {
            if (("-s".equals(args[i]) || "--server".equals(args[i])) && i + 1 < args.length) {
                serverAddr = args[i + 1];
            }
            if ("--tx-service-group".equals(args[i]) && i + 1 < args.length) {
                txGroup = args[i + 1];
            }
        }

        // Set Seata configuration properties BEFORE any Seata class loading
        System.setProperty("seata.config.type", "file");
        System.setProperty("seata.registry.type", "file");
        System.setProperty("service.default.grouplist", serverAddr);
        System.setProperty("service.vgroupMapping." + txGroup, "default");

        int exitCode = new CommandLine(new BenchmarkApplication()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        TransactionExecutor executor = null;
        WorkloadGenerator workloadGenerator = null;

        try {
            System.out.println("===========================================");
            System.out.println("   Seata Benchmark CLI v1.0.0");
            System.out.println("===========================================\n");

            BenchmarkConfig config = new BenchmarkConfig();
            config = BenchmarkConfigLoader.merge(
                    config,
                    server,
                    mode,
                    targetTps,
                    threads,
                    duration,
                    warmupDuration,
                    applicationId,
                    txServiceGroup,
                    rollbackPercentage,
                    branches);
            config.validate();

            System.out.println("Configuration:");
            System.out.println("  Server:       " + config.getServer());
            System.out.println("  Mode:         " + config.getMode());
            System.out.println("  Target TPS:   " + config.getTargetTps());
            System.out.println("  Threads:      " + config.getThreads());
            System.out.println("  Duration:     " + config.getDuration() + "s");
            if (config.getWarmupDuration() > 0) {
                System.out.println("  Warmup:       " + config.getWarmupDuration() + "s");
            }
            System.out.println("  Rollback %:   " + config.getRollbackPercentage() + "%");
            System.out.println("  Branches:     " + config.getBranches()
                    + (config.getBranches() == 0 ? " (empty mode)" : " (real mode)"));
            System.out.println();

            initSeataClient(config);

            executor = createExecutor(config);
            executor.init();

            BenchmarkMetrics metrics = new BenchmarkMetrics();
            MetricsCollector metricsCollector = new MetricsCollector(metrics);
            workloadGenerator = new WorkloadGenerator(config, executor, metrics);

            System.out.println("Starting benchmark...\n");
            workloadGenerator.start();
            workloadGenerator.waitForCompletion();

            System.out.println("\n" + metricsCollector.generateFinalReport());

            if (exportCsv != null) {
                metricsCollector.exportToCsv(exportCsv);
                System.out.println("\nMetrics exported to: " + exportCsv);
            }

            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return 1;

        } finally {
            if (workloadGenerator != null) {
                workloadGenerator.stop();
            }
            if (executor != null) {
                executor.destroy();
            }
        }
    }

    private void initSeataClient(BenchmarkConfig config) {
        System.out.println("Initializing Seata client...");

        TMClient.init(config.getApplicationId(), config.getTxServiceGroup());
        RMClient.init(config.getApplicationId(), config.getTxServiceGroup());

        ShutdownHook.getInstance()
                .addDisposable(
                        TmNettyRemotingClient.getInstance(config.getApplicationId(), config.getTxServiceGroup()));
        ShutdownHook.getInstance()
                .addDisposable(
                        RmNettyRemotingClient.getInstance(config.getApplicationId(), config.getTxServiceGroup()));

        System.out.println("Seata client initialized\n");
    }

    private TransactionExecutor createExecutor(BenchmarkConfig config) {
        boolean isRealMode = config.getBranches() > 0;

        if (config.getMode() == BranchType.AT) {
            String mode = isRealMode ? " (MySQL via Testcontainers)" : " (empty transaction)";
            System.out.println("Creating AT mode executor" + mode + "\n");
            return new ATModeExecutor(config);
        } else if (config.getMode() == BranchType.TCC) {
            System.out.println("Creating TCC mode executor (mock implementation)\n");
            return new TCCModeExecutor(config);
        } else if (config.getMode() == BranchType.SAGA) {
            String mode = isRealMode ? " (state machine engine)" : " (empty transaction)";
            System.out.println("Creating Saga mode executor" + mode + "\n");
            return new SagaModeExecutor(config);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported mode: " + config.getMode() + ". Only AT, TCC, and SAGA are supported.");
        }
    }
}
