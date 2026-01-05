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
package org.apache.seata.saga.engine.pcext.handlers;

import org.apache.seata.saga.engine.pcext.StateHandlerInterceptor;
import org.apache.seata.saga.statelang.domain.ExecutionStatus;
import org.apache.seata.saga.statelang.domain.StateMachineInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link SubStateMachineHandler}
 */
public class SubStateMachineHandlerTest {

    private SubStateMachineHandler handler;

    @BeforeEach
    public void setUp() {
        handler = new SubStateMachineHandler();
    }

    @Test
    public void addInterceptorAddToListTest() {
        StateHandlerInterceptor interceptor = mock(StateHandlerInterceptor.class);
        handler.addInterceptor(interceptor);
        assertTrue(handler.getInterceptors().contains(interceptor));
    }

    @Test
    public void addInterceptorDuplicateInterceptorNotAddTest() {
        StateHandlerInterceptor interceptor = mock(StateHandlerInterceptor.class);
        handler.addInterceptor(interceptor);
        handler.addInterceptor(interceptor);
        assertEquals(1, handler.getInterceptors().size());
    }

    @Test
    public void addInterceptorMultipleDifferentInterceptorsTest() {
        StateHandlerInterceptor interceptor1 = mock(StateHandlerInterceptor.class);
        StateHandlerInterceptor interceptor2 = mock(StateHandlerInterceptor.class);
        handler.addInterceptor(interceptor1);
        handler.addInterceptor(interceptor2);
        assertEquals(2, handler.getInterceptors().size());
        assertTrue(handler.getInterceptors().contains(interceptor1));
        assertTrue(handler.getInterceptors().contains(interceptor2));
    }

    @Test
    public void getInterceptorsReturnListTest() {
        List<StateHandlerInterceptor> interceptors = handler.getInterceptors();
        assertNotNull(interceptors);
    }

    @Test
    public void setInterceptorsTest() {
        List<StateHandlerInterceptor> interceptors = new ArrayList<>();
        StateHandlerInterceptor interceptor = mock(StateHandlerInterceptor.class);
        interceptors.add(interceptor);

        handler.setInterceptors(interceptors);

        assertSame(interceptors, handler.getInterceptors());
    }

    @Test
    public void setInterceptorsWithNullTest() {
        handler.setInterceptors(null);
        assertNull(handler.getInterceptors());
    }

    @Test
    public void addInterceptorWhenInterceptorsIsNullDoNothingTest() {
        handler.setInterceptors(null);
        StateHandlerInterceptor interceptor = mock(StateHandlerInterceptor.class);

        // Should not throw
        assertDoesNotThrow(() -> handler.addInterceptor(interceptor));
    }

    @Test
    public void handlerNotNullTest() {
        assertNotNull(handler);
    }

    // ========== 新增测试：覆盖 decideStatus 静态方法 ==========

    @Test
    public void decideStatusForwardWithSuccessReturnSUTest() throws Exception {
        StateMachineInstance instance = mock(StateMachineInstance.class);
        when(instance.getStatus()).thenReturn(ExecutionStatus.SU);

        ExecutionStatus result = invokeDecideStatus(instance, true);

        assertEquals(ExecutionStatus.SU, result);
    }

    @Test
    public void decideStatusForwardWithFailureReturnInstanceStatusTest() throws Exception {
        StateMachineInstance instance = mock(StateMachineInstance.class);
        when(instance.getStatus()).thenReturn(ExecutionStatus.FA);
        when(instance.getCompensationStatus()).thenReturn(null);

        ExecutionStatus result = invokeDecideStatus(instance, true);

        assertEquals(ExecutionStatus.FA, result);
    }

    @Test
    public void decideStatusWhenCompensationStatusIsNullReturnInstanceStatusTest() throws Exception {
        StateMachineInstance instance = mock(StateMachineInstance.class);
        when(instance.getStatus()).thenReturn(ExecutionStatus.FA);
        when(instance.getCompensationStatus()).thenReturn(null);

        ExecutionStatus result = invokeDecideStatus(instance, false);

        assertEquals(ExecutionStatus.FA, result);
    }

    @Test
    public void decideStatusWhenCompensationStatusIsFAReturnInstanceStatusTest() throws Exception {
        StateMachineInstance instance = mock(StateMachineInstance.class);
        when(instance.getStatus()).thenReturn(ExecutionStatus.FA);
        when(instance.getCompensationStatus()).thenReturn(ExecutionStatus.FA);

        ExecutionStatus result = invokeDecideStatus(instance, false);

        assertEquals(ExecutionStatus.FA, result);
    }

    @Test
    public void decideStatusWhenCompensationStatusIsSUReturnFATest() throws Exception {
        StateMachineInstance instance = mock(StateMachineInstance.class);
        when(instance.getCompensationStatus()).thenReturn(ExecutionStatus.SU);

        ExecutionStatus result = invokeDecideStatus(instance, false);

        assertEquals(ExecutionStatus.FA, result);
    }

    @Test
    public void decideStatusWhenCompensationStatusIsUNReturnUNTest() throws Exception {
        StateMachineInstance instance = mock(StateMachineInstance.class);
        when(instance.getCompensationStatus()).thenReturn(ExecutionStatus.UN);

        ExecutionStatus result = invokeDecideStatus(instance, false);

        assertEquals(ExecutionStatus.UN, result);
    }

    @Test
    public void decideStatusWhenCompensationStatusIsRUReturnUNTest() throws Exception {
        StateMachineInstance instance = mock(StateMachineInstance.class);
        when(instance.getCompensationStatus()).thenReturn(ExecutionStatus.RU);

        ExecutionStatus result = invokeDecideStatus(instance, false);

        assertEquals(ExecutionStatus.UN, result);
    }

    /**
     * 通过反射调用私有静态方法 decideStatus
     */
    private ExecutionStatus invokeDecideStatus(StateMachineInstance instance, boolean isForward) throws Exception {
        Method method = SubStateMachineHandler.class.getDeclaredMethod(
                "decideStatus", StateMachineInstance.class, boolean.class);
        method.setAccessible(true);
        return (ExecutionStatus) method.invoke(null, instance, isForward);
    }
}
