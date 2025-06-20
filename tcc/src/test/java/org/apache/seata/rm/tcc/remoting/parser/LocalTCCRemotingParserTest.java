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
package org.apache.seata.rm.tcc.remoting.parser;

import org.apache.seata.common.exception.FrameworkException;
import org.apache.seata.common.transaction.api.LocalTransactional;
import org.apache.seata.integration.tx.api.remoting.Protocols;
import org.apache.seata.integration.tx.api.remoting.RemotingDesc;
import org.apache.seata.rm.tcc.api.LocalTCC;
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
 * Unit tests for LocalTCCRemotingParser
 * Tests the dual annotation support (@LocalTCC and @LocalTransactional)
 * 
 * Test scenarios covered:
 * 1. Single @LocalTCC annotation on implementation class
 * 2. Single @LocalTransactional annotation on implementation class  
 * 3. Both annotations on implementation class (edge case)
 * 4. Single @LocalTCC annotation on interface with implementation
 * 5. Single @LocalTransactional annotation on interface with implementation
 * 6. Both annotations on interface with implementation (edge case)
 * 7. No annotations (negative test cases)
 * 8. Multiple interfaces with different annotations
 * 
 * The tests verify:
 * - Annotation detection accuracy
 * - Service/reference identification
 * - RemotingDesc generation correctness
 * - Backward compatibility with existing @LocalTCC usage
 * - Forward compatibility with new @LocalTransactional usage
 */
public class LocalTCCRemotingParserTest {

    private LocalTCCRemotingParser parser;

    // Test classes with different annotation patterns
    @LocalTCC
    public static class LocalTCCOnlyService {
        public String doSomething() { return "local-tcc"; }
    }

    @LocalTransactional
    public static class LocalTransactionalOnlyService {
        public String doSomething() { return "transaction-participant"; }
    }

    @LocalTCC
    @LocalTransactional
    public static class BothAnnotationsService {
        public String doSomething() { return "both"; }
    }

    public static class NoAnnotationService {
        public String doSomething() { return "none"; }
    }

    @LocalTCC
    public interface LocalTCCInterface {
        String doSomething();
    }

    @LocalTransactional
    public interface LocalTransactionalInterface {
        String doSomething();
    }

    @LocalTCC
    @LocalTransactional
    public interface BothAnnotationsInterface {
        String doSomething();
    }

    public interface NoAnnotationInterface {
        String doSomething();
    }

    public static class LocalTCCInterfaceImpl implements LocalTCCInterface {
        @Override
        public String doSomething() { return "impl-local-tcc"; }
    }

    public static class LocalTransactionalInterfaceImpl implements LocalTransactionalInterface {
        @Override
        public String doSomething() { return "impl-local-transactional"; }
    }

    public static class BothAnnotationsInterfaceImpl implements BothAnnotationsInterface {
        @Override
        public String doSomething() { return "impl-both"; }
    }

    public static class NoAnnotationInterfaceImpl implements NoAnnotationInterface {
        @Override
        public String doSomething() { return "impl-none"; }
    }

    @BeforeEach
    public void setUp() {
        parser = new LocalTCCRemotingParser();
    }

    @Test
    public void testIsReference_LocalTCCOnly() {
        LocalTCCOnlyService service = new LocalTCCOnlyService();
        assertTrue(parser.isReference(service, "localTCCService"));
    }

    @Test
    public void testIsReference_LocalTransactionalOnly() {
        LocalTransactionalOnlyService service = new LocalTransactionalOnlyService();
        assertTrue(parser.isReference(service, "localTransactionalService"));
    }

    @Test
    public void testIsReference_BothAnnotations() {
        BothAnnotationsService service = new BothAnnotationsService();
        assertTrue(parser.isReference(service, "bothAnnotationsService"));
    }

    @Test
    public void testIsReference_NoAnnotations() {
        NoAnnotationService service = new NoAnnotationService();
        assertFalse(parser.isReference(service, "noAnnotationService"));
    }

    @Test
    public void testIsService_LocalTCCOnly() {
        LocalTCCOnlyService service = new LocalTCCOnlyService();
        assertTrue(parser.isService(service, "localTCCService"));
    }

    @Test
    public void testIsService_LocalTransactionalOnly() {
        LocalTransactionalOnlyService service = new LocalTransactionalOnlyService();
        assertTrue(parser.isService(service, "localTransactionalService"));
    }

    @Test
    public void testIsService_BothAnnotations() {
        BothAnnotationsService service = new BothAnnotationsService();
        assertTrue(parser.isService(service, "bothAnnotationsService"));
    }

    @Test
    public void testIsService_NoAnnotations() {
        NoAnnotationService service = new NoAnnotationService();
        assertFalse(parser.isService(service, "noAnnotationService"));
    }

    @Test
    public void testIsService_Class_LocalTCCOnly() throws FrameworkException {
        assertTrue(parser.isService(LocalTCCOnlyService.class));
    }

    @Test
    public void testIsService_Class_LocalTransactionalOnly() throws FrameworkException {
        assertTrue(parser.isService(LocalTransactionalOnlyService.class));
    }

    @Test
    public void testIsService_Class_BothAnnotations() throws FrameworkException {
        assertTrue(parser.isService(BothAnnotationsService.class));
    }

    @Test
    public void testIsService_Class_NoAnnotations() throws FrameworkException {
        assertFalse(parser.isService(NoAnnotationService.class));
    }

