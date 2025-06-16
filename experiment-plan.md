# Automated Changelog Experiment Plan

## 目标
实验两种工具的多语言changelog生成效果，选择最适合Seata项目的方案。

## 实验方案

### Option A: Release-Drafter
**优势:**
- 简单易用，模板灵活
- 社区使用广泛
- 配置简单

**配置文件:**
```yaml
# .github/release-drafter-en.yml
name-template: 'v$RESOLVED_VERSION'
tag-template: 'v$RESOLVED_VERSION'
template: |
  ## What's Changed
  
  $CHANGES
  
  **Full Changelog**: https://github.com/$OWNER/$REPOSITORY/compare/$PREVIOUS_TAG...v$RESOLVED_VERSION

categories:
  - title: '🚀 Features'
    labels: ['type: feature', 'feature']
  - title: '🐛 Bug Fixes'
    labels: ['type: bugfix', 'type: fix', 'bugfix']
  - title: '⚡ Performance'
    labels: ['type: optimize', 'optimize']
  - title: '🔒 Security'
    labels: ['type: security', 'security']
  - title: '🧪 Tests'
    labels: ['type: test', 'test']
```

```yaml
# .github/release-drafter-zh.yml
name-template: 'v$RESOLVED_VERSION'
tag-template: 'v$RESOLVED_VERSION'
template: |
  ## 更新内容
  
  $CHANGES
  
  **完整更新日志**: https://github.com/$OWNER/$REPOSITORY/compare/$PREVIOUS_TAG...v$RESOLVED_VERSION

categories:
  - title: '🚀 新功能'
    labels: ['type: feature', 'feature']
  - title: '🐛 问题修复'
    labels: ['type: bugfix', 'type: fix', 'bugfix']
  - title: '⚡ 性能优化'
    labels: ['type: optimize', 'optimize']
  - title: '🔒 安全修复'
    labels: ['type: security', 'security']
  - title: '🧪 测试'
    labels: ['type: test', 'test']
```

### Option B: Release-Please
**优势:**
- Google维护，更活跃
- 基于conventional commits
- 自动版本管理
- 更强大的自定义能力

**配置文件:**
```yaml
# .github/workflows/release-please.yml
name: Release Please
on:
  push:
    branches: [2.x]

jobs:
  release-please:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        include:
          - language: en
            path: changes/en-us
            config: .github/release-please-en.json
          - language: zh
            path: changes/zh-cn
            config: .github/release-please-zh.json
    steps:
      - uses: google-github-actions/release-please-action@v3
        with:
          release-type: java
          package-name: seata
          changelog-path: ${{ matrix.path }}/CHANGELOG.md
          config-file: ${{ matrix.config }}
```

## 实验步骤

### Step 1: Fork & Setup
```bash
# 1. Fork seata repository to personal account
# 2. Clone and create experiment branch
git clone https://github.com/[your-username]/incubator-seata.git
cd incubator-seata
git checkout -b experiment/auto-changelog
```

### Step 2: Release-Drafter 实验
```bash
# 创建配置文件
mkdir -p .github/workflows
# 添加 release-drafter 配置
# 测试几个已有的PR
```

### Step 3: Release-Please 实验
```bash
# 在同一分支创建 release-please 配置
# 对比两种工具的输出效果
```

### Step 4: 对比测试
针对最近几个版本的PR进行回测，生成示例changelog

## 评估标准

1. **易用性**: 配置复杂度、维护成本
2. **输出质量**: 格式美观度、信息完整性
3. **多语言支持**: 双语言生成效果
4. **维护性**: 工具活跃度、社区支持
5. **集成度**: 与现有工作流兼容性

## 时间安排

- **Week 1**: 配置和初步测试
- **Week 2**: 完善配置，对比评估
- **Week 3**: 准备PR和文档

## 预期产出

1. 两种工具的完整配置
2. 生成的示例changelog对比
3. 详细的评估报告
4. 推荐方案和实施计划
5. PR ready的配置文件 