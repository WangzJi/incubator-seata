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

# Changelog Translation Assistant
# 用于辅助将英文changelog翻译为中文版本

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to initialize translation mappings
init_translations() {
    # Create temporary file for translations
    TRANS_FILE=$(mktemp)
    cat > "$TRANS_FILE" << 'EOF'
# Categories
New Features|新功能
Bug Fixes|问题修复
Documentation|文档更新
Refactoring|代码重构
Testing|测试相关
Performance|性能优化
Security|安全修复

# Common phrases
Add support for|添加对...的支持
Fix issue with|修复...的问题
Fix|修复
Add|添加
Update|更新
Remove|移除
Optimize|优化
Refactor|重构
Improve|改进
Enhance|增强
Upgrade|升级

# Technical terms
database|数据库
transaction|事务
connection pool|连接池
configuration|配置
performance|性能
memory leak|内存泄漏
thread pool|线程池
serialization|序列化
deserialization|反序列化
load balancing|负载均衡
cluster|集群
distributed|分布式
synchronization|同步
asynchronous|异步
concurrent|并发
lock|锁
timeout|超时
retry|重试
failover|故障转移
rollback|回滚
commit|提交
branch|分支
global transaction|全局事务
local transaction|本地事务
resource manager|资源管理器
transaction coordinator|事务协调器
AT mode|AT模式
TCC mode|TCC模式
SAGA mode|SAGA模式
XA mode|XA模式
EOF
}

# Function to print colored output
print_color() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to show usage
show_usage() {
    cat << EOF
Changelog翻译助手脚本

使用方法:
  $0 [选项] <英文文件路径> [输出文件路径]

选项:
  -h, --help          显示此帮助信息
  -v, --verbose       详细输出模式
  -i, --interactive   交互式翻译模式
  -d, --dry-run       预览模式，不生成文件

示例:
  # 翻译英文changelog并输出到中文文件
  $0 changes/en-us/2.x.md changes/zh-cn/2.x.md
  
  # 交互式翻译模式
  $0 -i changes/en-us/2.x.md
  
  # 预览翻译结果
  $0 -d changes/en-us/2.x.md

注意:
  - 此脚本提供基础翻译辅助，建议人工审核
  - 保持PR链接和结构不变
  - 仅翻译描述文本内容
EOF
}

# Function to translate text using predefined mappings
translate_text() {
    local text="$1"
    local translated="$text"
    
    # Apply translations from file
    while IFS='|' read -r english chinese; do
        # Skip comments and empty lines
        if [[ "$english" =~ ^#.*$ ]] || [[ -z "$english" ]]; then
            continue
        fi
        # Apply translation
        translated=$(echo "$translated" | sed "s/$english/$chinese/g")
    done < "$TRANS_FILE"
    
    echo "$translated"
}

# Function to process changelog content
process_changelog() {
    local input_file="$1"
    local output_file="$2"
    local interactive="$3"
    local dry_run="$4"
    local verbose="$5"
    
    if [[ ! -f "$input_file" ]]; then
        print_color $RED "错误: 输入文件不存在: $input_file"
        exit 1
    fi
    
    print_color $BLUE "开始处理changelog文件: $input_file"
    
    # Read input file and process line by line
    local temp_file=$(mktemp)
    local line_num=0
    
    while IFS= read -r line || [[ -n "$line" ]]; do
        line_num=$((line_num + 1))
        
        # Skip certain lines (keep as-is)
        if [[ "$line" =~ ^[[:space:]]*$ ]] || \
           [[ "$line" =~ ^[[:space:]]*#.*$ ]] || \
           [[ "$line" =~ ^[[:space:]]*-.*\[\[#.*$ ]] || \
           [[ "$line" =~ ^[[:space:]]*\<!--.*$ ]] || \
           [[ "$line" =~ ^[[:space:]]*Licensed\ to.*$ ]]; then
            echo "$line" >> "$temp_file"
            continue
        fi
        
        # Translate regular text lines
        translated_line=$(translate_text "$line")
        
        if [[ "$interactive" == "true" ]] && [[ "$line" != "$translated_line" ]]; then
            print_color $YELLOW "原文: $line"
            print_color $GREEN "翻译: $translated_line"
            echo -n "是否接受此翻译? [Y/n/e(编辑)]: "
            read -r response
            case $response in
                [Nn]* )
                    echo "$line" >> "$temp_file"
                    ;;
                [Ee]* )
                    echo -n "请输入自定义翻译: "
                    read -r custom_translation
                    echo "$custom_translation" >> "$temp_file"
                    ;;
                * )
                    echo "$translated_line" >> "$temp_file"
                    ;;
            esac
        else
            echo "$translated_line" >> "$temp_file"
            if [[ "$verbose" == "true" ]] && [[ "$line" != "$translated_line" ]]; then
                print_color $YELLOW "行 $line_num: $line"
                print_color $GREEN "翻译为: $translated_line"
            fi
        fi
    done < "$input_file"
    
    # Handle output
    if [[ "$dry_run" == "true" ]]; then
        print_color $BLUE "=== 预览结果 ==="
        cat "$temp_file"
        print_color $BLUE "=== 预览结束 ==="
    else
        if [[ -n "$output_file" ]]; then
            cp "$temp_file" "$output_file"
            print_color $GREEN "翻译完成，输出文件: $output_file"
        else
            cat "$temp_file"
        fi
    fi
    
    rm -f "$temp_file"
}

# Function to show translation statistics
show_stats() {
    local input_file="$1"
    
    print_color $BLUE "=== 翻译统计 ==="
    
    local total_lines=$(wc -l < "$input_file")
    local pr_links=$(grep -c '\[\[#[0-9]*\]' "$input_file" || echo "0")
    local categories=$(grep -c '^###' "$input_file" || echo "0")
    
    echo "总行数: $total_lines"
    echo "PR链接数: $pr_links"
    echo "分类数: $categories"
    
    print_color $YELLOW "注意: 此脚本仅提供基础翻译，建议人工审核以确保准确性"
}

# Main script
main() {
    local interactive=false
    local dry_run=false
    local verbose=false
    local input_file=""
    local output_file=""
    
    # Initialize translations
    init_translations
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_usage
                exit 0
                ;;
            -v|--verbose)
                verbose=true
                shift
                ;;
            -i|--interactive)
                interactive=true
                shift
                ;;
            -d|--dry-run)
                dry_run=true
                shift
                ;;
            -*)
                print_color $RED "未知选项: $1"
                show_usage
                exit 1
                ;;
            *)
                if [[ -z "$input_file" ]]; then
                    input_file="$1"
                elif [[ -z "$output_file" ]]; then
                    output_file="$1"
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
    if [[ -z "$input_file" ]]; then
        print_color $RED "错误: 请指定输入文件"
        show_usage
        exit 1
    fi
    
    # Show translation statistics
    show_stats "$input_file"
    
    # Process the changelog
    process_changelog "$input_file" "$output_file" "$interactive" "$dry_run" "$verbose"
    
    print_color $GREEN "处理完成!"
    
    if [[ "$dry_run" == "false" ]]; then
        print_color $YELLOW "建议:"
        echo "1. 请仔细审核翻译结果"
        echo "2. 检查技术术语的准确性"
        echo "3. 确保PR链接和格式正确"
        echo "4. 可使用 'git diff' 查看变更"
    fi
    
    # Cleanup
    rm -f "$TRANS_FILE"
}

# Run main function
main "$@" 