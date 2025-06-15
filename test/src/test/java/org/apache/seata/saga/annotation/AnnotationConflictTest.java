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
package org.apache.seata.saga.annotation;

import org.apache.seata.common.exception.FrameworkException;
import org.apache.seata.common.transaction.api.TransactionParticipant;
import org.apache.seata.integration.tx.api.remoting.RemotingDesc;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.rm.tcc.remoting.parser.LocalTCCRemotingParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Boundary and edge case tests for annotation conflicts and complex scenarios
 * 
 * This test suite validates the parser behavior in challenging scenarios involving
 * @LocalTCC and @TransactionParticipant annotations:
 * 
 * Edge cases covered:
 * 1. Annotation conflicts (both annotations on same class/interface)
 * 2. Mixed annotation scenarios (different annotations on interface vs implementation)
 * 3. Complex inheritance chains with mixed annotations
 * 4. Multiple interface implementation with different annotations
 * 5. Deep inheritance hierarchies
 * 6. Error handling and boundary conditions (null values, empty strings)
 * 7. Performance with many conflicting instances
 * 8. Annotation precedence rules (implementation vs interface priority)
 * 
 * These tests ensure robust behavior in real-world scenarios where annotation
 * usage might be complex or inconsistent, validating that the parser handles
 * all edge cases gracefully while maintaining predictable behavior.
 */
public class AnnotationConflictTest {

    private LocalTCCRemotingParser parser;

    // Conflict scenarios: both annotations present
    @LocalTCC
    @TransactionParticipant
    public static class ConflictOnClassService {
        public boolean doSomething() { return true; }
    }

    @LocalTCC
    @TransactionParticipant
    public interface ConflictOnInterfaceService {
        boolean doSomething();
    }

    public static class ConflictOnInterfaceServiceImpl implements ConflictOnInterfaceService {
        @Override
        public boolean doSomething() { return true; }
    }

    // Mixed scenarios: different annotations on interface vs implementation
    @LocalTCC
    public interface LocalTCCInterface {
        boolean doSomething();
    }

    @TransactionParticipant
    public static class TransactionParticipantImpl implements LocalTCCInterface {
        @Override
        public boolean doSomething() { return true; }
    }

    @TransactionParticipant
    public interface TransactionParticipantInterface {
        boolean doSomething();
    }

    @LocalTCC
    public static class LocalTCCImpl implements TransactionParticipantInterface {
        @Override
        public boolean doSomething() { return true; }
    }

    // Multiple interface inheritance scenarios
    @LocalTCC
    public interface FirstInterface {
        boolean firstMethod();
    }

    @TransactionParticipant
    public interface SecondInterface {
        boolean secondMethod();
    }

    public static class MultipleInterfaceImpl implements FirstInterface, SecondInterface {
        @Override
        public boolean firstMethod() { return true; }
        
        @Override
        public boolean secondMethod() { return true; }
    }

    // Inheritance chain scenarios
    @LocalTCC
    public static class BaseService {
        public boolean baseMethod() { return true; }
    }

    @TransactionParticipant
    public static class ExtendedService extends BaseService {
        public boolean extendedMethod() { return true; }
    }

    // Deep inheritance chain
    public static class DeeplyExtendedService extends ExtendedService {
        public boolean deepMethod() { return true; }
    }

    // No annotation scenarios for edge case testing
    public static class NoAnnotationService {
        public boolean doSomething() { return true; }
    }

    public interface NoAnnotationInterface {
        boolean doSomething();
    }

    public static class NoAnnotationInterfaceImpl implements NoAnnotationInterface {
        @Override
        public boolean doSomething() { return true; }
    }

    @BeforeEach
    public void setUp() {
        parser = new LocalTCCRemotingParser();
    }

