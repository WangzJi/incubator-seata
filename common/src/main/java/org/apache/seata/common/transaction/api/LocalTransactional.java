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
 * Local Transactional annotation.
 * A general annotation for marking beans that participate in local transaction processing.
 * This annotation is designed to replace the specific usage of @LocalTCC in Saga scenarios
 * to avoid confusion, while also being applicable to TCC and other transaction modes.
 * 
 * The "Local" prefix indicates this is for local (non-remote) transaction resources,
 * and "Transactional" clearly indicates its purpose for transaction processing.
 * This annotation can enable local transaction features like connection reuse and unified commit.
 * 
 * When to Use @LocalTransactional:
 * - Saga scenarios with compensation actions
 * - Generic transaction participants (non-TCC specific)
 * - New implementations where transaction mode clarity is important
 * - Services that might be used in multiple transaction modes
 * 
 * Migration from @LocalTCC:
 * 
 * Before (confusing in Saga scenarios):
 * @LocalTCC
 * public interface PaymentService {
 *     @CompensationBusinessAction(...)
 *     void processPayment(...);
 * }
 * 
 * After (clear and semantic):
 * @LocalTransactional
 * public interface PaymentService {
 *     @CompensationBusinessAction(...)
 *     void processPayment(...);
 * }
 * 
 * Backward Compatibility:
 * - Existing @LocalTCC code continues to work unchanged
 * - Both annotations can coexist in the same application
 * - No functional changes required
 * 
 * @see org.apache.seata.rm.tcc.api.LocalTCC the original TCC-specific annotation, still supported for backward compatibility
 * @see org.apache.seata.saga.rm.api.CompensationBusinessAction commonly used with this annotation in Saga scenarios
 * @see org.apache.seata.rm.tcc.remoting.parser.LocalTCCRemotingParser the parser that handles both annotations
 * @since 2.5.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface LocalTransactional {
} 