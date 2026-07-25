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
package org.apache.seata.core.protocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server version holder test.
 */
class ServerVersionHolderTest {

    private static final String SERVER_ADDRESS = "127.0.0.1:8091";

    @BeforeEach
    void setUp() {
        ServerVersionHolder.clear();
    }

    @AfterEach
    void tearDown() {
        ServerVersionHolder.clear();
    }

    @Test
    void putAndGetServerVersionTest() {
        ServerVersionHolder.putServerVersion(SERVER_ADDRESS, "2.1.0");
        assertEquals("2.1.0", ServerVersionHolder.getServerVersion(SERVER_ADDRESS));

        // a re-registration overwrites the previous version
        ServerVersionHolder.putServerVersion(SERVER_ADDRESS, "2.6.0");
        assertEquals("2.6.0", ServerVersionHolder.getServerVersion(SERVER_ADDRESS));
    }

    @Test
    void getServerVersionReturnNullWhenNotExistTest() {
        assertNull(ServerVersionHolder.getServerVersion("127.0.0.1:9999"));
        assertNull(ServerVersionHolder.getServerVersion(null));
    }

    @Test
    void putBlankValueIgnoredTest() {
        ServerVersionHolder.putServerVersion(SERVER_ADDRESS, null);
        ServerVersionHolder.putServerVersion(SERVER_ADDRESS, "");
        assertNull(ServerVersionHolder.getServerVersion(SERVER_ADDRESS));

        ServerVersionHolder.putServerVersion(null, "2.6.0");
        assertNull(ServerVersionHolder.getServerVersion(null));
    }

    @Test
    void isServerAboveOrEqualVersionTest() {
        ServerVersionHolder.putServerVersion(SERVER_ADDRESS, "2.6.0");
        assertTrue(ServerVersionHolder.isServerAboveOrEqualVersion(SERVER_ADDRESS, Version.VERSION_2_6_0));
        assertTrue(ServerVersionHolder.isServerAboveOrEqualVersion(SERVER_ADDRESS, "2.5.0"));
        assertFalse(ServerVersionHolder.isServerAboveOrEqualVersion(SERVER_ADDRESS, "2.7.0"));
    }

    @Test
    void isServerAboveOrEqualVersionReturnFalseWhenUnknownTest() {
        assertFalse(ServerVersionHolder.isServerAboveOrEqualVersion("127.0.0.1:9999", Version.VERSION_2_6_0));
    }

    @Test
    void clearTest() {
        ServerVersionHolder.putServerVersion(SERVER_ADDRESS, "2.6.0");
        ServerVersionHolder.putServerVersion("127.0.0.1:8092", "2.6.0");

        ServerVersionHolder.clear();

        assertNull(ServerVersionHolder.getServerVersion(SERVER_ADDRESS));
        assertNull(ServerVersionHolder.getServerVersion("127.0.0.1:8092"));
    }
}
