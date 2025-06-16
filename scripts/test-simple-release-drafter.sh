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

# Simple Release Drafter Test for Fork Repository

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 Testing Simple Release Drafter for Fork Repository${NC}"
echo -e "${BLUE}Target: experiment/auto-changelog branch${NC}"
echo "=================================================="

# Function to print test status
print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ $2${NC}"
    else
        echo -e "${RED}❌ $2${NC}"
        exit 1
    fi
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# Check if running from correct directory
if [ ! -f "pom.xml" ] || ! grep -q "seata" pom.xml; then
    echo -e "${RED}❌ This script must be run from the Seata project root directory${NC}"
    exit 1
fi

print_status 0 "Running from Seata project root"

# 1. Check required files
echo -e "\n${BLUE}1. Checking configuration files...${NC}"

files=(
    ".github/release-drafter.yml"
    ".github/workflows/release-drafter-simple.yml"
    ".github/workflows/pr-labeler-test.yml"
    ".github/pr-labeler.yml"
)

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        print_status 0 "$file exists"
    else
        print_status 1 "$file missing"
    fi
done

# 2. Validate YAML syntax if yq is available
echo -e "\n${BLUE}2. Validating YAML syntax...${NC}"

if command -v yq &> /dev/null; then
    for file in .github/*.yml .github/workflows/*.yml; do
        if [ -f "$file" ]; then
            if yq eval '.' "$file" > /dev/null 2>&1; then
                print_status 0 "$file has valid YAML syntax"
            else
                print_status 1 "$file has invalid YAML syntax"
            fi
        fi
    done
else
    echo -e "${YELLOW}⚠️  yq not found, skipping YAML validation${NC}"
fi

# 3. Check current branch
echo -e "\n${BLUE}3. Checking Git setup...${NC}"

current_branch=$(git branch --show-current)
print_info "Current branch: $current_branch"

if [ "$current_branch" = "experiment/auto-changelog" ]; then
    print_status 0 "On the correct test branch"
else
    echo -e "${YELLOW}⚠️  Not on experiment/auto-changelog branch${NC}"
fi

# 4. Check remote configuration
if git remote -v | grep -q origin; then
    print_status 0 "Remote 'origin' configured"
    
    # Show remote info
    remote_url=$(git remote get-url origin)
    print_info "Remote URL: $remote_url"
    
    if echo "$remote_url" | grep -q "WangzJi/incubator-seata"; then
        print_status 0 "Using fork repository (WangzJi/incubator-seata)"
    fi
else
    print_status 1 "Remote 'origin' not configured"
fi

# 5. Test PR title patterns
echo -e "\n${BLUE}4. Testing PR title patterns...${NC}"

test_titles=(
    "feature: add automated changelog generation"
    "bugfix: resolve GitHub Actions issue"
    "docs: update README with testing info"
    "test: validate release drafter configuration"
)

for title in "${test_titles[@]}"; do
    type=$(echo "$title" | cut -d: -f1)
    if grep -q "$type" .github/pr-labeler.yml; then
        print_status 0 "Label rule exists for: $title"
    else
        echo -e "${YELLOW}⚠️  No specific rule for: $title${NC}"
    fi
done

# 6. Final summary
echo -e "\n${BLUE}5. Configuration Summary${NC}"
echo "=================================================="
echo -e "${GREEN}✅ Simple release drafter configured${NC}"
echo -e "${GREEN}✅ PR auto-labeling enabled${NC}"
echo -e "${GREEN}✅ Fork repository testing ready${NC}"
echo -e "${BLUE}Target branch: experiment/auto-changelog${NC}"
echo ""

echo -e "${BLUE}📋 Next Steps:${NC}"
echo "1. Create a PR targeting experiment/auto-changelog"
echo "2. Check GitHub Actions execution"
echo "3. Verify PR gets labeled automatically"  
echo "4. Check if draft release is created"
echo ""
echo -e "${GREEN}Ready for fork testing! 🚀${NC}" 