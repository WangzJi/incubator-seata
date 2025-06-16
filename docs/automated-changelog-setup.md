# 自动化Changelog配置指南

## 概述

此配置为Apache Seata项目提供了自动化的双语言changelog生成功能。系统会根据PR的标签和描述自动生成英文和中文版本的更新日志。

## 文件结构

```
.github/
├── release-drafter-en.yml      # 英文版配置
├── release-drafter-zh.yml      # 中文版配置
├── pr-labeler.yml              # PR自动标签配置
└── workflows/
    ├── release-drafter-en.yml  # 英文版工作流
    └── release-drafter-zh.yml  # 中文版工作流
```

## 工作原理

### 1. PR标签自动化
- 系统会根据分支名称、PR标题和文件变更自动为PR添加标签
- 支持的标签类型包括：
  - `feature` - 新功能
  - `bugfix` - 问题修复
  - `docs` - 文档更新
  - `refactor` - 代码重构
  - `test` - 测试相关
  - `optimize` - 性能优化
  - `security` - 安全修复

### 2. 双语言Changelog生成
- 每次PR合并时自动更新draft release
- 生成英文版本（标签：`v{version}-en`）
- 生成中文版本（标签：`v{version}-zh`）
- 自动保存到`changes/en-us/`和`changes/zh-cn/`目录

## 使用方法

### 1. PR标题格式建议 (遵循CONTRIBUTING.md规范)

根据项目的CONTRIBUTING.md文档，建议使用以下commit message格式：

```
feature: 添加新的事务管理功能
bugfix: 修复连接池泄漏问题
docs: 更新安装指南
test: 添加单元测试覆盖
refactor: 重构配置管理模块
```

支持的类型：
- `feature` - 新功能
- `bugfix` - 问题修复
- `docs` - 文档更新
- `refactor` - 代码重构
- `test` - 测试相关

### 2. 分支命名建议

系统会根据分支名称自动添加标签：

```
feature/new-transaction-mode    # 自动添加 feature 标签
bugfix/connection-leak          # 自动添加 bugfix 标签
docs/update-readme              # 自动添加 docs 标签
test/add-unit-tests            # 自动添加 test 标签
refactor/code-cleanup          # 自动添加 refactor 标签
```

### 3. 手动标签管理

如果自动标签不准确，可以在PR页面手动调整：

1. 进入PR页面
2. 在右侧找到"Labels"选项
3. 添加或移除相应的类型标签

## 生成的Changelog格式

### 英文版示例
```markdown
# Apache Seata v2.5.0

We are excited to announce the release of Apache Seata v2.5.0! 🎉

## What's Changed

### 🚀 New Features
- Support for PostgreSQL in AT mode @username (#1234)
- Add distributed lock mechanism @username (#1235)

### 🐛 Bug Fixes  
- Fix connection pool leak issue @username (#1236)
- Resolve transaction timeout problem @username (#1237)

**Full Changelog**: https://github.com/apache/incubator-seata/compare/v2.4.0...v2.5.0
```

### 中文版示例
```markdown
# Apache Seata v2.5.0

我们很兴奋地宣布 Apache Seata v2.5.0 发布！🎉

## 更新内容

### 🚀 新功能
- 支持PostgreSQL AT模式 @username (#1234)
- 添加分布式锁机制 @username (#1235)

### 🐛 问题修复
- 修复连接池泄漏问题 @username (#1236) 
- 解决事务超时问题 @username (#1237)

**完整更新日志**: https://github.com/apache/incubator-seata/compare/v2.4.0...v2.5.0
```

## 版本管理

系统会根据PR标签自动确定版本递增规则：

- **Major版本**: 包含`breaking-change`标签的PR
- **Minor版本**: 包含`feature`标签的PR  
- **Patch版本**: 包含`bugfix`标签的PR

## 发布流程

1. **开发阶段**: PR合并时自动更新draft release
2. **准备发布**: 检查并编辑draft release内容
3. **正式发布**: 将draft release发布为正式版本
4. **同步文件**: 系统会自动将changelog保存到对应目录

## 自定义配置

### 添加新的标签类型

在`.github/release-drafter-{en|zh}.yml`中添加新的category：

