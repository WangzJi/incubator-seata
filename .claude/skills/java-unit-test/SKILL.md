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
- **构建工具**: Maven (使用 Maven Wrapper: `./mvnw`)
- **项目结构**: 多模块 Maven 项目
- **测试目录**: `<module>/src/test/java/`
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
- 识别代码所属的 Maven 模块（如 common, core, rm-datasource 等）
- 识别所有 public 和 protected 方法
- 分析方法的参数、返回值和异常
- 识别依赖关系（需要 mock 的对象）
- 理解业务逻辑和边界条件

**输出**: 列出需要测试的方法清单、测试点和所属模块名称

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
- 如需清理资源，使用 `@AfterEach`
- 合理使用 Mockito 的 `mock()`, `when()`, `verify()` 等方法
- 为断言添加描述性消息，帮助定位失败原因
- 避免测试实现细节，应测试行为和结果

### 6. 运行测试验证

**目标**: 确保测试能够正确运行

**步骤**:
- 运行新添加的测试用例
- 验证测试是否通过
- 检查是否有编译错误或警告
- 确认测试覆盖了预期的场景

**测试命令**:
```bash
# 运行指定模块的单个测试类
./mvnw test -pl <module-name> -Dtest=ClassNameTest

# 示例：运行 common 模块的 StringUtilsTest
./mvnw test -pl common -Dtest=StringUtilsTest

# 运行单个测试方法
./mvnw test -pl <module-name> -Dtest=ClassNameTest#methodNameTest

# 示例：运行特定测试方法
./mvnw test -pl common -Dtest=StringUtilsTest#isEmptyTest

# 运行整个模块的所有测试
./mvnw test -pl <module-name>

# 查看测试覆盖率（如果配置了 jacoco）
./mvnw test jacoco:report -pl <module-name>
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
- 所属模块和包路径
- 为哪些类/方法添加了测试
- 添加了多少个测试用例
- 测试覆盖的场景（正常、边界、异常）
- 测试运行结果
- 提供具体的运行命令（包含模块路径）
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

// With descriptive message
assertThat(result)
    .as("User should be active after registration")
    .isEqualTo(UserStatus.ACTIVE);
```

**异常测试最佳实践**:
```java
// Using AssertJ - recommended for detailed exception verification
assertThatThrownBy(() -> service.process(null))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage("Input cannot be null")
    .hasNoCause();

// Using JUnit - simple exception verification
Assertions.assertThrows(IllegalArgumentException.class,
    () -> service.process(null));

// Verify exception message
Exception exception = Assertions.assertThrows(
    IllegalArgumentException.class,
    () -> service.process(null));
assertThat(exception.getMessage()).contains("cannot be null");
```

**集合和字符串断言**:
```java
// Collection assertions
assertThat(list)
    .isNotEmpty()
    .hasSize(3)
    .contains("item1", "item2")
    .doesNotContainNull();

// String assertions
assertThat(result)
    .isNotBlank()
    .startsWith("prefix")
    .contains("substring")
    .matches("regex.*pattern");
```

### Mock 使用指南

**何时使用 Mock**:
- 测试依赖外部服务（数据库、网络、文件系统）
- 测试复杂对象的交互
- 隔离被测试单元

**Mock 基本用法**:
```java
// Create mock object
SomeService mockService = Mockito.mock(SomeService.class);

// Setup mock behavior
when(mockService.getData()).thenReturn(testData);
when(mockService.process(any())).thenReturn(result);
when(mockService.process(any())).thenThrow(new RuntimeException());

// Verify mock calls
verify(mockService, times(1)).getData();
verify(mockService, never()).delete();
verify(mockService, atLeastOnce()).process(any());
```

**验证传入参数 (ArgumentCaptor)**:
```java
// Capture and verify arguments
ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
verify(userService).saveUser(userCaptor.capture());

User capturedUser = userCaptor.getValue();
assertThat(capturedUser.getName()).isEqualTo("John");
assertThat(capturedUser.getAge()).isEqualTo(30);
```

