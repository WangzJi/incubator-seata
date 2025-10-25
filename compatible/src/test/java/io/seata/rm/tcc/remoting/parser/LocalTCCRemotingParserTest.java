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
package io.seata.rm.tcc.remoting.parser;

import io.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.common.exception.FrameworkException;
import org.apache.seata.integration.tx.api.remoting.Protocols;
import org.apache.seata.integration.tx.api.remoting.RemotingDesc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for LocalTCCRemotingParser.
 */
public class LocalTCCRemotingParserTest {

    private LocalTCCRemotingParser parser;

    @BeforeEach
    public void setUp() {
        parser = new LocalTCCRemotingParser();
    }

    @Test
    public void testDeprecatedAnnotation() {
        assertTrue(
                LocalTCCRemotingParser.class.isAnnotationPresent(Deprecated.class),
                "LocalTCCRemotingParser should be marked as @Deprecated");
    }

    @Test
    public void testExtendsApacheLocalTCCRemotingParser() {
        assertTrue(
                org.apache.seata.rm.tcc.remoting.parser.LocalTCCRemotingParser.class.isAssignableFrom(
                        LocalTCCRemotingParser.class),
                "LocalTCCRemotingParser should extend Apache Seata LocalTCCRemotingParser");
    }

    @Test
    public void testGetServiceDescWithLocalTCCOnClass() throws FrameworkException {
        TestLocalTCCOnClass bean = new TestLocalTCCOnClass();
        RemotingDesc desc = parser.getServiceDesc(bean, "testBean");

        assertNotNull(desc);
        assertTrue(desc.isService());
        assertEquals(Protocols.IN_JVM, desc.getProtocol());
        assertNotNull(desc.getServiceClass());
        assertEquals(bean, desc.getTargetBean());
    }

    @Test
    public void testGetServiceDescWithLocalTCCOnInterface() throws FrameworkException {
        TestLocalTCCOnInterfaceImpl bean = new TestLocalTCCOnInterfaceImpl();
        RemotingDesc desc = parser.getServiceDesc(bean, "testBean");

        assertNotNull(desc);
        assertTrue(desc.isService());
        assertEquals(Protocols.IN_JVM, desc.getProtocol());
        assertEquals(TestLocalTCCInterface.class.getName(), desc.getServiceClassName());
        assertEquals(TestLocalTCCInterface.class, desc.getServiceClass());
        assertEquals(bean, desc.getTargetBean());
    }

    @Test
    public void testGetServiceDescWithoutLocalTCC() {
        Object bean = new Object();
        assertThrows(
                FrameworkException.class,
                () -> {
                    parser.getServiceDesc(bean, "testBean");
                },
                "Should throw FrameworkException when no LocalTCC annotation found");
    }

    @Test
    public void testIsServiceWithLocalTCCClass() throws FrameworkException {
        assertTrue(parser.isService(TestLocalTCCOnClass.class));
    }

    @Test
    public void testIsServiceWithLocalTCCInterface() throws FrameworkException {
        assertTrue(parser.isService(TestLocalTCCOnInterfaceImpl.class));
    }

    @Test
    public void testIsServiceWithoutLocalTCC() throws FrameworkException {
        assertFalse(parser.isService(Object.class));
    }

    @Test
    public void testIsReferenceWithLocalTCCBean() {
        TestLocalTCCOnClass bean = new TestLocalTCCOnClass();
        assertTrue(parser.isReference(bean, "testBean"));
    }

    @Test
    public void testIsReferenceWithoutLocalTCCBean() {
        Object bean = new Object();
        assertFalse(parser.isReference(bean, "testBean"));
    }

    // Test classes with @LocalTCC on class
    @LocalTCC
    static class TestLocalTCCOnClass {
        public void prepare() {}

        public void commit() {}

        public void rollback() {}
    }

    // Test interface with @LocalTCC
    @LocalTCC
    interface TestLocalTCCInterface {
        void prepare();

        void commit();

        void rollback();
    }

    // Test implementation of @LocalTCC interface
    static class TestLocalTCCOnInterfaceImpl implements TestLocalTCCInterface {
        @Override
        public void prepare() {}

        @Override
        public void commit() {}

        @Override
        public void rollback() {}
    }
}
