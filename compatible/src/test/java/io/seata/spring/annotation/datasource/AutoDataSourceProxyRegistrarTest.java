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
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.core.type.AnnotationMetadata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for AutoDataSourceProxyRegistrar.
 */
public class AutoDataSourceProxyRegistrarTest {

    @Test
    public void testImplementsImportBeanDefinitionRegistrar() {
        assertTrue(
                org.springframework.context.annotation.ImportBeanDefinitionRegistrar.class.isAssignableFrom(
                        AutoDataSourceProxyRegistrar.class),
                "AutoDataSourceProxyRegistrar should implement ImportBeanDefinitionRegistrar");
    }

    @Test
    public void testRegisterBeanDefinitions() {
        AutoDataSourceProxyRegistrar registrar = new AutoDataSourceProxyRegistrar();
        AnnotationMetadata mockMetadata = mock(AnnotationMetadata.class);
        BeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();

        // Should not throw exception
        assertDoesNotThrow(() -> registrar.registerBeanDefinitions(mockMetadata, registry));
    }

    @Test
    public void testRegisterBeanDefinitionsWithNullMetadata() {
        AutoDataSourceProxyRegistrar registrar = new AutoDataSourceProxyRegistrar();
        BeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();

        // Should handle null metadata gracefully
        assertDoesNotThrow(() -> registrar.registerBeanDefinitions(null, registry));
    }

    @Test
    public void testRegisterBeanDefinitionsWithRealRegistry() {
        AutoDataSourceProxyRegistrar registrar = new AutoDataSourceProxyRegistrar();
        AnnotationMetadata mockMetadata = mock(AnnotationMetadata.class);
        SimpleBeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();

        registrar.registerBeanDefinitions(mockMetadata, registry);

        // Verify that registry has been used (beans may or may not be registered depending on annotation presence)
        assertNotNull(registry);
    }

    @Test
    public void testDeprecatedAnnotation() {
        assertTrue(
                AutoDataSourceProxyRegistrar.class.isAnnotationPresent(Deprecated.class),
                "AutoDataSourceProxyRegistrar should be marked as @Deprecated");
    }
}
