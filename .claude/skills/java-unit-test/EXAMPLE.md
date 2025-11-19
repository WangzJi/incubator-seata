# Java 单元测试 Skill 使用示例

## 示例 1: 为简单工具类补充测试

### 用户请求
```
为 org.apache.seata.common.util.NumberUtils 补充单元测试
```

### Skill 执行流程

#### 步骤 1: 分析目标代码
```
读取 common/src/main/java/org/apache/seata/common/util/NumberUtils.java
识别方法:
- isInteger(String str)
- parseLong(String str)
- parseBoolean(String str, boolean defaultValue)
```

#### 步骤 2: 检查现有测试
```
查找 common/src/test/java/org/apache/seata/common/util/NumberUtilsTest.java
发现: 已存在测试文件，但缺少边界条件测试
```

#### 步骤 3: 设计测试用例
```
需要补充的测试:
✅ isIntegerWithNullTest() - 测试 null 输入
✅ isIntegerWithEmptyStringTest() - 测试空字符串
✅ isIntegerWithNegativeNumberTest() - 测试负数
✅ parseLongWithInvalidInputTest() - 测试无效输入
✅ parseBooleanWithEdgeCasesTest() - 测试边界情况
```

#### 步骤 4: 生成测试代码
```java
@Test
void isIntegerWithNullTest() {
    assertThat(NumberUtils.isInteger(null)).isFalse();
}

@Test
void isIntegerWithEmptyStringTest() {
    assertThat(NumberUtils.isInteger("")).isFalse();
}

@ParameterizedTest
@MethodSource("provideIntegerTestCases")
void isIntegerTest(String input, boolean expected) {
    assertThat(NumberUtils.isInteger(input)).isEqualTo(expected);
}

static Stream<Arguments> provideIntegerTestCases() {
    return Stream.of(
        Arguments.of("123", true),
        Arguments.of("-456", true),
        Arguments.of("0", true),
        Arguments.of("12.34", false),
        Arguments.of("abc", false),
        Arguments.of("", false),
        Arguments.of(null, false)
    );
}
```

#### 步骤 5: 运行测试
```bash
./mvnw test -pl common -Dtest=NumberUtilsTest

[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

#### 步骤 6: 总结报告
```
✅ 单元测试补充完成

📋 测试总结：
- 目标类: NumberUtils
- 新增测试方法: 5 个
- 测试场景: 正常场景 3 个，边界条件 4 个，异常场景 2 个

🧪 测试结果：全部通过 ✅
```

## 示例 2: 为包含依赖的业务类编写测试

### 用户请求
```
为 DefaultResourceManager 的 registerResource 方法编写单元测试
```

### Skill 执行流程

#### 步骤 1: 分析依赖
```
识别到依赖:
- ResourceManagerCache (需要 mock)
- BranchRegisterRequest (测试数据)
- 可能抛出的异常: TransactionException
```

#### 步骤 2: 设计测试场景
```
测试场景:
1. 正常注册资源
2. 重复注册资源
3. 注册失败抛出异常
4. null 参数处理
```

#### 步骤 3: 生成带 Mock 的测试代码
```java
public class DefaultResourceManagerTest {

    private DefaultResourceManager resourceManager;
    private ResourceManagerCache mockCache;

    @BeforeEach
    void setUp() {
        mockCache = Mockito.mock(ResourceManagerCache.class);
        resourceManager = new DefaultResourceManager(mockCache);
    }

    @Test
    void registerResourceSuccessTest() {
        // Arrange
        String resourceId = "test-resource-01";
        BranchRegisterRequest request = new BranchRegisterRequest();
        request.setResourceId(resourceId);

        when(mockCache.containsKey(resourceId)).thenReturn(false);

        // Act
        resourceManager.registerResource(request);

        // Assert
        verify(mockCache, times(1)).put(eq(resourceId), any());
    }

    @Test
    void registerResourceWithNullThrowsExceptionTest() {
        // Act & Assert
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> resourceManager.registerResource(null));
    }

    @Test
    void registerResourceAlreadyExistsTest() {
        // Arrange
        String resourceId = "existing-resource";
        when(mockCache.containsKey(resourceId)).thenReturn(true);

        BranchRegisterRequest request = new BranchRegisterRequest();
        request.setResourceId(resourceId);

        // Act & Assert
        Assertions.assertThrows(TransactionException.class,
            () -> resourceManager.registerResource(request));
    }
}
```

## 示例 3: 使用参数化测试批量验证

### 用户请求
```
为 StringUtils.trim 方法补充全面的测试
```

### 生成的测试代码
```java
@ParameterizedTest
@MethodSource("provideTrimTestCases")
void trimTest(String input, String expected, String description) {
    assertThat(StringUtils.trim(input))
        .as(description)
        .isEqualTo(expected);
}

static Stream<Arguments> provideTrimTestCases() {
    return Stream.of(
        Arguments.of(null, null, "null input should return null"),
        Arguments.of("", "", "empty string should return empty"),
        Arguments.of("  ", "", "spaces should be trimmed to empty"),
        Arguments.of("abc", "abc", "no spaces should remain unchanged"),
        Arguments.of("  abc  ", "abc", "leading and trailing spaces should be removed"),
        Arguments.of("\t\nabc\r\n", "abc", "whitespace characters should be removed"),
        Arguments.of("a b c", "a b c", "internal spaces should be preserved")
    );
}
```

## 最佳实践提示

### 1. 明确指定测试范围
❌ 不好: "测试这个类"
✅ 好: "为 UserService.createUser() 方法编写测试，重点测试参数验证和异常处理"

### 2. 提供上下文信息
❌ 不好: "补充测试"
✅ 好: "为 TransactionManager 补充测试，现有测试已覆盖正常流程，需要补充异常场景和边界条件"

### 3. 指定特殊需求
✅ "使用参数化测试验证多种输入"
✅ "需要 mock 数据库连接"
✅ "测试多线程场景"

## 常见问题

**Q: Skill 会覆盖已有的测试吗？**
A: 不会。Skill 会先检查现有测试，只补充缺失的部分。

**Q: 如何确保生成的测试符合项目规范？**
A: Skill 会分析项目中的现有测试代码，学习并遵循项目的测试风格。

**Q: 可以为整个模块批量生成测试吗？**
A: 可以，但建议逐个类进行，以便更好地控制测试质量。

**Q: 生成的测试需要手动修改吗？**
A: Skill 会生成可运行的测试代码，但您可能需要根据具体业务逻辑调整测试数据或断言。

## 下一步

尝试使用这个 skill：
1. 找到一个需要测试的 Java 类
2. 告诉 Claude："为 [类名] 补充单元测试"
3. 查看生成的测试代码
4. 运行测试验证

祝测试愉快！🧪
