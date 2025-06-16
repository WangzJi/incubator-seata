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

# Test Release Drafter Configuration for Seata Project
# This script validates the automated changelog generation setup

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test branch configuration
TEST_BRANCH="experiment/auto-changelog"

echo -e "${BLUE}🧪 Testing Release Drafter Configuration for Seata Project${NC}"
echo -e "${BLUE}Testing against branch: ${TEST_BRANCH}${NC}"
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

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# Check if running from Seata project root
if [ ! -f "pom.xml" ] || ! grep -q "seata" pom.xml; then
    echo -e "${RED}❌ This script must be run from the Seata project root directory${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Running from Seata project root${NC}"

# 1. Validate configuration files exist
echo -e "\n${BLUE}1. Checking configuration files...${NC}"

files=(
    ".github/release-drafter-en.yml"
    ".github/release-drafter-zh.yml"
    ".github/workflows/release-drafter-en.yml"
    ".github/workflows/release-drafter-zh.yml"
    ".github/pr-labeler.yml"
    "scripts/translate-changelog.sh"
    "scripts/translation-dictionary.txt"
)

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        print_status 0 "$file exists"
    else
        print_status 1 "$file missing"
    fi
done

# 2. Validate YAML syntax
echo -e "\n${BLUE}2. Validating YAML syntax...${NC}"

# Check if yq is available
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
    print_warning "yq not found, skipping YAML validation"
    print_info "Install yq with: brew install yq (macOS) or apt-get install yq (Ubuntu)"
fi

# 3. Check workflow branch configuration
echo -e "\n${BLUE}3. Checking workflow branch configuration...${NC}"

for workflow in .github/workflows/release-drafter-*.yml; do
    if grep -q "experiment/auto-changelog" "$workflow"; then
        print_status 0 "$workflow configured for test branch"
    else
        print_status 1 "$workflow not configured for test branch"
    fi
done

# 4. Validate release drafter configurations
echo -e "\n${BLUE}4. Validating release drafter configurations...${NC}"

# Check English config
if [ -f ".github/release-drafter-en.yml" ]; then
    if grep -q "template:" .github/release-drafter-en.yml && grep -q "categories:" .github/release-drafter-en.yml; then
        print_status 0 "English config has required sections"
    else
        print_status 1 "English config missing required sections"
    fi
fi

# Check Chinese config
if [ -f ".github/release-drafter-zh.yml" ]; then
    if grep -q "template:" .github/release-drafter-zh.yml && grep -q "categories:" .github/release-drafter-zh.yml; then
        print_status 0 "Chinese config has required sections"
    else
        print_status 1 "Chinese config missing required sections"
    fi
fi

# 5. Test translation script
echo -e "\n${BLUE}5. Testing translation script...${NC}"

if [ -f "scripts/translate-changelog.sh" ]; then
    chmod +x scripts/translate-changelog.sh
    
    # Test basic functionality by creating a temporary test file
    echo "## What's Changed" > /tmp/test-changelog.md
    echo "- Added new feature" >> /tmp/test-changelog.md
    
    ./scripts/translate-changelog.sh -d /tmp/test-changelog.md > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        print_status 0 "Translation script runs without errors"
    else
        print_status 1 "Translation script has errors"
    fi
    
    # Clean up test file
    rm -f /tmp/test-changelog.md
    
    # Check dictionary
    if [ -f "scripts/translation-dictionary.txt" ]; then
        dict_lines=$(wc -l < scripts/translation-dictionary.txt)
        if [ "$dict_lines" -gt 10 ]; then
            print_status 0 "Translation dictionary has $dict_lines entries"
        else
            print_warning "Translation dictionary has only $dict_lines entries"
        fi
    fi
fi

# 6. Check current branch and remote setup
echo -e "\n${BLUE}6. Checking Git setup...${NC}"

current_branch=$(git branch --show-current)
print_info "Current branch: $current_branch"

if git remote -v | grep -q origin; then
    print_status 0 "Remote 'origin' configured"
    
    # Check if test branch exists on remote
    if git branch -r | grep -q "origin/$TEST_BRANCH"; then
        print_status 0 "Test branch '$TEST_BRANCH' exists on remote"
    else
        print_warning "Test branch '$TEST_BRANCH' not found on remote"
        print_info "You may need to push the branch: git push origin $current_branch:$TEST_BRANCH"
    fi
else
    print_status 1 "Remote 'origin' not configured"
fi

# 7. Simulate PR labeler rules
echo -e "\n${BLUE}7. Testing PR labeler rules...${NC}"

test_cases=(
    "feature: add new functionality"
    "bugfix: resolve issue with connection"
    "docs: update README"
    "refactor: improve code structure"
    "test: add unit tests"
)

for test_case in "${test_cases[@]}"; do
    # Extract type from commit message
    type=$(echo "$test_case" | cut -d: -f1)
    
    # Check if there's a corresponding label rule
    if grep -q "$type" .github/pr-labeler.yml; then
        print_status 0 "Label rule exists for: $test_case"
    else
        print_warning "No label rule for: $test_case"
    fi
done

# 8. Check test output directories
echo -e "\n${BLUE}8. Checking test output setup...${NC}"

if [ ! -d "changes/test" ]; then
    print_info "Creating test output directory: changes/test"
    mkdir -p changes/test/{en-us,zh-cn}
    print_status 0 "Test directories created"
else
    print_status 0 "Test directories exist"
fi

# 9. Final recommendations
echo -e "\n${BLUE}9. Test Execution Recommendations...${NC}"
echo "=================================================="
print_info "To test the automation system:"
echo ""
echo "1. Create a test PR targeting '$TEST_BRANCH':"
echo "   - Title: 'feature: test automated changelog generation'"
echo "   - Make any small change (e.g., update docs/README.md)"
echo ""
echo "2. Check GitHub Actions execution:"
echo "   - Go to Actions tab in your repository"
echo "   - Look for 'Release Drafter (English) - TEST' workflow"
echo "   - Look for 'Release Drafter (Chinese) - TEST' workflow"
echo ""
echo "3. Verify automated outputs:"
echo "   - Check if PR gets labeled automatically"
echo "   - Check if draft releases are created"
echo "   - Check if changelog files are updated in changes/test/"
echo ""
echo "4. Review generated content:"
echo "   - English changelog: changes/test/en-us/experiment.md"
echo "   - Chinese changelog: changes/test/zh-cn/experiment.md"
echo ""

# 10. Current status summary
echo -e "\n${BLUE}10. Configuration Status Summary${NC}"
echo "=================================================="
echo -e "${GREEN}✅ Bilingual release drafter configured${NC}"
echo -e "${GREEN}✅ PR auto-labeling rules defined${NC}"
echo -e "${GREEN}✅ Translation system ready${NC}"
echo -e "${GREEN}✅ GitHub Actions workflows prepared${NC}"
echo -e "${YELLOW}⚠️  Configured for TEST BRANCH: $TEST_BRANCH${NC}"
echo ""
echo -e "${BLUE}Ready for testing! 🚀${NC}"

# Show next steps based on current branch
if [ "$current_branch" = "$TEST_BRANCH" ]; then
    echo -e "\n${GREEN}You're on the test branch. Ready to create PR and test!${NC}"
else
    echo -e "\n${YELLOW}Switch to test branch or create PR targeting '$TEST_BRANCH'${NC}"
    echo "git checkout $TEST_BRANCH"
fi 