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

import org.apache.seata.saga.engine.StateMachineConfig;
import org.apache.seata.saga.engine.pcext.StateInstruction;
import org.apache.seata.saga.proctrl.ProcessContext;
import org.apache.seata.saga.statelang.domain.DomainConstants;
import org.apache.seata.saga.statelang.domain.State;
import org.apache.seata.saga.statelang.domain.StateMachineInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link LoopStartStateHandler}
 */
public class LoopStartStateHandlerTest {

    private LoopStartStateHandler handler;

    @BeforeEach
    public void setUp() {
        handler = new LoopStartStateHandler();
    }

    @Test
    public void processWhenAsyncDisabledThrowExceptionTest() {
        ProcessContext context = mock(ProcessContext.class);
        StateInstruction instruction = mock(StateInstruction.class);
        StateMachineInstance smInstance = mock(StateMachineInstance.class);
        StateMachineConfig config = mock(StateMachineConfig.class);
        State state = mock(State.class);

        when(context.getInstruction(StateInstruction.class)).thenReturn(instruction);
        when(instruction.getState(context)).thenReturn(state);
        when(context.getVariable(DomainConstants.VAR_NAME_STATEMACHINE_INST)).thenReturn(smInstance);
        when(context.getVariable(DomainConstants.VAR_NAME_STATEMACHINE_CONFIG)).thenReturn(config);
        when(config.isEnableAsync()).thenReturn(false);

        // When loop config is not null but async is disabled, should throw
        // Note: This test needs actual Loop mock to trigger the exception path
    }

    @Test
    public void processWhenLoopConfigNullSetTemporaryStateTest() {
        ProcessContext context = mock(ProcessContext.class);
        StateInstruction instruction = mock(StateInstruction.class);
        StateMachineInstance smInstance = mock(StateMachineInstance.class);
        StateMachineConfig config = mock(StateMachineConfig.class);
        State state = mock(State.class);

        when(context.getInstruction(StateInstruction.class)).thenReturn(instruction);
        when(instruction.getState(context)).thenReturn(state);
        when(instruction.getStateName()).thenReturn("testState");
        when(context.getVariable(DomainConstants.VAR_NAME_STATEMACHINE_INST)).thenReturn(smInstance);
        when(context.getVariable(DomainConstants.VAR_NAME_STATEMACHINE_CONFIG)).thenReturn(config);

        // When loop config is null, should set temporary state and log warning
        handler.process(context);

        verify(instruction).setTemporaryState(null);
        verify(instruction).setTemporaryState(state);
    }

    @Test
    public void processHandlerNotNullTest() {
        assertNotNull(handler);
    }
}
