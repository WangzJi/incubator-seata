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
package org.apache.seata.integration.tx.api.remoting.parser;

import org.apache.seata.common.transaction.api.LocalTransactional;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for @LocalTransactional annotation
 * 
 * This test suite validates the runtime behavior and properties of the @LocalTransactional annotation:
 * 
 * Key test areas:
 * 1. Annotation presence detection at runtime
 * 2. Annotation inheritance behavior with @Inherited
 * 3. Interface vs implementation annotation handling
 * 4. Reflection-based annotation access
 * 5. Service instantiation with annotated classes
 * 6. Complex inheritance hierarchies
 * 7. Multiple interface scenarios
 * 
 * These tests ensure that @LocalTransactional behaves correctly in real runtime environments
 * and maintains compatibility with existing transaction processing frameworks.
 */
public class LocalTransactionalIntegrationTest {

    // Test classes for integration testing
    @LocalTransactional
    public static class AccountService {
        public boolean debit(String accountId, double amount) {
            return amount > 0;
        }
        
        public boolean credit(String accountId, double amount) {
            return amount > 0;
        }
    }

    @LocalTransactional
    public interface PaymentService {
        boolean processPayment(String orderId, double amount);
        boolean refundPayment(String orderId, double amount);
    }

    public static class PaymentServiceImpl implements PaymentService {
        @Override
        public boolean processPayment(String orderId, double amount) {
            return orderId != null && amount > 0;
        }

        @Override
        public boolean refundPayment(String orderId, double amount) {
            return orderId != null && amount > 0;
        }
    }

    public static class NoAnnotationService {
        public boolean doSomething() { return true; }
    }

    @Test
    public void testLocalTransactionalAnnotationExists() {
        // Verify the annotation exists and can be used
        assertTrue(AccountService.class.isAnnotationPresent(LocalTransactional.class));
        assertTrue(PaymentService.class.isAnnotationPresent(LocalTransactional.class));
        assertFalse(NoAnnotationService.class.isAnnotationPresent(LocalTransactional.class));
    }

    @Test
    public void testLocalTransactionalAnnotationProperties() throws Exception {
        LocalTransactional annotation = AccountService.class.getAnnotation(LocalTransactional.class);
        assertNotNull(annotation);
        
        // Verify annotation type
        assertEquals(LocalTransactional.class, annotation.annotationType());
    }

    @Test
    public void testAnnotationInheritance() {
        // Test that @Inherited works properly
        class InheritedService extends AccountService {
            public boolean additionalOperation() { return true; }
        }
        
        InheritedService inheritedService = new InheritedService();
        
        // Should inherit the @LocalTransactional annotation
        assertTrue(inheritedService.getClass().isAnnotationPresent(LocalTransactional.class));
    }

    @Test
    public void testAnnotationOnInterface() {
        PaymentServiceImpl implementation = new PaymentServiceImpl();
        
        // Check that the interface has the annotation
        assertTrue(PaymentService.class.isAnnotationPresent(LocalTransactional.class));
        
        // Check that implementation can access interface annotation
        for (Class<?> interfaceClass : implementation.getClass().getInterfaces()) {
            if (interfaceClass == PaymentService.class) {
                assertTrue(interfaceClass.isAnnotationPresent(LocalTransactional.class));
            }
        }
    }

    @Test
    public void testAnnotationRetentionPolicy() throws Exception {
        // Verify annotation is retained at runtime
        LocalTransactional annotation = AccountService.class.getAnnotation(LocalTransactional.class);
        assertNotNull(annotation);
        
        // Verify it's available through reflection
        boolean foundAnnotation = false;
        for (java.lang.annotation.Annotation ann : AccountService.class.getAnnotations()) {
            if (ann instanceof LocalTransactional) {
                foundAnnotation = true;
                break;
            }
        }
        assertTrue(foundAnnotation);
    }

    @Test
    public void testAnnotationTarget() {
        // Verify annotation can be applied to types (classes and interfaces)
        assertTrue(AccountService.class.isAnnotationPresent(LocalTransactional.class));
        assertTrue(PaymentService.class.isAnnotationPresent(LocalTransactional.class));
    }

    @Test
    public void testMultipleServicesWithAnnotation() {
        List<Class<?>> annotatedClasses = new ArrayList<>();
        List<Class<?>> testClasses = new ArrayList<>();
        testClasses.add(AccountService.class);
        testClasses.add(PaymentService.class);
        testClasses.add(PaymentServiceImpl.class);
        testClasses.add(NoAnnotationService.class);
        
        for (Class<?> clazz : testClasses) {
            if (clazz.isAnnotationPresent(LocalTransactional.class)) {
                annotatedClasses.add(clazz);
            }
            
            // Also check interfaces
            for (Class<?> interfaceClass : clazz.getInterfaces()) {
                if (interfaceClass.isAnnotationPresent(LocalTransactional.class)) {
                    annotatedClasses.add(interfaceClass);
                }
            }
        }
        
        // Should find AccountService and PaymentService
        assertTrue(annotatedClasses.size() >= 2);
        assertTrue(annotatedClasses.contains(AccountService.class));
        assertTrue(annotatedClasses.contains(PaymentService.class));
    }

    @Test
    public void testServiceInstantiation() {
        // Verify services with annotation can be instantiated normally
        AccountService accountService = new AccountService();
        assertNotNull(accountService);
        assertTrue(accountService.debit("123", 100.0));
        
        PaymentServiceImpl paymentService = new PaymentServiceImpl();
        assertNotNull(paymentService);
        assertTrue(paymentService.processPayment("order123", 50.0));
    }

    @Test
    public void testReflectionAccess() throws Exception {
        AccountService service = new AccountService();
        Class<?> serviceClass = service.getClass();
        
        // Verify we can access methods through reflection
        assertNotNull(serviceClass.getMethod("debit", String.class, double.class));
        assertNotNull(serviceClass.getMethod("credit", String.class, double.class));
        
        // Verify annotation is accessible through reflection
        assertTrue(serviceClass.isAnnotationPresent(LocalTransactional.class));
    }

    @Test
    public void testClassHierarchyAnnotationDetection() {
        // Test complex inheritance scenario
        @LocalTransactional
        class BaseService {
            public void baseMethod() {}
        }
        
        class MiddleService extends BaseService {
            public void middleMethod() {}
        }
        
        class FinalService extends MiddleService {
            public void finalMethod() {}
        }
        
        FinalService finalService = new FinalService();
        
        // Should inherit annotation from base class
        assertTrue(finalService.getClass().isAnnotationPresent(LocalTransactional.class));
    }
} 