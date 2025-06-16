#!/bin/bash

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

# Release Version Changelog Generator
# 用于版本发布时创建具体版本的changelog文件

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_color() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to show usage
show_usage() {
    cat << EOF
版本发布Changelog生成器

使用方法:
  $0 <版本号> [选项]

参数:
  版本号               发布的版本号，如: 2.5.0

选项:
  -h, --help          显示此帮助信息
  -d, --dry-run       预览模式，不创建文件
  -f, --force         强制覆盖已存在的文件

示例:
  # 为2.5.0版本生成changelog
  $0 2.5.0
  
  # 预览生成结果
  $0 2.5.0 --dry-run
  
  # 强制覆盖已存在的文件
  $0 2.5.0 --force

说明:
  - 此脚本会读取 changes/{en-us,zh-cn}/2.x.md
  - 生成对应版本的 changes/{en-us,zh-cn}/{version}.md
  - 清空 2.x.md 为下一个开发周期准备
EOF
}

# Function to create version changelog
create_version_changelog() {
    local version="$1"
    local dry_run="$2"
    local force="$3"
    
    local en_source="changes/en-us/2.x.md"
    local zh_source="changes/zh-cn/2.x.md"
    local en_target="changes/en-us/${version}.md"
    local zh_target="changes/zh-cn/${version}.md"
    
    # Check if source files exist
    if [[ ! -f "$en_source" ]]; then
        print_color $RED "错误: 英文源文件不存在: $en_source"
        exit 1
    fi
    
    if [[ ! -f "$zh_source" ]]; then
        print_color $RED "错误: 中文源文件不存在: $zh_source"
        exit 1
    fi
    
    # Check if target files already exist
    if [[ "$force" != "true" ]]; then
        if [[ -f "$en_target" ]]; then
            print_color $RED "错误: 英文目标文件已存在: $en_target"
            print_color $YELLOW "使用 --force 选项覆盖现有文件"
            exit 1
        fi
        
        if [[ -f "$zh_target" ]]; then
            print_color $RED "错误: 中文目标文件已存在: $zh_target"
            print_color $YELLOW "使用 --force 选项覆盖现有文件"
            exit 1
        fi
    fi
    
    if [[ "$dry_run" == "true" ]]; then
        print_color $BLUE "=== 预览模式 ==="
        print_color $YELLOW "将要创建的文件:"
        echo "  - $en_target"
        echo "  - $zh_target"
        print_color $YELLOW "将要重置的文件:"
        echo "  - $en_source"
        echo "  - $zh_source"
        return 0
    fi
    
    print_color $BLUE "开始生成版本 $version 的changelog..."
    
    # Process English changelog
    print_color $YELLOW "处理英文changelog..."
    sed "s/Add changes here for all PR submitted to the 2.x branch./All changes for Apache Seata $version release./g" "$en_source" > "$en_target"
    
    # Process Chinese changelog
    print_color $YELLOW "处理中文changelog..."
    sed "s/所有提交到 2.x 分支的 PR 请在此处登记。/Apache Seata $version 版本的所有变更。/g" "$zh_source" > "$zh_target"
    
    print_color $GREEN "✅ 版本changelog创建完成:"
    echo "  - $en_target"
    echo "  - $zh_target"
    
    # Reset 2.x.md files for next development cycle
    print_color $YELLOW "重置2.x.md文件为下一个开发周期..."
    
    # Reset English 2.x.md
    cat > "$en_source" << 'EOF'
<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->
Add changes here for all PR submitted to the 2.x branch.

<!-- Please add the `changes` to the following location(feature/bugfix/optimize/test) based on the type of PR -->

### feature:


### bugfix:


### optimize:


### security:


### test:


### refactor:


### doc:


Thanks to these contributors for their code commits. Please report an unintended omission.

<!-- Please make sure your Github ID is in the list below -->



Also, we receive many valuable issues, questions and advices from our community. Thanks for you all.
EOF

    # Reset Chinese 2.x.md
    cat > "$zh_source" << 'EOF'
<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->
所有提交到 2.x 分支的 PR 请在此处登记。

<!-- 请根据PR的类型添加 `变更记录` 到以下对应位置(feature/bugfix/optimize/test) 下 -->

### feature:


### bugfix:


### optimize:


### security:


### test:


### refactor:


### doc:


非常感谢以下 contributors 的代码贡献。若有无意遗漏，请报告。

<!-- 请确保您的 GitHub ID 在以下列表中 -->



同时，我们收到了社区反馈的很多有价值的issue和建议，非常感谢大家。
EOF

    print_color $GREEN "✅ 2.x.md文件已重置为下一个开发周期"
    
    print_color $BLUE "版本发布changelog生成完成！"
    print_color $YELLOW "后续步骤:"
    echo "1. 检查生成的changelog文件"
    echo "2. 提交变更到git仓库"
    echo "3. 创建版本发布标签"
    echo "4. 发布新版本"
}

# Function to validate version format
validate_version() {
    local version="$1"
    
    if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        print_color $RED "错误: 版本号格式不正确: $version"
        print_color $YELLOW "请使用格式: X.Y.Z (如: 2.5.0)"
        exit 1
    fi
}

# Main script
main() {
    local version=""
    local dry_run=false
    local force=false
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_usage
                exit 0
                ;;
            -d|--dry-run)
                dry_run=true
                shift
                ;;
            -f|--force)
                force=true
                shift
                ;;
            -*)
                print_color $RED "未知选项: $1"
                show_usage
                exit 1
                ;;
            *)
                if [[ -z "$version" ]]; then
                    version="$1"
                else
                    print_color $RED "错误: 过多的参数"
                    show_usage
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # Validate arguments
    if [[ -z "$version" ]]; then
        print_color $RED "错误: 请指定版本号"
        show_usage
        exit 1
    fi
    
    # Validate version format
    validate_version "$version"
    
    # Create version changelog
    create_version_changelog "$version" "$dry_run" "$force"
}

# Run main function
main "$@" 