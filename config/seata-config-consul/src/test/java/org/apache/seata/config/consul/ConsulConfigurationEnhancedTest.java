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
package org.apache.seata.config.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.ecwid.consul.v1.kv.model.PutParams;
import org.apache.seata.common.util.ReflectionUtil;
import org.apache.seata.config.ConfigurationChangeListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.Set;

class ConsulConfigurationEnhancedTest {

    private ConsulClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        mockClient = Mockito.mock(ConsulClient.class);

        Field clientField = ReflectionUtil.getField(ConsulConfiguration.class, "client");
        clientField.setAccessible(true);
        clientField.set(null, mockClient);

        Field seataConfigField = ReflectionUtil.getField(ConsulConfiguration.class, "seataConfig");
        seataConfigField.setAccessible(true);
        seataConfigField.set(null, new Properties());
    }

    @AfterEach
    void tearDown() throws Exception {
        Field instanceField = ReflectionUtil.getField(ConsulConfiguration.class, "instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        Field clientField = ReflectionUtil.getField(ConsulConfiguration.class, "client");
        clientField.setAccessible(true);
        clientField.set(null, null);

        Field seataConfigField = ReflectionUtil.getField(ConsulConfiguration.class, "seataConfig");
        seataConfigField.setAccessible(true);
        seataConfigField.set(null, new Properties());
    }

    @Test
    void testGetTypeName() {
        ConsulConfiguration config = ConsulConfiguration.getInstance();
        Assertions.assertEquals("consul", config.getTypeName());
    }

    @Test
    void testGetLatestConfigFromSeataConfig() throws Exception {
        Field seataConfigField = ReflectionUtil.getField(ConsulConfiguration.class, "seataConfig");
        seataConfigField.setAccessible(true);
        Properties props = new Properties();
        props.setProperty("test.key", "test-value");
        seataConfigField.set(null, props);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        String value = config.getLatestConfig("test.key", "default", 1000);

        Assertions.assertEquals("test-value", value);
    }

    @Test
    void testGetLatestConfigFromConsul() throws Exception {
        GetValue mockValue = Mockito.mock(GetValue.class);
        Mockito.when(mockValue.getDecodedValue()).thenReturn("consul-value");

        Response<GetValue> mockResponse = new Response<>(mockValue, 1L, false, 1L);
        Mockito.when(mockClient.getKVValue(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(mockResponse);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        String value = config.getLatestConfig("test.consul.key", "default", 1000);

        Assertions.assertEquals("consul-value", value);
    }

    @Test
    void testGetLatestConfigWithDefaultValue() throws Exception {
        GetValue mockValue = Mockito.mock(GetValue.class);
        Mockito.when(mockValue.getDecodedValue()).thenReturn(null);

        Response<GetValue> mockResponse = new Response<>(mockValue, 1L, false, 1L);
        Mockito.when(mockClient.getKVValue(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(mockResponse);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        String value = config.getLatestConfig("non.existent.key", "default-value", 1000);

        Assertions.assertEquals("default-value", value);
    }

    @Test
    void testPutConfigWhenSeataConfigEmpty() throws Exception {
        Response<Boolean> mockResponse = new Response<>(true, 1L, false, 1L);
        Mockito.when(mockClient.setKVValue(
                        Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.isNull()))
                .thenReturn(mockResponse);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        boolean result = config.putConfig("test.key", "test-value", 1000);

        Assertions.assertTrue(result);
    }

    @Test
    void testPutConfigWhenSeataConfigNotEmpty() throws Exception {
        Field seataConfigField = ReflectionUtil.getField(ConsulConfiguration.class, "seataConfig");
        seataConfigField.setAccessible(true);
        Properties props = new Properties();
        props.setProperty("existing.key", "existing-value");
        seataConfigField.set(null, props);

        Response<Boolean> mockResponse = new Response<>(true, 1L, false, 1L);
        Mockito.when(mockClient.setKVValue(
                        Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.isNull()))
                .thenReturn(mockResponse);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        boolean result = config.putConfig("new.key", "new-value", 1000);

        Assertions.assertTrue(result);
    }

    @Test
    void testPutConfigIfAbsentWhenSeataConfigEmpty() throws Exception {
        Response<Boolean> mockResponse = new Response<>(true, 1L, false, 1L);
        Mockito.when(mockClient.setKVValue(
                        Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(PutParams.class)))
                .thenReturn(mockResponse);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        boolean result = config.putConfigIfAbsent("new.key", "new-value", 1000);

        Assertions.assertTrue(result);
    }

    @Test
    void testRemoveConfigWhenSeataConfigEmpty() throws Exception {
        Response<Void> mockResponse = new Response<>(null, 1L, false, 1L);
        Mockito.when(mockClient.deleteKVValue(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(mockResponse);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        boolean result = config.removeConfig("test.key", 1000);

        Assertions.assertTrue(result);
    }

    @Test
    void testRemoveConfigWhenSeataConfigNotEmpty() throws Exception {
        Field seataConfigField = ReflectionUtil.getField(ConsulConfiguration.class, "seataConfig");
        seataConfigField.setAccessible(true);
        Properties props = new Properties();
        props.setProperty("test.key", "test-value");
        seataConfigField.set(null, props);

        Response<Boolean> mockResponse = new Response<>(true, 1L, false, 1L);
        Mockito.when(mockClient.setKVValue(
                        Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.isNull()))
                .thenReturn(mockResponse);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        boolean result = config.removeConfig("test.key", 1000);

        Assertions.assertTrue(result);
    }

    @Test
    void testAddConfigListener() {
        ConfigurationChangeListener listener = event -> {};

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        config.addConfigListener("test.listener.key", listener);

        Set<ConfigurationChangeListener> listeners = config.getConfigListeners("test.listener.key");
        Assertions.assertNotNull(listeners);
        Assertions.assertEquals(1, listeners.size());
    }

    @Test
    void testAddConfigListenerWithBlankDataId() {
        ConfigurationChangeListener listener = event -> {};

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        config.addConfigListener("", listener);
        config.addConfigListener(null, listener);

        Set<ConfigurationChangeListener> listeners1 = config.getConfigListeners("");
        Set<ConfigurationChangeListener> listeners2 = config.getConfigListeners(null);
        Assertions.assertNull(listeners1);
        Assertions.assertNull(listeners2);
    }

    @Test
    void testAddConfigListenerWithNullListener() {
        ConsulConfiguration config = ConsulConfiguration.getInstance();
        config.addConfigListener("test.key", null);

        Set<ConfigurationChangeListener> listeners = config.getConfigListeners("test.key");
        Assertions.assertNull(listeners);
    }

    @Test
    void testRemoveConfigListener() {
        ConfigurationChangeListener listener = event -> {};

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        config.addConfigListener("test.remove.key", listener);

        Set<ConfigurationChangeListener> listeners = config.getConfigListeners("test.remove.key");
        Assertions.assertNotNull(listeners);

        config.removeConfigListener("test.remove.key", listener);
        Set<ConfigurationChangeListener> remainingListeners = config.getConfigListeners("test.remove.key");
        Assertions.assertTrue(remainingListeners == null || remainingListeners.isEmpty());
    }

    @Test
    void testRemoveConfigListenerWithBlankDataId() {
        ConfigurationChangeListener listener = event -> {};

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        config.removeConfigListener("", listener);
        config.removeConfigListener(null, listener);
    }

    @Test
    void testRemoveConfigListenerWithNullListener() {
        ConsulConfiguration config = ConsulConfiguration.getInstance();
        config.removeConfigListener("test.key", null);
    }

    @Test
    void testGetConfigListeners() {
        ConfigurationChangeListener listener1 = event -> {};
        ConfigurationChangeListener listener2 = event -> {};

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        config.addConfigListener("test.multi.listeners", listener1);
        config.addConfigListener("test.multi.listeners", listener2);

        Set<ConfigurationChangeListener> listeners = config.getConfigListeners("test.multi.listeners");
        Assertions.assertNotNull(listeners);
        Assertions.assertEquals(2, listeners.size());
    }

    @Test
    void testGetConfigListenersForNonExistentKey() {
        ConsulConfiguration config = ConsulConfiguration.getInstance();
        Set<ConfigurationChangeListener> listeners = config.getConfigListeners("non.existent.key");
        Assertions.assertNull(listeners);
    }

    @Test
    void testGetConfig() throws Exception {
        GetValue mockValue = Mockito.mock(GetValue.class);
        Mockito.when(mockValue.getDecodedValue()).thenReturn("config-value");

        Response<GetValue> mockResponse = new Response<>(mockValue, 1L, false, 1L);
        Mockito.when(mockClient.getKVValue(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(mockResponse);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        String value = config.getConfig("test.key", "default-value", 1000);

        Assertions.assertEquals("config-value", value);
    }

    @Test
    void testGetInt() throws Exception {
        Field seataConfigField = ReflectionUtil.getField(ConsulConfiguration.class, "seataConfig");
        seataConfigField.setAccessible(true);
        Properties props = new Properties();
        props.setProperty("test.int.key", "100");
        seataConfigField.set(null, props);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        int value = config.getInt("test.int.key", 50, 1000);
        Assertions.assertEquals(100, value);
    }

    @Test
    void testGetBoolean() throws Exception {
        Field seataConfigField = ReflectionUtil.getField(ConsulConfiguration.class, "seataConfig");
        seataConfigField.setAccessible(true);
        Properties props = new Properties();
        props.setProperty("test.boolean.key", "true");
        seataConfigField.set(null, props);

        ConsulConfiguration config = ConsulConfiguration.getInstance();
        boolean value = config.getBoolean("test.boolean.key", false, 1000);
        Assertions.assertTrue(value);
    }
}
