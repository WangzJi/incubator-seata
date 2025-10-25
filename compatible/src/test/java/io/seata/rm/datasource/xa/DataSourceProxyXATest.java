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
package io.seata.rm.datasource.xa;

import io.seata.core.model.BranchType;
import io.seata.rm.datasource.mock.MockDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for DataSourceProxyXA.
 */
public class DataSourceProxyXATest {

    @Test
    public void testConstructorWithDataSource() {
        DataSource mockDataSource = new MockDataSource();
        DataSourceProxyXA proxy = new DataSourceProxyXA(mockDataSource);
        assertNotNull(proxy);
    }

    @Test
    public void testConstructorWithDataSourceAndResourceGroupId() {
        DataSource mockDataSource = new MockDataSource();
        DataSourceProxyXA proxy = new DataSourceProxyXA(mockDataSource, "test-resource-group");
        assertNotNull(proxy);
    }

    @Test
    public void testGetConnection() throws Exception {
        DataSource mockDataSource = new MockDataSource();
        DataSourceProxyXA proxy = new DataSourceProxyXA(mockDataSource);
        Connection connection = proxy.getConnection();
        assertNotNull(connection);
    }

    @Test
    public void testGetConnectionWithCredentials() throws Exception {
        DataSource mockDataSource = new MockDataSource();
        DataSourceProxyXA proxy = new DataSourceProxyXA(mockDataSource);
        Connection connection = proxy.getConnection("user", "password");
        assertNotNull(connection);
    }

    @Test
    public void testGetBranchType() {
        DataSource mockDataSource = new MockDataSource();
        DataSourceProxyXA proxy = new DataSourceProxyXA(mockDataSource);
        assertEquals(BranchType.XA, proxy.getBranchType());
    }

    @Test
    public void testGetTargetDataSource() {
        DataSource mockDataSource = new MockDataSource();
        DataSourceProxyXA proxy = new DataSourceProxyXA(mockDataSource);
        assertSame(mockDataSource, proxy.getTargetDataSource());
    }

    @Test
    public void testExtendsApacheDataSourceProxyXA() {
        assertTrue(
                org.apache.seata.rm.datasource.xa.DataSourceProxyXA.class.isAssignableFrom(DataSourceProxyXA.class),
                "DataSourceProxyXA should extend org.apache.seata.rm.datasource.xa.DataSourceProxyXA");
    }

    @Test
    public void testConstructorWithNestedProxy() {
        DataSource mockDataSource = new MockDataSource();
        DataSourceProxyXA proxy1 = new DataSourceProxyXA(mockDataSource);
        DataSourceProxyXA proxy2 = new DataSourceProxyXA(proxy1);
        assertNotNull(proxy2);
        assertSame(mockDataSource, proxy2.getTargetDataSource());
    }
}
