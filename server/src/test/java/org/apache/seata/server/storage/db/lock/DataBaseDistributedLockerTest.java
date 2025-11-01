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
package org.apache.seata.server.storage.db.lock;

import org.apache.seata.core.store.DistributedLockDO;
import org.apache.seata.core.store.DistributedLocker;
import org.apache.seata.server.BaseSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "dbCaseEnabled", matches = "true")
public class DataBaseDistributedLockerTest extends BaseSpringBootTest {

    private DataBaseDistributedLocker dataBaseDistributedLocker;

    @BeforeEach
    public void setUp() {
        dataBaseDistributedLocker = new DataBaseDistributedLocker();
    }

    @AfterEach
    public void tearDown() {
        // Clean up test locks
        try {
            DistributedLockDO cleanupLock = new DistributedLockDO();
            cleanupLock.setLockKey("test-key");
            cleanupLock.setLockValue("");
            cleanupLock.setExpireTime(0L);
            dataBaseDistributedLocker.releaseLock(cleanupLock);

            cleanupLock.setLockKey("test-key-2");
            dataBaseDistributedLocker.releaseLock(cleanupLock);

            cleanupLock.setLockKey("test-key-expired");
            dataBaseDistributedLocker.releaseLock(cleanupLock);

            cleanupLock.setLockKey("test-key-conflict");
            dataBaseDistributedLocker.releaseLock(cleanupLock);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    public void testInstanceCreation() {
        Assertions.assertNotNull(dataBaseDistributedLocker);
    }

    @Test
    public void testImplementsDistributedLocker() {
        Assertions.assertTrue(dataBaseDistributedLocker instanceof DistributedLocker);
    }

    @Test
    public void testAcquireAndReleaseLock() {
        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");
        lockDO.setLockValue("test-value");
        lockDO.setExpireTime(60000L);

        boolean acquired = dataBaseDistributedLocker.acquireLock(lockDO);
        Assertions.assertTrue(acquired, "Lock acquisition should succeed");

        boolean released = dataBaseDistributedLocker.releaseLock(lockDO);
        Assertions.assertTrue(released, "Lock release should succeed");
    }

    @Test
    public void testAcquireLockConflict() {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key-conflict");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(60000L);

        // First acquisition should succeed
        boolean firstAcquired = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(firstAcquired, "First lock acquisition should succeed");

        // Second acquisition with different value should fail
        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key-conflict");
        lockDO2.setLockValue("holder-2");
        lockDO2.setExpireTime(60000L);

        boolean secondAcquired = dataBaseDistributedLocker.acquireLock(lockDO2);
        Assertions.assertFalse(secondAcquired, "Conflicting lock acquisition should fail");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO1);
    }

    @Test
    public void testAcquireLockAfterExpired() throws InterruptedException {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key-expired");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(100L); // 100ms expiration

        // First acquisition should succeed
        boolean firstAcquired = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(firstAcquired, "First lock acquisition should succeed");

        // Wait for lock to expire
        Thread.sleep(150);

        // Second acquisition should succeed after expiration
        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key-expired");
        lockDO2.setLockValue("holder-2");
        lockDO2.setExpireTime(60000L);

        boolean secondAcquired = dataBaseDistributedLocker.acquireLock(lockDO2);
        Assertions.assertTrue(secondAcquired, "Lock acquisition should succeed after expiration");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO2);
    }

    @Test
    public void testReleaseLockNotOwned() {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key-2");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(60000L);

        // Acquire lock with holder-1
        boolean acquired = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(acquired, "Lock acquisition should succeed");

        // Try to release with different holder
        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key-2");
        lockDO2.setLockValue("holder-2");
        lockDO2.setExpireTime(60000L);

        boolean released = dataBaseDistributedLocker.releaseLock(lockDO2);
        // Release should succeed but not actually release (as per implementation)
        Assertions.assertTrue(released, "Release should return true even if not owned");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO1);
    }

    @Test
    public void testAcquireLockWithZeroExpireTime() {
        DistributedLockDO lockDO = new DistributedLockDO();
        lockDO.setLockKey("test-key");
        lockDO.setLockValue("test-value");
        lockDO.setExpireTime(0L);

        boolean acquired = dataBaseDistributedLocker.acquireLock(lockDO);
        Assertions.assertTrue(acquired, "Lock acquisition with zero expire time should succeed");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO);
    }

    @Test
    public void testReacquireSameLock() {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(60000L);

        // First acquisition
        boolean firstAcquired = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(firstAcquired, "First acquisition should succeed");

        // Try to reacquire same lock with same holder - should fail due to conflict
        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key");
        lockDO2.setLockValue("holder-1");
        lockDO2.setExpireTime(60000L);

        boolean secondAcquired = dataBaseDistributedLocker.acquireLock(lockDO2);
        Assertions.assertFalse(secondAcquired, "Reacquisition should fail while lock is held");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO1);
    }

    @Test
    public void testAcquireLockAfterRelease() {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(60000L);

        // Acquire and release
        boolean acquired1 = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(acquired1, "First acquisition should succeed");

        boolean released = dataBaseDistributedLocker.releaseLock(lockDO1);
        Assertions.assertTrue(released, "Release should succeed");

        // Acquire again with different holder
        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key");
        lockDO2.setLockValue("holder-2");
        lockDO2.setExpireTime(60000L);

        boolean acquired2 = dataBaseDistributedLocker.acquireLock(lockDO2);
        Assertions.assertTrue(acquired2, "Acquisition after release should succeed");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO2);
    }

    @Test
    public void testMultipleLockKeys() {
        DistributedLockDO lockDO1 = new DistributedLockDO();
        lockDO1.setLockKey("test-key");
        lockDO1.setLockValue("holder-1");
        lockDO1.setExpireTime(60000L);

        DistributedLockDO lockDO2 = new DistributedLockDO();
        lockDO2.setLockKey("test-key-2");
        lockDO2.setLockValue("holder-2");
        lockDO2.setExpireTime(60000L);

        // Both locks should succeed (different keys)
        boolean acquired1 = dataBaseDistributedLocker.acquireLock(lockDO1);
        Assertions.assertTrue(acquired1, "First lock should succeed");

        boolean acquired2 = dataBaseDistributedLocker.acquireLock(lockDO2);
        Assertions.assertTrue(acquired2, "Second lock with different key should succeed");

        // Clean up
        dataBaseDistributedLocker.releaseLock(lockDO1);
        dataBaseDistributedLocker.releaseLock(lockDO2);
    }
}
