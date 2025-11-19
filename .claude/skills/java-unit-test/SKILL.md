---
name: java-unit-test
description: 为 Java 项目补充单元测试。当用户需要为指定的 Java 类、方法或模块编写单元测试时使用此技能。适用于 JUnit 5、Mockito 测试框架。
---

# Java 单元测试补充 Skill

为 Java 项目自动生成和补充高质量的单元测试代码。

## 适用场景

- 为新编写的 Java 类添加单元测试
- 为现有代码补充缺失的测试用例
- 提高代码的测试覆盖率
- 为重构后的代码更新测试

## 项目测试技术栈

基于 Seata 项目的测试框架配置：

- **测试框架**: JUnit 5 (Jupiter)
- **Mock 框架**: Mockito
- **断言库**: AssertJ (`assertThat`) 和 JUnit Assertions
- **构建工具**: Maven
- **测试目录**: `src/test/java/`
- **许可证**: Apache License 2.0

## ⚠️ 重要：测试方法命名规范

**所有测试方法必须使用小驼峰命名，并以 `Test` 结尾！**

**正确示例**:
```java
void isEmptyTest()                    // ✅ Correct
void trimToNullTest()                 // ✅ Correct
void parseIntegerWithNullTest()       // ✅ Correct
void commitTransactionThrowsExceptionTest()  // ✅ Correct
```

**错误示例**:
```java
void testIsEmpty()                    // ❌ Wrong: test prefix
void test_is_empty()                  // ❌ Wrong: underscore naming
void IsEmptyTest()                    // ❌ Wrong: capitalized first letter
void isEmpty()                        // ❌ Wrong: missing Test suffix
```

## 工作流程

为确保测试质量和一致性，请按照以下步骤执行：

### 1. 分析目标代码

**目标**: 理解待测试的代码结构和功能

**步骤**:
- 读取待测试的源代码文件
- 识别所有 public 和 protected 方法
- 分析方法的参数、返回值和异常
- 识别依赖关系（需要 mock 的对象）
- 理解业务逻辑和边界条件

**输出**: 列出需要测试的方法清单和测试点

### 2. 检查现有测试

**目标**: 避免重复编写已有的测试

**步骤**:
- 查找对应的测试文件（如 `Foo.java` -> `FooTest.java`）
- 分析已有测试覆盖的方法和场景
- 识别缺失的测试场景

**输出**: 标识出缺失测试的方法和场景

### 3. 查找测试模板

**目标**: 保持测试风格一致

**步骤**:
- 在同一模块或相似模块中查找参考测试代码
- 分析项目的测试命名规范
- 识别常用的测试模式（如参数化测试、异常测试等）

**关键模式**:
```java
// Parameterized test example
@ParameterizedTest
@MethodSource("provideTestData")
void methodWithMultipleInputsTest(InputType input, ExpectedType expected) {
    assertThat(actualResult).isEqualTo(expected);
}

static Stream<Arguments> provideTestData() {
    return Stream.of(
        Arguments.of(input1, expected1),
        Arguments.of(input2, expected2)
    );
}

// Exception test example
@Test
void methodThrowsExceptionTest() {
    Assertions.assertThrows(ExceptionType.class,
        () -> methodUnderTest(invalidInput));
}

// Mock object example
@Test
void methodWithMockTest() {
    // Arrange
    SomeService mockService = Mockito.mock(SomeService.class);
    when(mockService.someMethod()).thenReturn(expectedValue);

    // Act
    Result result = classUnderTest.methodUnderTest();

    // Assert
    assertThat(result).isEqualTo(expectedValue);
    verify(mockService).someMethod();
}
```

### 4. 设计测试用例

**目标**: 设计全面的测试场景

**测试类型**:
- **正常场景测试**: 验证正确的输入产生预期的输出
- **边界值测试**: 测试边界条件（null, 空字符串, 0, 负数等）
- **异常场景测试**: 验证异常情况的处理
- **参数化测试**: 使用多组数据验证同一逻辑

**测试原则**:
- 每个测试方法只测试一个场景
- 测试方法命名清晰描述测试内容
- 遵循 AAA 模式：Arrange（准备）、Act（执行）、Assert（断言）
- 测试应该是独立的、可重复的

### 5. 编写测试代码

**目标**: 生成高质量的测试代码

**代码结构**:
```java
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
package org.apache.seata.xxx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;

/**
 * Unit test for {@link ClassName}
 */
public class ClassNameTest {

    private ClassName classUnderTest;

    @BeforeEach
    void setUp() {
        // Initialize test object
        classUnderTest = new ClassName();
    }

    @Test
    void methodNameTest() {
        // Arrange
        // Prepare test data

        // Act
        // Execute method under test

        // Assert
        // Verify results
        assertThat(result).isEqualTo(expected);
    }
}
```

