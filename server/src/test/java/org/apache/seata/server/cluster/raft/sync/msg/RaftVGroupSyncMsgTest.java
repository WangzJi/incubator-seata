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
package org.apache.seata.server.cluster.raft.sync.msg;

import org.apache.seata.core.store.MappingDO;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class RaftVGroupSyncMsgTest {

    @Test
    public void testDefaultConstructor() {
        RaftVGroupSyncMsg msg = new RaftVGroupSyncMsg();
        assertNotNull(msg);
        assertNull(msg.getMappingDO());
        assertNull(msg.getMsgType());
    }

    @Test
    public void testConstructorWithParameters() {
        MappingDO mappingDO = new MappingDO();
        mappingDO.setVGroup("vgroup");
        mappingDO.setCluster("cluster");
        RaftVGroupSyncMsg msg = new RaftVGroupSyncMsg(mappingDO, RaftSyncMsgType.ADD_VGROUP_MAPPING);

        assertEquals(RaftSyncMsgType.ADD_VGROUP_MAPPING, msg.getMsgType());
        assertEquals(mappingDO, msg.getMappingDO());
    }

    @Test
    public void testSetAndGetMappingDO() {
        RaftVGroupSyncMsg msg = new RaftVGroupSyncMsg();
        MappingDO mappingDO = new MappingDO();
        mappingDO.setVGroup("vgroup2");
        mappingDO.setCluster("cluster2");
        msg.setMappingDO(mappingDO);

        assertEquals(mappingDO, msg.getMappingDO());
    }

    @Test
    public void testSerialization() throws Exception {
        MappingDO mappingDO = new MappingDO();
        mappingDO.setVGroup("test-vgroup");
        mappingDO.setCluster("test-cluster");
        RaftVGroupSyncMsg original = new RaftVGroupSyncMsg(mappingDO, RaftSyncMsgType.REMOVE_VGROUP_MAPPING);

        // Serialize
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(original);
        oos.flush();
        byte[] serialized = bos.toByteArray();

        // Deserialize
        ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
        ObjectInputStream ois = new ObjectInputStream(bis);
        RaftVGroupSyncMsg deserialized = (RaftVGroupSyncMsg) ois.readObject();

        // Verify
        assertEquals(original.getMsgType(), deserialized.getMsgType());
        assertEquals(original.getMappingDO().getVGroup(), deserialized.getMappingDO().getVGroup());
        assertEquals(original.getMappingDO().getCluster(), deserialized.getMappingDO().getCluster());
    }
}
