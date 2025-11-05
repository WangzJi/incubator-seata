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
package org.apache.seata.server.cluster.raft;

import org.apache.seata.server.store.StoreConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RaftStateMachineTest {

    private RaftStateMachine raftStateMachine;

    @BeforeEach
    public void setUp() {
        StoreConfig.setStartupParameter("file", "file", "file");
        raftStateMachine = new RaftStateMachine("test-group");
    }

    @Test
    public void testConstructor() {
        assertNotNull(raftStateMachine);
    }

    @Test
    public void testIsLeaderWhenNotLeader() {
        assertFalse(raftStateMachine.isLeader());
    }

    @Test
    public void testIsLeaderInitialState() {
        RaftStateMachine newMachine = new RaftStateMachine("default");
        assertFalse(newMachine.isLeader());
    }

    @Test
    public void testMultipleInstances() {
        RaftStateMachine machine1 = new RaftStateMachine("group1");
        RaftStateMachine machine2 = new RaftStateMachine("group2");

        assertNotNull(machine1);
        assertNotNull(machine2);
        assertNotSame(machine1, machine2);
    }
}
