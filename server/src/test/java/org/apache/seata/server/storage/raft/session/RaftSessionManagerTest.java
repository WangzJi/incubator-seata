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
package org.apache.seata.server.storage.raft.session;

import org.apache.seata.server.BaseSpringBootTest;
import org.apache.seata.server.storage.file.session.FileSessionManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RaftSessionManagerTest extends BaseSpringBootTest {

    @Test
    public void testRaftSessionManagerExtendsFileSessionManager() throws Exception {
        Class<?> clazz = Class.forName("org.apache.seata.server.storage.raft.session.RaftSessionManager");
        Assertions.assertTrue(FileSessionManager.class.isAssignableFrom(clazz));
    }

    @Test
    public void testRaftSessionManagerHasConstructor() throws Exception {
        Class<?> clazz = Class.forName("org.apache.seata.server.storage.raft.session.RaftSessionManager");
        Assertions.assertNotNull(clazz.getConstructor(String.class));
    }
}
