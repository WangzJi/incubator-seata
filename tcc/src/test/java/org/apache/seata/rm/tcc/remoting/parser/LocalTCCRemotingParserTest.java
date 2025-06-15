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
import org.apache.seata.common.transaction.api.TransactionParticipant;
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
 * Tests the dual annotation support (@LocalTCC and @TransactionParticipant)
 */
public class LocalTCCRemotingParserTest {

    private LocalTCCRemotingParser parser;

    // Test classes with different annotation patterns
    @LocalTCC
    public static class LocalTCCOnlyService {
        public String doSomething() { return "local-tcc"; }
    }

    @TransactionParticipant
    public static class TransactionParticipantOnlyService {
        public String doSomething() { return "transaction-participant"; }
    }

    @LocalTCC
    @TransactionParticipant
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

    @TransactionParticipant
    public interface TransactionParticipantInterface {
        String doSomething();
    }

    @LocalTCC
    @TransactionParticipant
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

    public static class TransactionParticipantInterfaceImpl implements TransactionParticipantInterface {
        @Override
        public String doSomething() { return "impl-transaction-participant"; }
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
    public void testIsReference_TransactionParticipantOnly() {
        TransactionParticipantOnlyService service = new TransactionParticipantOnlyService();
        assertTrue(parser.isReference(service, "transactionParticipantService"));
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
    public void testIsService_TransactionParticipantOnly() {
        TransactionParticipantOnlyService service = new TransactionParticipantOnlyService();
        assertTrue(parser.isService(service, "transactionParticipantService"));
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
    public void testIsService_Class_TransactionParticipantOnly() throws FrameworkException {
        assertTrue(parser.isService(TransactionParticipantOnlyService.class));
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
        
        assertNotNull(desc);
        assertTrue(desc.isReference());
        assertTrue(desc.isService());
        assertEquals(Protocols.IN_JVM, desc.getProtocol());
        assertEquals(LocalTCCOnlyService.class, desc.getServiceClass());
        assertEquals(LocalTCCOnlyService.class.getName(), desc.getServiceClassName());
        assertEquals(service, desc.getTargetBean());
    }

    @Test
    public void testGetServiceDesc_TransactionParticipantOnImplementation() throws FrameworkException {
        TransactionParticipantOnlyService service = new TransactionParticipantOnlyService();
        RemotingDesc desc = parser.getServiceDesc(service, "transactionParticipantService");
        
        assertNotNull(desc);
        assertTrue(desc.isReference());
        assertTrue(desc.isService());
        assertEquals(Protocols.IN_JVM, desc.getProtocol());
        assertEquals(TransactionParticipantOnlyService.class, desc.getServiceClass());
        assertEquals(TransactionParticipantOnlyService.class.getName(), desc.getServiceClassName());
        assertEquals(service, desc.getTargetBean());
    }

    @Test
    public void testGetServiceDesc_LocalTCCOnInterface() throws FrameworkException {
        LocalTCCInterfaceImpl service = new LocalTCCInterfaceImpl();
        RemotingDesc desc = parser.getServiceDesc(service, "localTCCInterfaceImpl");
        
        assertNotNull(desc);
        assertTrue(desc.isReference());
        assertTrue(desc.isService());
        assertEquals(Protocols.IN_JVM, desc.getProtocol());
        assertEquals(LocalTCCInterface.class, desc.getServiceClass());
        assertEquals(LocalTCCInterface.class.getName(), desc.getServiceClassName());
        assertEquals(service, desc.getTargetBean());
    }

    @Test
    public void testGetServiceDesc_TransactionParticipantOnInterface() throws FrameworkException {
        TransactionParticipantInterfaceImpl service = new TransactionParticipantInterfaceImpl();
        RemotingDesc desc = parser.getServiceDesc(service, "transactionParticipantInterfaceImpl");
        
        assertNotNull(desc);
        assertTrue(desc.isReference());
        assertTrue(desc.isService());
        assertEquals(Protocols.IN_JVM, desc.getProtocol());
        assertEquals(TransactionParticipantInterface.class, desc.getServiceClass());
        assertEquals(TransactionParticipantInterface.class.getName(), desc.getServiceClassName());
        assertEquals(service, desc.getTargetBean());
    }

    @Test
    public void testGetServiceDesc_BothAnnotationsOnInterface() throws FrameworkException {
        BothAnnotationsInterfaceImpl service = new BothAnnotationsInterfaceImpl();
        RemotingDesc desc = parser.getServiceDesc(service, "bothAnnotationsInterfaceImpl");
        
        assertNotNull(desc);
        assertTrue(desc.isReference());
        assertTrue(desc.isService());
        assertEquals(Protocols.IN_JVM, desc.getProtocol());
        assertEquals(BothAnnotationsInterface.class, desc.getServiceClass());
        assertEquals(BothAnnotationsInterface.class.getName(), desc.getServiceClassName());
        assertEquals(service, desc.getTargetBean());
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
        
        assertNotNull(desc);
        assertEquals(BothAnnotationsService.class, desc.getServiceClass());
        // Should handle both annotations gracefully
        assertTrue(desc.isService());
        assertTrue(desc.isReference());
    }

    // Edge case: Class implements multiple interfaces with different annotations
    public static class MultipleInterfaceImpl implements LocalTCCInterface, TransactionParticipantInterface {
        @Override
        public String doSomething() { return "multiple"; }
    }

    @Test
    public void testMultipleInterfacesWithDifferentAnnotations() throws FrameworkException {
        MultipleInterfaceImpl service = new MultipleInterfaceImpl();
        RemotingDesc desc = parser.getServiceDesc(service, "multipleInterfaceImpl");
        
        assertNotNull(desc);
        // Should detect at least one annotation and work properly
        assertTrue(desc.isService());
        assertTrue(desc.isReference());
        // The first interface found with annotation should be used
        assertTrue(desc.getServiceClass() == LocalTCCInterface.class || 
                  desc.getServiceClass() == TransactionParticipantInterface.class);
    }
}