    // Test both annotations on the same class
    @Test
    public void testConflictOnClass_BothAnnotationsPresent() {
        ConflictOnClassService service = new ConflictOnClassService();
        
        // Should be recognized as a valid service despite having both annotations
        assertTrue(parser.isService(service, "conflictService"));
        assertTrue(parser.isReference(service, "conflictService"));
        
        // Should generate valid RemotingDesc
        RemotingDesc desc = parser.getServiceDesc(service, "conflictService");
        assertNotNull(desc);
        assertEquals(ConflictOnClassService.class, desc.getServiceClass());
        
        // Should not throw exceptions
        assertDoesNotThrow(() -> {
            parser.getServiceDesc(service, "conflictService");
        });
    }

    @Test
    public void testConflictOnInterface_BothAnnotationsPresent() {
        ConflictOnInterfaceServiceImpl service = new ConflictOnInterfaceServiceImpl();
        
        // Should be recognized despite interface having both annotations
        assertTrue(parser.isService(service, "conflictInterfaceService"));
        
        RemotingDesc desc = parser.getServiceDesc(service, "conflictInterfaceService");
        assertNotNull(desc);
        assertEquals(ConflictOnInterfaceService.class, desc.getServiceClass());
    }

    @Test
    public void testMixedAnnotations_LocalTCCInterfaceTransactionParticipantImpl() {
        TransactionParticipantImpl service = new TransactionParticipantImpl();
        
        // Should be recognized (impl has @TransactionParticipant, interface has @LocalTCC)
        assertTrue(parser.isService(service, "mixedService1"));
        
        RemotingDesc desc = parser.getServiceDesc(service, "mixedService1");
        assertNotNull(desc);
        
        // Either implementation class or interface should be used (depending on parser logic)
        // Both are valid as long as the service is recognized
        assertTrue(desc.getServiceClass() == TransactionParticipantImpl.class || 
                   desc.getServiceClass() == LocalTCCInterface.class,
                   "Service class should be either implementation or interface");
    }

    @Test
    public void testMixedAnnotations_TransactionParticipantInterfaceLocalTCCImpl() {
        LocalTCCImpl service = new LocalTCCImpl();
        
        // Should be recognized (both interface and impl have annotations)
        assertTrue(parser.isService(service, "mixedService2"));
        
        RemotingDesc desc = parser.getServiceDesc(service, "mixedService2");
        assertNotNull(desc);
        
        // The implementation annotation should take precedence
        assertEquals(LocalTCCImpl.class, desc.getServiceClass());
    }

    @Test
    public void testMultipleInterfaceInheritance() {
        MultipleInterfaceImpl service = new MultipleInterfaceImpl();
        
        // Should be recognized (implements interfaces with different annotations)
        assertTrue(parser.isService(service, "multipleInterfaceService"));
        
        RemotingDesc desc = parser.getServiceDesc(service, "multipleInterfaceService");
        assertNotNull(desc);
        
        // Should use one of the interfaces (behavior is deterministic based on iteration order)
        assertTrue(desc.getServiceClass() == FirstInterface.class || 
                  desc.getServiceClass() == SecondInterface.class);
    }

    @Test
    public void testInheritanceChain_BaseWithLocalTCC() {
        BaseService service = new BaseService();
        
        assertTrue(parser.isService(service, "baseService"));
        
        RemotingDesc desc = parser.getServiceDesc(service, "baseService");
        assertNotNull(desc);
        assertEquals(BaseService.class, desc.getServiceClass());
    }

    @Test
    public void testInheritanceChain_ExtendedWithTransactionParticipant() {
        ExtendedService service = new ExtendedService();
        
        // Should be recognized (has @TransactionParticipant, inherits from @LocalTCC)
        assertTrue(parser.isService(service, "extendedService"));
        
        RemotingDesc desc = parser.getServiceDesc(service, "extendedService");
        assertNotNull(desc);
        assertEquals(ExtendedService.class, desc.getServiceClass());
    }

    @Test
    public void testInheritanceChain_DeeplyExtended() {
        DeeplyExtendedService service = new DeeplyExtendedService();
        
        // Should inherit @TransactionParticipant from ExtendedService
        assertTrue(parser.isService(service, "deeplyExtendedService"));
        
        RemotingDesc desc = parser.getServiceDesc(service, "deeplyExtendedService");
        assertNotNull(desc);
        assertEquals(DeeplyExtendedService.class, desc.getServiceClass());
    }

