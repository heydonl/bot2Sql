#!/bin/bash

# SQL2Bot 前端迁移脚本
# 用途：将 frontend 目录迁移到独立仓库

set -e

echo "=========================================="
echo "SQL2Bot 前端迁移脚本"
echo "=========================================="
echo ""

# 检查当前目录
if [ ! -d "frontend" ]; then
    echo "❌ 错误：未找到 frontend 目录"
    echo "请在 sql2bot 项目根目录下运行此脚本"
    exit 1
fi

echo "✅ 找到 frontend 目录"
echo ""

# 提示用户
echo "此脚本将执行以下操作："
echo "1. 将 frontend 目录复制到 ../sql2bot-frontend"
echo "2. 在新目录中初始化 git 仓库"
echo "3. 创建初始提交"
echo ""
read -p "是否继续？(y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "已取消"
    exit 0
fi

# 步骤 1: 复制目录
echo ""
echo "步骤 1: 复制 frontend 目录..."
cd ..
if [ -d "sql2bot-frontend" ]; then
    echo "⚠️  警告：sql2bot-frontend 目录已存在"
    read -p "是否覆盖？(y/n) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "已取消"
        exit 0
    fi
    rm -rf sql2bot-frontend
fi

cp -r sql2bot/frontend sql2bot-frontend
echo "✅ 复制完成"

# 步骤 2: 初始化 git 仓库
echo ""
echo "步骤 2: 初始化 git 仓库..."
cd sql2bot-frontend
git init
echo "✅ Git 仓库已初始化"

# 步骤 3: 创建 .gitignore
echo ""
echo "步骤 3: 创建 .gitignore..."
cat > .gitignore << 'EOF'
# Dependencies
node_modules/
package-lock.json

# Build output
dist/
dist-ssr/

# Editor
.vscode/
.idea/
*.swp
*.swo
*~

# OS
.DS_Store
Thumbs.db

# Logs
*.log
npm-debug.log*
yarn-debug.log*
yarn-error.log*

# Environment
.env
.env.local
.env.*.local
EOF
echo "✅ .gitignore 已创建"

# 步骤 4: 创建初始提交
echo ""
echo "步骤 4: 创建初始提交..."
git add .
git commit -m "chore: 初始化前端项目

- Vue 3 + Vite 项目结构
- Element Plus UI 组件库
- 完整的页面组件
- API 调用封装
- 路由配置"
echo "✅ 初始提交已创建"

# 完成
echo ""
echo "=========================================="
echo "✅ 迁移完成！"
echo "=========================================="
echo ""
echo "下一步操作："
echo ""
echo "1. 在 GitHub/GitLab 创建新仓库 'sql2bot-frontend'"
echo ""
echo "2. 关联远程仓库："
echo "   cd $(pwd)"
echo "   git remote add origin <your-frontend-repo-url>"
echo "   git branch -M main"
echo "   git push -u origin main"
echo ""
echo "3. 在主项目中删除 frontend 目录并添加 submodule："
echo "   cd ../sql2bot"
echo "   rm -rf frontend"
echo "   git add frontend"
echo "   git commit -m 'chore: 移除前端目录，准备使用 submodule'"
echo "   git submodule add <your-frontend-repo-url> frontend"
echo "   git commit -m 'chore: 添加前端项目作为 submodule'"
echo "   git push"
echo ""
echo "4. 其他开发者克隆项目时："
echo "   git clone --recurse-submodules <sql2bot-repo-url>"
echo ""
echo "详细说明请参考: FRONTEND_MIGRATION_GUIDE.md"
echo ""
