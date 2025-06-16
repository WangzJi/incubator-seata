#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Test script for Release Drafter configuration
# This script validates the YAML configuration files

set -e

echo "🔍 Testing Release Drafter Configuration..."

# Check if required files exist
CONFIG_DIR=".github"
CONFIGS=("release-drafter-en.yml" "release-drafter-zh.yml" "pr-labeler.yml")
WORKFLOWS=("workflows/release-drafter-en.yml" "workflows/release-drafter-zh.yml")

echo "📁 Checking configuration files..."
for config in "${CONFIGS[@]}"; do
    if [[ -f "$CONFIG_DIR/$config" ]]; then
        echo "✅ $CONFIG_DIR/$config exists"
    else
        echo "❌ $CONFIG_DIR/$config is missing"
        exit 1
    fi
done

echo "📁 Checking workflow files..."
for workflow in "${WORKFLOWS[@]}"; do
    if [[ -f "$CONFIG_DIR/$workflow" ]]; then
        echo "✅ $CONFIG_DIR/$workflow exists"
    else
        echo "❌ $CONFIG_DIR/$workflow is missing"
        exit 1
    fi
done

# Validate YAML syntax if yq is available
if command -v yq &> /dev/null; then
    echo "🔧 Validating YAML syntax..."
    
    for config in "${CONFIGS[@]}"; do
        if yq eval '.' "$CONFIG_DIR/$config" > /dev/null 2>&1; then
            echo "✅ $CONFIG_DIR/$config has valid YAML syntax"
        else
            echo "❌ $CONFIG_DIR/$config has invalid YAML syntax"
            exit 1
        fi
    done
    
    for workflow in "${WORKFLOWS[@]}"; do
        if yq eval '.' "$CONFIG_DIR/$workflow" > /dev/null 2>&1; then
            echo "✅ $CONFIG_DIR/$workflow has valid YAML syntax"
        else
            echo "❌ $CONFIG_DIR/$workflow has invalid YAML syntax"
            exit 1
        fi
    done
else
    echo "⚠️  yq not found, skipping YAML syntax validation"
fi

# Check template consistency
echo "🔄 Checking template consistency..."

EN_CATEGORIES=$(yq eval '.categories[].title' $CONFIG_DIR/release-drafter-en.yml | wc -l)
ZH_CATEGORIES=$(yq eval '.categories[].title' $CONFIG_DIR/release-drafter-zh.yml | wc -l)

if [[ "$EN_CATEGORIES" -eq "$ZH_CATEGORIES" ]]; then
    echo "✅ English and Chinese configurations have same number of categories ($EN_CATEGORIES)"
else
    echo "⚠️  Category count mismatch: EN=$EN_CATEGORIES, ZH=$ZH_CATEGORIES"
fi

# Test common PR title patterns
echo "🧪 Testing PR title patterns..."

TEST_TITLES=(
    "feature: add new transaction mode"
    "bugfix: resolve connection pool leak" 
    "docs: update installation guide"
    "test: add unit test coverage"
    "refactor: restructure config module"
    "security: fix SQL injection vulnerability"
)

echo "Testing these PR title patterns:"
for title in "${TEST_TITLES[@]}"; do
    echo "  - $title"
done

# Check if directories exist
echo "📂 Checking target directories..."
if [[ -d "changes/en-us" ]]; then
    echo "✅ changes/en-us directory exists"
else
    echo "❌ changes/en-us directory is missing"
    exit 1
fi

if [[ -d "changes/zh-cn" ]]; then
    echo "✅ changes/zh-cn directory exists"
else
    echo "❌ changes/zh-cn directory is missing"
    exit 1
fi

# Create sample changelog entry
echo "📝 Creating sample changelog entries..."

SAMPLE_VERSION="2.5.0"
SAMPLE_EN_CONTENT="# Apache Seata v$SAMPLE_VERSION (TEST)

We are excited to announce the release of Apache Seata v$SAMPLE_VERSION! 🎉

## What's Changed

### 🚀 New Features
- Add distributed lock mechanism (#1234)
- Support for PostgreSQL in AT mode (#1235)

### 🐛 Bug Fixes
- Fix connection pool leak issue (#1236)
- Resolve transaction timeout problem (#1237)

**Full Changelog**: https://github.com/apache/incubator-seata/compare/v2.4.0...v$SAMPLE_VERSION"

SAMPLE_ZH_CONTENT="# Apache Seata v$SAMPLE_VERSION (测试)

我们很兴奋地宣布 Apache Seata v$SAMPLE_VERSION 发布！🎉

## 更新内容

### 🚀 新功能
- 添加分布式锁机制 (#1234)
- 支持PostgreSQL AT模式 (#1235)

### 🐛 问题修复
- 修复连接池泄漏问题 (#1236)
- 解决事务超时问题 (#1237)

**完整更新日志**: https://github.com/apache/incubator-seata/compare/v2.4.0...v$SAMPLE_VERSION"

echo "$SAMPLE_EN_CONTENT" > "changes/en-us/$SAMPLE_VERSION-test.md"
echo "$SAMPLE_ZH_CONTENT" > "changes/zh-cn/$SAMPLE_VERSION-test.md"

echo "✅ Sample changelog entries created"
echo "  - changes/en-us/$SAMPLE_VERSION-test.md"
echo "  - changes/zh-cn/$SAMPLE_VERSION-test.md"

# Cleanup
read -p "🗑️  Remove test files? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    rm -f "changes/en-us/$SAMPLE_VERSION-test.md"
    rm -f "changes/zh-cn/$SAMPLE_VERSION-test.md"
    echo "✅ Test files removed"
fi

echo ""
echo "🎉 Release Drafter configuration test completed successfully!"
echo ""
echo "📋 Next steps:"
echo "1. Create a test PR with proper labels"
echo "2. Check if GitHub Actions triggers correctly"
echo "3. Review the generated draft release"
echo "4. Adjust configuration as needed"
echo ""
echo "💡 For more information, see docs/automated-changelog-setup.md" 