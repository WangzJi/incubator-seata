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
package org.apache.seata.saga.engine.pcext.interceptors;

import org.apache.seata.saga.engine.pcext.handlers.ServiceTaskStateHandler;
import org.apache.seata.saga.engine.pcext.handlers.SubStateMachineHandler;
import org.apache.seata.saga.proctrl.ProcessContext;
import org.apache.seata.saga.statelang.domain.DomainConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link LoopTaskHandlerInterceptor}
 */
public class LoopTaskHandlerInterceptorTest {

    private LoopTaskHandlerInterceptor interceptor;

    @BeforeEach
    public void setUp() {
        interceptor = new LoopTaskHandlerInterceptor();
    }

    @Test
    public void matchForServiceTaskHandlerReturnTrueTest() {
        assertTrue(interceptor.match(ServiceTaskStateHandler.class));
    }

    @Test
    public void matchForSubStateMachineHandlerReturnTrueTest() {
        assertTrue(interceptor.match(SubStateMachineHandler.class));
    }

    @Test
    public void matchForNullClassReturnFalseTest() {
        assertFalse(interceptor.match(null));
    }

    @Test
    public void preProcessWhenNotLoopStateDoNothingTest() {
        ProcessContext context = mock(ProcessContext.class);
        when(context.hasVariable(DomainConstants.VAR_NAME_IS_LOOP_STATE)).thenReturn(false);

        // Should not throw and do nothing when not a loop state
        assertDoesNotThrow(() -> interceptor.preProcess(context));
    }

    @Test
    public void postProcessWhenNotLoopStateDoNothingTest() {
        ProcessContext context = mock(ProcessContext.class);
        when(context.hasVariable(DomainConstants.VAR_NAME_IS_LOOP_STATE)).thenReturn(false);

        // Should not throw and do nothing when not a loop state
        assertDoesNotThrow(() -> interceptor.postProcess(context, null));
    }

    @Test
    public void interceptorNotNullTest() {
        assertNotNull(interceptor);
    }
}
