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
package io.seata.spring.annotation.datasource;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test cases for AutoDataSourceProxyRegistrar compatibility wrapper.
 */
public class AutoDataSourceProxyRegistrarTest {

    @Test
    public void testDeprecatedAnnotation() {
        assertTrue(
                AutoDataSourceProxyRegistrar.class.isAnnotationPresent(Deprecated.class),
                "AutoDataSourceProxyRegistrar should be marked as @Deprecated");
    }

    @Test
    public void testImplementsImportBeanDefinitionRegistrar() {
        assertTrue(
                ImportBeanDefinitionRegistrar.class.isAssignableFrom(AutoDataSourceProxyRegistrar.class),
                "AutoDataSourceProxyRegistrar should implement ImportBeanDefinitionRegistrar");
    }

    @Test
    public void testBeanNameConstant() throws Exception {
        java.lang.reflect.Field field =
                AutoDataSourceProxyRegistrar.class.getField("BEAN_NAME_SEATA_AUTO_DATA_SOURCE_PROXY_CREATOR");
        assertNotNull(field, "Should have BEAN_NAME_SEATA_AUTO_DATA_SOURCE_PROXY_CREATOR field");
        assertEquals(
                "seataAutoDataSourceProxyCreator",
                field.get(null),
                "Bean name should be seataAutoDataSourceProxyCreator");
    }

    @Test
    public void testCanInstantiate() {
        AutoDataSourceProxyRegistrar registrar = new AutoDataSourceProxyRegistrar();
        assertNotNull(registrar, "Should be able to instantiate AutoDataSourceProxyRegistrar");
    }
}
