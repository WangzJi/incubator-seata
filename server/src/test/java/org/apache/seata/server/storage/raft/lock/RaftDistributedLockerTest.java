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
package org.apache.seata.server.storage.raft.lock;

import org.apache.seata.core.store.DistributedLockDO;
import org.apache.seata.server.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RaftDistributedLockerTest extends BaseSpringBootTest {

    private RaftDistributedLocker raftDistributedLocker;

    @BeforeEach
    public void setUp() {
        raftDistributedLocker = new RaftDistributedLocker();
    }

    @Test
    public void testReleaseLock() {
        DistributedLockDO lockDO = createDistributedLockDO();
        boolean result = raftDistributedLocker.releaseLock(lockDO);
        Assertions.assertTrue(result);
    }

    @Test
    public void testReleaseLockWithNull() {
        boolean result = raftDistributedLocker.releaseLock(null);
        Assertions.assertTrue(result);
    }

    @Test
    public void testReleaseLockWithEmptyKey() {
        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("");
        boolean result = raftDistributedLocker.releaseLock(lockDO);
        Assertions.assertTrue(result);
    }

    private DistributedLockDO createDistributedLockDO() {
        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-lock-key");
        lockDO.setLockValue("test-lock-value");
        lockDO.setExpireTime(30000L);
        return lockDO;
    }
}