**Stubbing 连续调用**:
```java
// Return different values on consecutive calls
when(mockService.getData())
    .thenReturn(data1)
    .thenReturn(data2)
    .thenThrow(new RuntimeException());

// First call returns data1, second returns data2, third throws exception
```

**何时避免 Mock**:
- 不要 mock 值对象（Value Objects）或数据类
- 不要 mock 简单的 POJO
- 过度使用 mock 可能表明设计问题
- 优先使用真实对象，只在必要时 mock

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

### 测试数据构建

**使用工厂方法创建测试数据**:
```java
// Create reusable test data builders
private User createTestUser() {
    return new User("John Doe", "john@example.com", 30);
}

private User createTestUserWithAge(int age) {
    return new User("John Doe", "john@example.com", age);
}

@Test
void userAgeValidationTest() {
    User youngUser = createTestUserWithAge(15);
    User adultUser = createTestUserWithAge(25);

    assertThat(service.isAdult(youngUser)).isFalse();
    assertThat(service.isAdult(adultUser)).isTrue();
}
```

**避免在测试中使用魔法值**:
```java
// Bad - magic values
void discountCalculationTest() {
    double result = service.calculateDiscount(1500.0, 0.15);
    assertThat(result).isEqualTo(1275.0);
}

// Good - use constants with meaningful names
void discountCalculationTest() {
    final double ORIGINAL_PRICE = 1500.0;
    final double DISCOUNT_RATE = 0.15;
    final double EXPECTED_PRICE = 1275.0;

    double result = service.calculateDiscount(ORIGINAL_PRICE, DISCOUNT_RATE);
    assertThat(result).isEqualTo(EXPECTED_PRICE);
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

## 测试反模式（避免这些常见错误）

### ❌ 测试实现细节而非行为
```java
// Bad - testing implementation
void getUserNameTest() {
    // Verifying internal field access
    assertThat(user.firstName).isEqualTo("John");
    assertThat(user.lastName).isEqualTo("Doe");
}

// Good - testing behavior
void getUserNameTest() {
    assertThat(user.getFullName()).isEqualTo("John Doe");
}
```

### ❌ 测试过于依赖执行顺序
```java
// Bad - tests depend on each other
@Test
void createUserTest() {
    userId = userService.createUser(user);
}

@Test
void updateUserTest() {
    userService.updateUser(userId, newData); // Depends on createUserTest
}
```

### ❌ 过度使用 Mock
```java
// Bad - mocking simple objects
User mockUser = Mockito.mock(User.class);
when(mockUser.getName()).thenReturn("John");

// Good - use real objects for simple data
User user = new User("John", 30);
```

### ❌ 一个测试测试多个场景
```java
// Bad - testing multiple things
void userServiceTest() {
    // Test creation
    User user = service.createUser(data);
    assertThat(user).isNotNull();

    // Test update
    service.updateUser(user.getId(), newData);

    // Test deletion
    service.deleteUser(user.getId());
}

// Good - separate tests for each scenario
void createUserTest() { /* ... */ }
void updateUserTest() { /* ... */ }
void deleteUserTest() { /* ... */ }
```

### ❌ 忽略异常消息验证
```java
// Bad - only checking exception type
assertThrows(IllegalArgumentException.class,
    () -> service.process(null));

// Good - verify exception message
assertThatThrownBy(() -> service.process(null))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage("Input cannot be null");
```

### ❌ 测试中有复杂逻辑
```java
// Bad - test has complex logic
void calculateDiscountTest() {
    double result = service.calculateDiscount(price);
    double expected = price > 100 ? price * 0.9 : price * 0.95;
    assertThat(result).isEqualTo(expected);
}

// Good - explicit expected values
void calculateDiscountForHighPriceTest() {
    double result = service.calculateDiscount(150.0);
    assertThat(result).isEqualTo(135.0); // 150 * 0.9
}
```

### ✅ 良好的测试特征
- **F.I.R.S.T. 原则**:
  - **Fast**: 测试应该快速运行
  - **Independent**: 测试之间相互独立
  - **Repeatable**: 可在任何环境重复执行
  - **Self-Validating**: 测试有明确的通过/失败结果
  - **Timely**: 测试应及时编写（理想情况下在代码之前 - TDD）

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
