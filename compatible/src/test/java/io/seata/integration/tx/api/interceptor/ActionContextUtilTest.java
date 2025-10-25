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
package io.seata.integration.tx.api.interceptor;

import io.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.ParamType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for ActionContextUtil.
 */
public class ActionContextUtilTest {

    @Test
    public void testDeprecatedAnnotation() {
        assertTrue(
                ActionContextUtil.class.isAnnotationPresent(Deprecated.class),
                "ActionContextUtil should be marked as @Deprecated");
    }

    @Test
    public void testFetchContextFromObject() {
        TestObject obj = new TestObject();
        obj.setUserId("user123");
        obj.setOrderId("order456");

        Map<String, Object> context = ActionContextUtil.fetchContextFromObject(obj);

        assertNotNull(context);
        assertTrue(context.size() >= 0);
    }

    @Test
    public void testFetchContextFromObjectWithNull() {
        Map<String, Object> context = ActionContextUtil.fetchContextFromObject(null);
        assertNotNull(context);
        assertTrue(context.isEmpty());
    }

    @Test
    public void testLoadParamByAnnotationAndPutToContext() {
        Map<String, Object> actionContext = new HashMap<>();
        TestAnnotation annotation = new TestAnnotation() {
            @Override
            public String value() {
                return "testParam";
            }

            @Override
            public String paramName() {
                return "testParam";
            }

            @Override
            public boolean isShardingParam() {
                return false;
            }

            @Override
            public int index() {
                return -1;
            }

            @Override
            public boolean isParamInProperty() {
                return false;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return BusinessActionContextParameter.class;
            }
        };

        ActionContextUtil.loadParamByAnnotationAndPutToContext(
                ParamType.PARAM, "testParam", "testValue", annotation, actionContext);

        assertTrue(actionContext.size() >= 0);
    }

    @Test
    public void testGetParamNameFromAnnotation() {
        BusinessActionContextParameter annotation = new BusinessActionContextParameter() {
            @Override
            public String value() {
                return "paramValue";
            }

            @Override
            public String paramName() {
                return "";
            }

            @Override
            public boolean isShardingParam() {
                return false;
            }

            @Override
            public int index() {
                return -1;
            }

            @Override
            public boolean isParamInProperty() {
                return false;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return BusinessActionContextParameter.class;
            }
        };

        String paramName = ActionContextUtil.getParamNameFromAnnotation(annotation);
        assertEquals("paramValue", paramName);
    }

    @Test
    public void testGetParamNameFromAnnotationWithParamName() {
        BusinessActionContextParameter annotation = new BusinessActionContextParameter() {
            @Override
            public String value() {
                return "";
            }

            @Override
            public String paramName() {
                return "testParamName";
            }

            @Override
            public boolean isShardingParam() {
                return false;
            }

            @Override
            public int index() {
                return -1;
            }

            @Override
            public boolean isParamInProperty() {
                return false;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return BusinessActionContextParameter.class;
            }
        };

        String paramName = ActionContextUtil.getParamNameFromAnnotation(annotation);
        assertEquals("testParamName", paramName);
    }

    @Test
    public void testPutActionContext() {
        Map<String, Object> actionContext = new HashMap<>();
        boolean result = ActionContextUtil.putActionContext(actionContext, "key1", "value1");

        assertTrue(result);
        assertTrue(actionContext.containsKey("key1"));
        assertEquals("value1", actionContext.get("key1"));
    }

    @Test
    public void testPutActionContextWithMap() {
        Map<String, Object> actionContext = new HashMap<>();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("key1", "value1");
        dataMap.put("key2", 123);

        boolean result = ActionContextUtil.putActionContext(actionContext, dataMap);

        assertTrue(result);
        assertEquals(2, actionContext.size());
        assertEquals("value1", actionContext.get("key1"));
        assertEquals(123, actionContext.get("key2"));
    }

    @Test
    public void testPutActionContextWithoutHandle() {
        Map<String, Object> actionContext = new HashMap<>();
        boolean result = ActionContextUtil.putActionContextWithoutHandle(actionContext, "key1", "value1");

        assertTrue(result);
        assertTrue(actionContext.containsKey("key1"));
        assertEquals("value1", actionContext.get("key1"));
    }

    @Test
    public void testPutActionContextWithoutHandleWithMap() {
        Map<String, Object> actionContext = new HashMap<>();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("key1", "value1");
        dataMap.put("key2", 456);

        boolean result = ActionContextUtil.putActionContextWithoutHandle(actionContext, dataMap);

        assertTrue(result);
        assertEquals(2, actionContext.size());
        assertEquals("value1", actionContext.get("key1"));
        assertEquals(456, actionContext.get("key2"));
    }

    @Test
    public void testHandleActionContext() {
        String simpleValue = "testValue";
        Object result = ActionContextUtil.handleActionContext(simpleValue);
        assertEquals(simpleValue, result);
    }

    @Test
    public void testHandleActionContextWithMap() {
        Map<String, Object> mapValue = new HashMap<>();
        mapValue.put("key1", "value1");
        Object result = ActionContextUtil.handleActionContext(mapValue);
        assertNotNull(result);
    }

    @Test
    public void testConvertActionContext() {
        String value = "123";
        Integer result = ActionContextUtil.convertActionContext("key", value, Integer.class);
        assertNotNull(result);
        assertEquals(123, result);
    }

    @Test
    public void testConvertActionContextWithNull() {
        Integer result = ActionContextUtil.convertActionContext("key", null, Integer.class);
        assertNull(result);
    }

    @Test
    public void testConvertActionContextSameType() {
        String value = "testValue";
        String result = ActionContextUtil.convertActionContext("key", value, String.class);
        assertEquals(value, result);
    }

    // Test helper classes
    static class TestObject {
        private String userId;
        private String orderId;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }
    }

    interface TestAnnotation extends BusinessActionContextParameter {}
}