    @Test
    public void testNoAnnotations_ShouldNotBeRecognized() {
        NoAnnotationService service = new NoAnnotationService();
        
        // Should not be recognized
        assertFalse(parser.isService(service, "noAnnotationService"));
        assertFalse(parser.isReference(service, "noAnnotationService"));
        
        // Should return null for getServiceDesc
        RemotingDesc desc = parser.getServiceDesc(service, "noAnnotationService");
        assertNull(desc);
    }

    @Test
    public void testNoAnnotationInterface_ShouldNotBeRecognized() {
        NoAnnotationInterfaceImpl service = new NoAnnotationInterfaceImpl();
        
        // Should not be recognized
        assertFalse(parser.isService(service, "noAnnotationInterfaceImpl"));
        
        // Should return null
        RemotingDesc desc = parser.getServiceDesc(service, "noAnnotationInterfaceImpl");
        assertNull(desc);
    }

    // Edge case: null handling
    @Test
    public void testNullService_ShouldThrowException() {
        assertThrows(RuntimeException.class, () -> {
            parser.isService(null, "nullService");
        });
        
        assertThrows(RuntimeException.class, () -> {
            parser.getServiceDesc(null, "nullService");
        });
    }

    @Test
    public void testNullBeanName_ShouldNotThrowException() {
        ConflictOnClassService service = new ConflictOnClassService();
        
        // Should handle null bean name gracefully
        assertDoesNotThrow(() -> {
            boolean result = parser.isService(service, null);
            assertTrue(result);
        });
        
        assertDoesNotThrow(() -> {
            RemotingDesc desc = parser.getServiceDesc(service, null);
            assertNotNull(desc);
        });
    }

    @Test
    public void testEmptyBeanName_ShouldNotThrowException() {
        ConflictOnClassService service = new ConflictOnClassService();
        
        // Should handle empty bean name gracefully
        assertDoesNotThrow(() -> {
            boolean result = parser.isService(service, "");
            assertTrue(result);
        });
        
        assertDoesNotThrow(() -> {
            RemotingDesc desc = parser.getServiceDesc(service, "");
            assertNotNull(desc);
        });
    }

    // Stress test: many conflicts
    @Test
    public void testManyConflicts_ShouldHandleGracefully() {
        for (int i = 0; i < 100; i++) {
            ConflictOnClassService service = new ConflictOnClassService();
            String beanName = "conflictService" + i;
            
            // Should handle many instances with conflicts without performance degradation
            assertTrue(parser.isService(service, beanName));
            
            RemotingDesc desc = parser.getServiceDesc(service, beanName);
            assertNotNull(desc);
            assertEquals(ConflictOnClassService.class, desc.getServiceClass());
        }
    }

    // Test annotation precedence
    @Test
    public void testAnnotationPrecedence_ImplementationOverInterface() {
        // When both implementation and interface have annotations,
        // implementation should take precedence
        
        @LocalTCC
        class TestImpl implements ConflictOnInterfaceService {
            @Override
            public boolean doSomething() { return true; }
        }
        
        TestImpl service = new TestImpl();
        RemotingDesc desc = parser.getServiceDesc(service, "testImpl");
        
        assertNotNull(desc);
        // Implementation class should be used, not the interface
        assertEquals(TestImpl.class, desc.getServiceClass());
    }

    // Test class hierarchy with mixed annotations
    @Test
    public void testComplexInheritanceHierarchy() {
        @TransactionParticipant
        class Level1 {
            public boolean level1Method() { return true; }
        }
        
        @LocalTCC
        class Level2 extends Level1 {
            public boolean level2Method() { return true; }
        }
        
        class Level3 extends Level2 {
            public boolean level3Method() { return true; }
        }
        
        Level3 service = new Level3();
        
        // Should be recognized (inherits annotations)
        assertTrue(parser.isService(service, "level3Service"));
        
        RemotingDesc desc = parser.getServiceDesc(service, "level3Service");
        assertNotNull(desc);
        assertEquals(Level3.class, desc.getServiceClass());
    }
} 