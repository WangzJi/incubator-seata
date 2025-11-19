# Java 单元测试补充 Skill

## 简介

这是一个专门为 Seata 项目（以及其他 Java 项目）设计的单元测试自动补充技能。它可以帮助开发者快速为 Java 代码生成高质量的单元测试。

## 技术栈

- JUnit 5 (Jupiter)
- Mockito
- AssertJ
- Maven

## 如何使用

### 方式 1: 自动触发

当您提到需要编写或补充单元测试时，Claude 会自动使用此 skill：

```
"帮我为 XxxUtil 类补充单元测试"
"为 TransactionManager.commit() 方法编写测试"
```

### 方式 2: 明确调用

您也可以明确要求使用此 skill：

```
"使用 java-unit-test skill 为 ConfigLoader 补充测试"
```

## 功能特性

✅ 自动分析待测试代码的结构
✅ 识别缺失的测试场景
✅ 生成符合项目规范的测试代码
✅ 支持参数化测试
✅ 自动配置 Mock 对象
✅ 运行并验证测试结果
✅ 提供详细的测试报告

## Skill 工作流程

1. **分析目标代码** - 理解代码结构和功能
2. **检查现有测试** - 避免重复
3. **查找测试模板** - 保持风格一致
4. **设计测试用例** - 覆盖各种场景
5. **编写测试代码** - 生成高质量代码
6. **运行测试验证** - 确保测试通过
7. **代码审查优化** - 提高代码质量
8. **总结报告** - 提供清晰的反馈

## 示例场景

### 场景 1: 为工具类补充测试

```
用户: "为 org.apache.seata.common.util.CollectionUtils 补充单元测试"

Claude 会：
1. 读取 CollectionUtils 源代码
2. 检查已有的 CollectionUtilsTest
3. 识别缺失的测试场景
4. 生成测试代码
5. 运行测试验证
```

### 场景 2: 为业务逻辑类编写测试

```
用户: "为 DefaultCoordinator 类编写单元测试，重点测试事务提交逻辑"

Claude 会：
1. 分析 DefaultCoordinator 的依赖
2. 创建必要的 Mock 对象
3. 编写测试用例覆盖正常流程和异常情况
4. 验证测试结果
```

## 测试代码示例

生成的测试代码将遵循以下模式：

```java
/*
 * Apache License 2.0 header
 */
package org.apache.seata.xxx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link ClassName}
 */
public class ClassNameTest {

    @BeforeEach
    void setUp() {
        // Initialize
    }

    @Test
    void methodNameTest() {
        // Arrange
        // Act
        // Assert
        assertThat(result).isEqualTo(expected);
    }
}
```

## 配置信息

- **Skill 名称**: java-unit-test
- **类型**: 项目 Skill（存储在 `.claude/skills/java-unit-test/`）
- **适用范围**: Seata 项目及其他 Java/Maven 项目

## 维护说明

此 skill 是项目的一部分，会随代码一起提交到 Git 仓库，团队所有成员都可以使用。

如需修改 skill 的行为，请编辑 `SKILL.md` 文件。

## 支持的测试类型

- ✅ 单元测试（Unit Tests）
- ✅ 参数化测试（Parameterized Tests）
- ✅ 异常测试（Exception Tests）
- ✅ Mock 对象测试（Mock Tests）
- ✅ 边界值测试（Boundary Tests）

## 获取帮助

如果在使用过程中遇到问题，可以：
1. 查看 `SKILL.md` 了解详细的工作流程
2. 直接询问 Claude 具体的测试需求
3. 提供更多上下文帮助 Claude 更好地理解您的需求

---

**创建日期**: 2025-11-19
**适用项目**: Apache Seata
**维护者**: 开发团队