```yaml
categories:
  - title: '🔧 新类型'
    labels:
      - 'type: new-type'
      - 'new-type'
```

### 修改模板格式

在配置文件的`template`部分修改输出格式。

## 注意事项

1. **权限要求**: 工作流需要`contents: write`权限
2. **标签一致性**: 确保英文和中文配置中的标签保持一致
3. **手动review**: 发布前请review生成的changelog内容
4. **备份机制**: 原有的手动changelog维护方式可以作为备份

## 故障排除

### 常见问题

1. **工作流不触发**
   - 检查分支配置是否正确
   - 确认权限设置是否完整

2. **标签未自动添加**
   - 检查PR标题格式
   - 确认分支命名是否符合规范

3. **生成内容不正确**
   - 检查PR标签是否准确
   - 确认配置文件语法是否正确

### 手动触发

如果需要手动触发changelog生成：

```bash
# 在GitHub Actions页面手动运行工作流
# 或通过GitHub API触发
```

## 贡献

如果您发现配置问题或有改进建议，请：

1. 提交Issue描述问题
2. 提交PR with改进方案
3. 在讨论区分享想法

## 英文PR内容的中文生成方案

### 当前挑战
当PR标题和描述为英文时，如何生成对应的中文changelog是一个常见问题。基于项目现状分析，提供以下解决方案：

### 方案1: PR标题双语言规范 (推荐)

建议在PR标题中同时包含英文和中文描述：

```
feature: Add PostgreSQL support / 添加PostgreSQL支持
bugfix: Fix connection leak issue / 修复连接泄漏问题
docs: Update installation guide / 更新安装指南
```

**优势**: 
- 无需额外工具
- 确保翻译准确性
- 便于国际化协作

### 方案2: 使用标准化描述模板

在PR描述中使用双语言模板：

```markdown
## 变更说明 / Change Description

**English**: Add support for PostgreSQL database in AT mode
**中文**: 在AT模式中添加对PostgreSQL数据库的支持

## 影响范围 / Impact Scope
- 数据库支持 / Database support
- AT模式功能 / AT mode functionality
```

### 方案3: 基于AI翻译的半自动化流程

配置AI翻译服务来处理英文内容：

1. **release-drafter生成英文版本**
2. **AI翻译服务处理英文内容**
3. **人工审核和调整翻译结果**
4. **生成最终中文版本**

示例翻译映射：
```yaml
# 常见术语翻译对照
translations:
  "Add support for": "添加对...的支持"
  "Fix issue with": "修复...的问题" 
  "Optimize": "优化"
  "Refactor": "重构"
  "Update": "更新"
  "Remove": "移除"
  "connection pool": "连接池"
  "transaction": "事务"
  "database": "数据库"
```

### 方案4: 分阶段实施策略

**第一阶段**: 使用当前配置生成英文版changelog
**第二阶段**: 基于英文版本进行翻译
**第三阶段**: 合并生成双语言版本

### 实施建议

1. **短期方案**: 采用方案1或方案2，通过规范化PR标题和描述解决
2. **中期方案**: 开发翻译工具或集成AI翻译服务
3. **长期方案**: 完全自动化的双语言changelog生成

### 翻译质量保证

为确保中文翻译质量，建议：

1. **建立术语库**: 维护技术术语的标准翻译对照表
2. **人工审核**: 重要版本发布前进行人工审核
3. **社区参与**: 邀请中文社区参与翻译校对
4. **版本一致性**: 确保两个语言版本的信息完全对应

### 配置示例

可以在workflow中添加翻译步骤：

```yaml
# .github/workflows/release-drafter-zh.yml (修改版本)
- name: Generate Chinese Changelog
  run: |
    # 1. 获取英文版本内容
    # 2. 调用翻译服务
    # 3. 生成中文版本
    # 4. 人工审核标记
```

### 最佳实践

基于现有项目分析，建议：

1. **保持结构一致**: 中英文版本使用相同的分类和格式
2. **术语标准化**: 使用统一的技术术语翻译
3. **链接保持**: 保留原始PR链接，方便追溯
4. **时间同步**: 两个版本同时更新，避免信息滞后

---

此文档会随着配置的更新而持续维护。如有疑问，请联系项目维护者。 