**注意事项**:
- 使用 Apache License 2.0 头部注释
- 优先使用 AssertJ 的 `assertThat` 进行断言
- 对于简单断言可以使用 JUnit 的 `Assertions`
- 使用 `@BeforeEach` 进行测试前的初始化
- 合理使用 Mockito 的 `mock()`, `when()`, `verify()` 等方法

### 6. 运行测试验证

**目标**: 确保测试能够正确运行

**步骤**:
- 运行新添加的测试用例
- 验证测试是否通过
- 检查是否有编译错误或警告
- 确认测试覆盖了预期的场景

**测试命令**:
```bash
# 运行单个测试类
mvn test -Dtest=ClassNameTest

# 运行单个测试方法
mvn test -Dtest=ClassNameTest#testMethodName

# 查看测试覆盖率（如果配置了 jacoco）
mvn test jacoco:report
```

### 7. 代码审查和优化

**目标**: 提高测试代码质量

**检查清单**:
- [ ] 测试命名是否清晰表达测试意图
- [ ] 是否覆盖了所有重要的测试场景
- [ ] 测试代码是否易于理解和维护
- [ ] 是否正确使用了 mock 对象
- [ ] 断言是否足够明确
- [ ] 是否有重复代码可以提取

### 8. 总结报告

**目标**: 向用户提供清晰的测试总结

**报告内容**:
- 为哪些类/方法添加了测试
- 添加了多少个测试用例
- 测试覆盖的场景（正常、边界、异常）
- 测试运行结果
- 后续建议（如需要补充的场景）

## 最佳实践

### 测试命名规范

**测试方法命名**: 小驼峰 + Test 结尾

- `methodNameTest()` - 测试指定方法
- `methodNameWithConditionTest()` - 测试特定条件下的方法
- `methodNameThrowsExceptionTest()` - 测试异常情况
- `methodNameReturnsExpectedValueTest()` - 测试返回值

**示例**:
```java
void isEmptyTest()  // Test isEmpty method
void trimToNullTest()  // Test trimToNull method
void parseIntegerWithNullTest()  // Test parseInt method with null input
void commitTransactionThrowsExceptionTest()  // Test exception scenario
```

### 断言选择

**优先使用 AssertJ** (更流畅、可读性强):
```java
assertThat(actual).isEqualTo(expected);
assertThat(actual).isNotNull();
assertThat(actual).isTrue();
assertThat(list).hasSize(3);
assertThat(list).contains("item");
```

**JUnit Assertions** (简单场景):
```java
Assertions.assertTrue(condition);
Assertions.assertFalse(condition);
Assertions.assertNull(object);
Assertions.assertThrows(Exception.class, () -> {...});
```

### Mock 使用指南

**何时使用 Mock**:
- 测试依赖外部服务（数据库、网络、文件系统）
- 测试复杂对象的交互
- 隔离被测试单元

**Mock 示例**:
```java
// Create mock object
SomeService mockService = Mockito.mock(SomeService.class);

// Setup mock behavior
when(mockService.getData()).thenReturn(testData);
when(mockService.process(any())).thenThrow(new RuntimeException());

// Verify mock calls
verify(mockService, times(1)).getData();
verify(mockService, never()).delete();
```

### 参数化测试使用

当需要用多组数据测试同一逻辑时：
```java
@ParameterizedTest
@MethodSource("provideTestCases")
void methodWithMultipleInputsTest(String input, String expected) {
    String result = methodUnderTest(input);
    assertThat(result).isEqualTo(expected);
}

static Stream<Arguments> provideTestCases() {
    return Stream.of(
        Arguments.of("input1", "expected1"),
        Arguments.of("input2", "expected2"),
        Arguments.of(null, null),
        Arguments.of("", "")
    );
}
```

## 常见测试场景

### 1. 工具类测试
- 测试各种输入组合
- 重点测试边界条件（null, 空, 特殊字符）
- 使用参数化测试提高效率

### 2. 业务逻辑测试
- Mock 外部依赖
- 验证方法调用顺序和次数
- 测试异常处理

### 3. 数据访问层测试
- 使用内存数据库或 Mock
- 测试 CRUD 操作
- 验证事务行为

### 4. 异常处理测试
```java
@Test
void methodThrowsExceptionTest() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> method(invalidInput));
}
```

## 注意事项

1. **保持测试独立性**: 每个测试不应依赖其他测试的执行顺序
2. **测试数据管理**: 使用测试专用的数据，不要依赖生产数据
3. **性能考虑**: 避免测试中有耗时操作，必要时使用 Mock
4. **可维护性**: 测试代码也需要高质量，便于后续维护
5. **文档价值**: 好的测试本身就是最好的文档

## 快速开始

直接告诉我您要测试的类或方法，我将：
1. 分析代码结构
2. 设计测试用例
3. 生成测试代码
4. 运行验证测试

例如：
- "为 StringUtils 类补充单元测试"
- "为 TransactionManager.commit() 方法编写测试"
- "提高 ConfigLoader 模块的测试覆盖率"
