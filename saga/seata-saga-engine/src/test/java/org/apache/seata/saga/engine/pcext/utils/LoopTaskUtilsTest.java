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
package org.apache.seata.saga.engine.pcext.utils;

import org.apache.seata.saga.statelang.domain.State;
import org.apache.seata.saga.statelang.domain.StateType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link LoopTaskUtils}
 */
public class LoopTaskUtilsTest {

    @Test
    public void matchLoopWhenStateTypeIsServiceTaskReturnTrueTest() {
        State state = mock(State.class);
        when(state.getType()).thenReturn(StateType.SERVICE_TASK);

        assertTrue(LoopTaskUtils.matchLoop(state));
    }

    @Test
    public void matchLoopWhenStateTypeIsScriptTaskReturnTrueTest() {
        State state = mock(State.class);
        when(state.getType()).thenReturn(StateType.SCRIPT_TASK);

        assertTrue(LoopTaskUtils.matchLoop(state));
    }

    @Test
    public void matchLoopWhenStateTypeIsSubStateMachineReturnTrueTest() {
        State state = mock(State.class);
        when(state.getType()).thenReturn(StateType.SUB_STATE_MACHINE);

        assertTrue(LoopTaskUtils.matchLoop(state));
    }

    @Test
    public void matchLoopWhenStateTypeIsChoiceReturnFalseTest() {
        State state = mock(State.class);
        when(state.getType()).thenReturn(StateType.CHOICE);

        assertFalse(LoopTaskUtils.matchLoop(state));
    }

    @Test
    public void matchLoopWhenStateIsNullReturnFalseTest() {
        assertFalse(LoopTaskUtils.matchLoop(null));
    }

    @Test
    public void reloadLoopCounterFromValidStateNameExtractCounterTest() {
        String stateName = "myState" + LoopTaskUtils.LOOP_STATE_NAME_PATTERN + "10";
        int counter = LoopTaskUtils.reloadLoopCounter(stateName);

        assertEquals(10, counter);
    }

    @Test
    public void reloadLoopCounterFromInvalidStateNameReturnNegativeOneTest() {
        String stateName = "myState";
        int counter = LoopTaskUtils.reloadLoopCounter(stateName);

        // 源码返回 -1 当找不到模式时
        assertEquals(-1, counter);
    }

    @Test
    public void reloadLoopCounterWithZeroCounterTest() {
        String stateName = "myState" + LoopTaskUtils.LOOP_STATE_NAME_PATTERN + "0";
        int counter = LoopTaskUtils.reloadLoopCounter(stateName);

        assertEquals(0, counter);
    }

    @Test
    public void reloadLoopCounterWithNullStateNameReturnNegativeOneTest() {
        int counter = LoopTaskUtils.reloadLoopCounter(null);

        assertEquals(-1, counter);
    }

    @Test
    public void reloadLoopCounterWithEmptyStateNameReturnNegativeOneTest() {
        int counter = LoopTaskUtils.reloadLoopCounter("");

        assertEquals(-1, counter);
    }

    @Test
    public void loopStateNamePatternConstantTest() {
        assertEquals("-loop-", LoopTaskUtils.LOOP_STATE_NAME_PATTERN);
    }
}