    @Test
    public void testGetServiceDesc_LocalTCCOnImplementation() throws FrameworkException {
        LocalTCCOnlyService service = new LocalTCCOnlyService();
        RemotingDesc desc = parser.getServiceDesc(service, "localTCCService");
        
        assertRemotingDescWithServiceClass(desc, service, LocalTCCOnlyService.class);
    }

    @Test
    public void testGetServiceDesc_LocalTransactionalOnImplementation() throws FrameworkException {
        LocalTransactionalOnlyService service = new LocalTransactionalOnlyService();
        RemotingDesc desc = parser.getServiceDesc(service, "localTransactionalService");
        
        assertRemotingDescWithServiceClass(desc, service, LocalTransactionalOnlyService.class);
    }

    @Test
    public void testGetServiceDesc_LocalTCCOnInterface() throws FrameworkException {
        LocalTCCInterfaceImpl service = new LocalTCCInterfaceImpl();
        RemotingDesc desc = parser.getServiceDesc(service, "localTCCInterfaceImpl");
        
        assertRemotingDescWithServiceClass(desc, service, LocalTCCInterface.class);
    }

    @Test
    public void testGetServiceDesc_LocalTransactionalOnInterface() throws FrameworkException {
        LocalTransactionalInterfaceImpl service = new LocalTransactionalInterfaceImpl();
        RemotingDesc desc = parser.getServiceDesc(service, "localTransactionalInterfaceImpl");
        
        assertRemotingDescWithServiceClass(desc, service, LocalTransactionalInterface.class);
    }

    @Test
    public void testGetServiceDesc_BothAnnotationsOnInterface() throws FrameworkException {
        BothAnnotationsInterfaceImpl service = new BothAnnotationsInterfaceImpl();
        RemotingDesc desc = parser.getServiceDesc(service, "bothAnnotationsInterfaceImpl");
        
        assertRemotingDescWithServiceClass(desc, service, BothAnnotationsInterface.class);
    }

    @Test
    public void testGetServiceDesc_NoAnnotations_ReturnsNull() {
        NoAnnotationService service = new NoAnnotationService();
        
        // Should return null for services without annotations
        RemotingDesc desc = parser.getServiceDesc(service, "noAnnotationService");
        assertNull(desc);
    }

    @Test
    public void testGetServiceDesc_NoAnnotationsOnInterface_ReturnsNull() {
        NoAnnotationInterfaceImpl service = new NoAnnotationInterfaceImpl();
        
        // Should return null for services without annotations
        RemotingDesc desc = parser.getServiceDesc(service, "noAnnotationInterfaceImpl");
        assertNull(desc);
    }

    @Test
    public void testGetProtocol() {
        assertEquals(Protocols.IN_JVM, parser.getProtocol());
    }

    @Test
    public void testBothAnnotationsOnImplementation_PrefersBoth() throws FrameworkException {
        BothAnnotationsService service = new BothAnnotationsService();
        RemotingDesc desc = parser.getServiceDesc(service, "bothAnnotationsService");
        
        // Should handle both annotations gracefully
        assertRemotingDescWithServiceClass(desc, service, BothAnnotationsService.class);
    }

    // Edge case: Class implements multiple interfaces with different annotations
    public static class MultipleInterfaceImpl implements LocalTCCInterface, LocalTransactionalInterface {
        @Override
        public String doSomething() { return "multiple"; }
    }

    @Test
    public void testMultipleInterfacesWithDifferentAnnotations() throws FrameworkException {
        MultipleInterfaceImpl service = new MultipleInterfaceImpl();
        RemotingDesc desc = parser.getServiceDesc(service, "multipleInterfaceImpl");
        
        // Should detect at least one annotation and work properly
        assertValidRemotingDesc(desc, service);
        
        // Verify that one of the annotated interfaces is selected as the service class
        Class<?> serviceClass = desc.getServiceClass();
        assertTrue(isAnnotatedInterface(serviceClass), 
                   "Service class should be one of the annotated interfaces, but was: " + serviceClass.getSimpleName());
    }
    
    /**
     * Helper method to check if a class is one of our supported annotated interfaces
     */
    private boolean isAnnotatedInterface(Class<?> clazz) {
        return clazz == LocalTCCInterface.class || clazz == LocalTransactionalInterface.class;
    }
    
    /**
     * Helper method to verify common RemotingDesc properties
     */
    private void assertValidRemotingDesc(RemotingDesc desc, Object expectedTargetBean) {
        assertNotNull(desc, "RemotingDesc should not be null");
        assertTrue(desc.isReference(), "Should be a reference");
        assertTrue(desc.isService(), "Should be a service");
        assertEquals(Protocols.IN_JVM, desc.getProtocol(), "Should use IN_JVM protocol");
        assertEquals(expectedTargetBean, desc.getTargetBean(), "Target bean should match");
    }
    
    /**
     * Helper method to verify RemotingDesc with expected service class
     */
    private void assertRemotingDescWithServiceClass(RemotingDesc desc, Object targetBean, Class<?> expectedServiceClass) {
        assertValidRemotingDesc(desc, targetBean);
        assertEquals(expectedServiceClass, desc.getServiceClass(), "Service class should match");
        assertEquals(expectedServiceClass.getName(), desc.getServiceClassName(), "Service class name should match");
    }
}
