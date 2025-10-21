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
package org.apache.seata.config.zk;

import org.apache.curator.test.TestingServer;
import org.apache.seata.common.exception.NotSupportYetException;
import org.apache.seata.config.ConfigurationChangeListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

class ZookeeperConfigurationEnhancedTest {

    protected static TestingServer server = null;

    @BeforeAll
    static void setUp() throws Exception {
        System.setProperty("config.type", "zk");
        System.setProperty("config.zk.serverAddr", "127.0.0.1:2181");
        server = new TestingServer(2181);
        server.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testGetTypeName() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        Assertions.assertEquals("zk", config.getTypeName());
    }

    @Test
    void testGetLatestConfigWithDefaultValue() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        String value = config.getLatestConfig("non.existent.key", "default-value", 1000);

        Assertions.assertEquals("default-value", value);
    }

    @Test
    void testGetLatestConfigFromZookeeper() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        String dataId = "test.zk.key";
        config.putConfig(dataId, "zk-value", 1000);

        String value = config.getLatestConfig(dataId, "default", 1000);
        Assertions.assertEquals("zk-value", value);

        config.removeConfig(dataId, 1000);
    }

    @Test
    void testPutConfigIfAbsent() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();

        Assertions.assertThrows(
                NotSupportYetException.class, () -> config.putConfigIfAbsent("test.key", "test-value", 1000));
    }

    @Test
    void testAddConfigListener() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        ConfigurationChangeListener listener = event -> {};

        config.addConfigListener("test.listener.key", listener);

        Set<ConfigurationChangeListener> listeners = config.getConfigListeners("test.listener.key");
        Assertions.assertNotNull(listeners);
        Assertions.assertEquals(1, listeners.size());
    }

    @Test
    void testAddConfigListenerWithBlankDataId() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        ConfigurationChangeListener listener = event -> {};

        config.addConfigListener("", listener);
        config.addConfigListener(null, listener);

        Set<ConfigurationChangeListener> listeners1 = config.getConfigListeners("");
        Set<ConfigurationChangeListener> listeners2 = config.getConfigListeners(null);
        Assertions.assertNull(listeners1);
        Assertions.assertNull(listeners2);
    }

    @Test
    void testAddConfigListenerWithNullListener() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        config.addConfigListener("test.key", null);

        Set<ConfigurationChangeListener> listeners = config.getConfigListeners("test.key");
        Assertions.assertNull(listeners);
    }

    @Test
    void testRemoveConfigListener() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        ConfigurationChangeListener listener = event -> {};

        config.addConfigListener("test.remove.key", listener);
        Set<ConfigurationChangeListener> listeners = config.getConfigListeners("test.remove.key");
        Assertions.assertNotNull(listeners);

        config.removeConfigListener("test.remove.key", listener);
        Set<ConfigurationChangeListener> remainingListeners = config.getConfigListeners("test.remove.key");
        Assertions.assertTrue(remainingListeners == null || remainingListeners.isEmpty());
    }

    @Test
    void testRemoveConfigListenerWithBlankDataId() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        ConfigurationChangeListener listener = event -> {};

        config.removeConfigListener("", listener);
        config.removeConfigListener(null, listener);
    }

    @Test
    void testRemoveConfigListenerWithNullListener() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        config.removeConfigListener("test.key", null);
    }

    @Test
    void testGetConfigListeners() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        ConfigurationChangeListener listener1 = event -> {};
        ConfigurationChangeListener listener2 = event -> {};

        config.addConfigListener("test.multi.listeners", listener1);
        config.addConfigListener("test.multi.listeners", listener2);

        Set<ConfigurationChangeListener> listeners = config.getConfigListeners("test.multi.listeners");
        Assertions.assertNotNull(listeners);
        Assertions.assertEquals(2, listeners.size());
    }

    @Test
    void testGetConfigListenersForNonExistentKey() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        Set<ConfigurationChangeListener> listeners = config.getConfigListeners("non.existent.key");
        Assertions.assertNull(listeners);
    }

    @Test
    void testGetConfig() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        String dataId = "test.config.key";
        config.putConfig(dataId, "config-value", 1000);

        String value = config.getConfig(dataId, "default-value", 1000);
        Assertions.assertEquals("config-value", value);

        config.removeConfig(dataId, 1000);
    }

    @Test
    void testGetInt() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        String dataId = "test.int.key";
        config.putConfig(dataId, "100", 1000);

        int value = config.getInt(dataId, 50, 1000);
        Assertions.assertEquals(100, value);

        config.removeConfig(dataId, 1000);
    }

    @Test
    void testGetBoolean() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        String dataId = "test.boolean.key";
        config.putConfig(dataId, "true", 1000);

        boolean value = config.getBoolean(dataId, false, 1000);
        Assertions.assertTrue(value);

        config.removeConfig(dataId, 1000);
    }

    @Test
    void testCheckExists() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        boolean exists = config.checkExists("/");
        Assertions.assertTrue(exists);
    }

    @Test
    void testCheckExistsForNonExistentPath() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        boolean exists = config.checkExists("/non/existent/path");
        Assertions.assertFalse(exists);
    }

    @Test
    void testCreatePersistent() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        String path = "/test/persistent/node";

        if (!config.checkExists("/test")) {
            config.createPersistent("/test");
        }
        if (!config.checkExists("/test/persistent")) {
            config.createPersistent("/test/persistent");
        }
        config.createPersistent(path);

        boolean exists = config.checkExists(path);
        Assertions.assertTrue(exists);
    }

    @Test
    void testReadData() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        String dataId = "test.read.key";
        String testValue = "read-value";

        config.putConfig(dataId, testValue, 1000);
        String path = config.buildPath(dataId);
        String value = config.readData(path);

        Assertions.assertEquals(testValue, value);

        config.removeConfig(dataId, 1000);
    }

    @Test
    void testReadDataFromNonExistentNode() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        String value = config.readData("/non/existent/node");
        Assertions.assertNull(value);
    }

    @Test
    void testBuildPath() {
        ZookeeperConfiguration config = new ZookeeperConfiguration();
        String path = config.buildPath("test.key");
        Assertions.assertTrue(path.startsWith("/seata"));
        Assertions.assertTrue(path.contains("test.key"));
    }
}
