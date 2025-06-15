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
package org.apache.seata.common.transaction.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Transaction Participant annotation.
 * A general annotation for marking beans that participate in distributed transactions.
 * This annotation is designed to replace the specific usage of @LocalTCC in Saga scenarios
 * to avoid confusion, while also being applicable to TCC and other transaction modes.
 * <p>
 * In distributed transaction terminology, a "participant" is any component that takes part
 * in the transaction protocol, making this name semantically accurate across different
 * transaction patterns (TCC, Saga, XA, etc.).
 * 
 * @see org.apache.seata.rm.tcc.api.LocalTCC // the original TCC-specific annotation, still supported for backward compatibility
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface TransactionParticipant {
} 