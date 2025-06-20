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
package org.apache.seata.rm.tcc.remoting.parser;

import org.apache.seata.common.exception.FrameworkException;
import org.apache.seata.common.transaction.api.LocalTransactional;
import org.apache.seata.common.util.ReflectionUtil;
import org.apache.seata.integration.tx.api.remoting.Protocols;
import org.apache.seata.integration.tx.api.remoting.RemotingDesc;
import org.apache.seata.integration.tx.api.remoting.parser.AbstractedRemotingParser;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.springframework.aop.framework.AopProxyUtils;

import java.util.Set;

/**
 * Parser for transaction participant beans with @LocalTCC or @LocalTransactional annotations
 * 
 * This parser supports both @LocalTCC (for backward compatibility) and @LocalTransactional 
 * annotations, providing a unified approach for transaction participant service detection.
 * 
 * Supported Annotations:
 * - LocalTCC - TCC-specific annotation, maintained for backward compatibility
 * - LocalTransactional - Generic transaction participant annotation (recommended for Saga)
 * 
 * Detection Priority:
 * 1. Interface annotations (higher priority)
 * 2. Implementation class annotations (fallback)
 * 
 * Mixed Usage Support:
 * The parser handles scenarios where both annotations are present, allowing for:
 * - Gradual migration from @LocalTCC to @LocalTransactional
 * - Mixed codebases with different annotation strategies
 * - Backward compatibility with existing @LocalTCC usage
 * 
 * Performance Considerations:
 * Annotation detection is performed using reflection and should be cached appropriately
 * in high-throughput scenarios.
 * 
 * @see LocalTCC TCC-specific annotation
 * @see LocalTransactional Generic transaction participant annotation
 * @see org.apache.seata.integration.tx.api.remoting.parser.AbstractedRemotingParser Base class
 * @since 1.0.0 (dual annotation support added in 2.5.0)
 */
public class LocalTCCRemotingParser extends AbstractedRemotingParser {

    @Override
    public boolean isReference(Object bean, String beanName) {
        return isLocalTransactional(bean);
    }

    @Override
    public boolean isService(Object bean, String beanName) {
        return isLocalTransactional(bean);
    }

    @Override
    public boolean isService(Class<?> beanClass) throws FrameworkException {
        return isLocalTransactional(beanClass);
    }

    @Override
    public RemotingDesc getServiceDesc(Object bean, String beanName) throws FrameworkException {
        if (!this.isRemoting(bean, beanName)) {
            return null;
        }
        RemotingDesc remotingDesc = new RemotingDesc();
        remotingDesc.setReference(this.isReference(bean, beanName));
        remotingDesc.setService(this.isService(bean, beanName));
        remotingDesc.setProtocol(Protocols.IN_JVM);
        Class<?> classType = bean.getClass();
        
        // First priority: check if annotations are present on any implemented interfaces
        // Interface annotations take precedence for service definition
        Set<Class<?>> interfaceClasses = ReflectionUtil.getInterfaces(classType);
        for (Class<?> interClass : interfaceClasses) {
            if (isLocalTransactional(interClass)) {
                remotingDesc.setServiceClassName(interClass.getName());
                remotingDesc.setServiceClass(interClass);
                remotingDesc.setTargetBean(bean);
                return remotingDesc;
            }
        }
        
        // Second priority: check if annotations are present on the implementation class
        // Fall back to implementation class if no interface annotations found
        if (isLocalTransactional(classType)) {
            remotingDesc.setServiceClass(AopProxyUtils.ultimateTargetClass(bean));
            remotingDesc.setServiceClassName(remotingDesc.getServiceClass().getName());
            remotingDesc.setTargetBean(bean);
            return remotingDesc;
        }
        throw new FrameworkException("Couldn't parser any Remoting info");
    }

    @Override
    public short getProtocol() {
        return Protocols.IN_JVM;
    }

    /**
     * Check if the given bean is annotated with local transaction participant annotations
     * Supports both {@link LocalTCC} and {@link LocalTransactional} annotations
     * 
     * @param bean the bean to check
     * @return true if the bean or its interfaces have either LocalTCC or LocalTransactional annotation
     */
    private boolean isLocalTransactional(Object bean) {
        return isLocalTransactional(bean.getClass());
    }

    /**
     * Check if the given class or its interfaces are annotated with local transaction participant annotations
     * Supports both {@link LocalTCC} and {@link LocalTransactional} annotations
     * 
     * @param classType the class type to check
     * @return true if the class has either LocalTCC or LocalTransactional annotation
     */
    private boolean isLocalTransactional(Class<?> classType) {
        // Check the class itself first for better performance
        if (hasLocalTransactionalAnnotation(classType)) {
            return true;
        }
        
        // Check all interfaces
        Set<Class<?>> interfaceClasses = ReflectionUtil.getInterfaces(classType);
        return interfaceClasses.stream().anyMatch(this::hasLocalTransactionalAnnotation);
    }

    /**
     * Check if a class has either LocalTCC or LocalTransactional annotation
     * 
     * @param clazz the class to check
     * @return true if the class has either annotation
     */
    private boolean hasLocalTransactionalAnnotation(Class<?> clazz) {
        return clazz.isAnnotationPresent(LocalTCC.class) || clazz.isAnnotationPresent(LocalTransactional.class);
    }
}
