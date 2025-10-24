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
package io.seata.saga.proctrl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test cases for HierarchicalProcessContext interface compatibility wrapper.
 */
public class HierarchicalProcessContextTest {

    @Test
    public void testDeprecatedAnnotation() {
        assertTrue(
                HierarchicalProcessContext.class.isAnnotationPresent(Deprecated.class),
                "HierarchicalProcessContext should be marked as @Deprecated");
    }

    @Test
    public void testIsInterface() {
        assertTrue(HierarchicalProcessContext.class.isInterface(), "HierarchicalProcessContext should be an interface");
    }

    @Test
    public void testExtendsApacheHierarchicalProcessContext() {
        assertTrue(
                org.apache.seata.saga.proctrl.HierarchicalProcessContext.class.isAssignableFrom(
                        HierarchicalProcessContext.class),
                "HierarchicalProcessContext should extend org.apache.seata.saga.proctrl.HierarchicalProcessContext");
    }
}
