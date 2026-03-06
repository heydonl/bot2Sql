# SQL2Bot 生产环境迁移执行计划

## 项目信息
- **项目类型**: 生产项目
- **迁移方式**: Git Submodule
- **执行日期**: 2026-03-06

---

## 迁移前检查清单

### 1. 代码状态检查
```bash
cd /c/tecdo2/sql2bot

# 检查是否有未提交的更改
git status

# 如果有未提交的更改，先提交
git add .
git commit -m "chore: 迁移前保存当前状态"
git push
```

### 2. 创建备份分支（安全措施）
```bash
# 创建备份分支
git checkout -b backup-before-frontend-migration
git push origin backup-before-frontend-migration

# 返回主分支
git checkout master  # 或 main
```

### 3. 确认环境
- [ ] Git 已安装且配置正确
- [ ] 有权限创建新仓库（GitHub/GitLab）
- [ ] 团队成员已知晓迁移计划
- [ ] 已阅读迁移文档

---

## 迁移步骤（详细版）

### 阶段 1: 创建独立的前端仓库

#### 步骤 1.1: 在 GitHub/GitLab 创建新仓库

**GitHub**:
1. 访问 https://github.com/new
2. 仓库名: `sql2bot-frontend`
3. 描述: `SQL2Bot 前端项目 - Vue 3 + Vite + Element Plus`
4. 可见性: Private（生产项目建议私有）
5. 不要初始化 README、.gitignore 或 LICENSE
6. 点击 "Create repository"

**GitLab**:
1. 访问 GitLab 并点击 "New project"
2. 选择 "Create blank project"
3. 项目名: `sql2bot-frontend`
4. 可见性: Private
5. 不要初始化 README
6. 点击 "Create project"

记录仓库 URL:
```
前端仓库 URL: ________________________________
```

#### 步骤 1.2: 运行迁移脚本

**Windows**:
```cmd
cd C:\tecdo2\sql2bot
migrate-frontend.bat
```

**Linux/Mac/Git Bash**:
```bash
cd /c/tecdo2/sql2bot
chmod +x migrate-frontend.sh
./migrate-frontend.sh
```

脚本会自动：
- ✅ 复制 frontend 到 ../sql2bot-frontend
- ✅ 初始化 git 仓库
- ✅ 创建 .gitignore
- ✅ 创建初始提交

#### 步骤 1.3: 关联远程仓库并推送

```bash
cd /c/tecdo2/sql2bot-frontend

# 关联远程仓库（替换为你的实际 URL）
git remote add origin <your-frontend-repo-url>

# 推送到远程
git branch -M main
git push -u origin main
```

验证推送成功：
- [ ] 访问前端仓库 URL，确认代码已上传
- [ ] 检查文件结构是否完整

---

### 阶段 2: 在主项目中配置 Submodule

#### 步骤 2.1: 删除本地 frontend 目录

```bash
cd /c/tecdo2/sql2bot

# 删除 frontend 目录
rm -rf frontend

# 提交删除操作
git add frontend
git commit -m "chore: 移除前端目录，准备使用 submodule"
```

#### 步骤 2.2: 添加 Submodule

```bash
# 添加前端项目作为 submodule（替换为你的实际 URL）
git submodule add <your-frontend-repo-url> frontend

# 查看 submodule 状态
git submodule status

# 提交 submodule 配置
git add .gitmodules frontend
git commit -m "chore: 添加前端项目作为 submodule"
```

#### 步骤 2.3: 推送到远程

```bash
git push origin master  # 或 main
```

验证：
- [ ] 检查 .gitmodules 文件已创建
- [ ] frontend 目录存在且包含代码
- [ ] 远程仓库已更新

---

### 阶段 3: 验证迁移结果

#### 步骤 3.1: 在新位置克隆项目

```bash
# 在另一个目录测试克隆
cd /c/temp
git clone --recurse-submodules <your-sql2bot-repo-url> sql2bot-test
cd sql2bot-test

# 检查 frontend 目录
ls -la frontend/
```

#### 步骤 3.2: 测试启动

**后端**:
```bash
cd /c/temp/sql2bot-test
./mvnw spring-boot:run
```

**前端**:
```bash
cd /c/temp/sql2bot-test/frontend
npm install
npm run dev
```

验证：
- [ ] 后端正常启动（http://localhost:8082）
- [ ] 前端正常启动（http://localhost:3000）
- [ ] 前端可以正常调用后端 API
- [ ] 所有功能正常工作

#### 步骤 3.3: 清理测试目录

```bash
cd /c/temp
rm -rf sql2bot-test
```

---

### 阶段 4: 团队协作配置

#### 步骤 4.1: 更新团队文档

在主项目 README.md 中添加克隆说明（已完成）：
```bash
# 克隆项目（包含 submodule）
git clone --recurse-submodules <repo-url>
```

#### 步骤 4.2: 通知团队成员

发送通知邮件/消息，包含以下内容：

```
主题: SQL2Bot 前端项目已迁移到独立仓库

团队成员你好，

SQL2Bot 项目的前端代码已迁移到独立仓库，使用 Git Submodule 方式管理。

【重要变更】
- 前端仓库: <frontend-repo-url>
- 主项目通过 submodule 引用前端

【如何更新本地代码】
1. 拉取最新代码:
   git pull origin main

2. 初始化 submodule:
   git submodule init
   git submodule update

3. 或者重新克隆:
   git clone --recurse-submodules <repo-url>

【开发流程】
- 前端开发: 在 frontend/ 目录中正常开发，提交到前端仓库
- 后端开发: 在主项目中开发，提交到主仓库
- 更新前端版本: 在主项目中 git add frontend && git commit

【文档】
- 详细说明: FRONTEND_MIGRATION_GUIDE.md
- 快速参考: FRONTEND_SEPARATION.md

如有问题，请联系项目负责人。
```

#### 步骤 4.3: 更新 CI/CD 配置

如果使用 CI/CD，需要更新配置：

**GitHub Actions** (.github/workflows/ci.yml):
```yaml
- uses: actions/checkout@v3
  with:
    submodules: recursive  # 添加这一行
```

**GitLab CI** (.gitlab-ci.yml):
```yaml
variables:
  GIT_SUBMODULE_STRATEGY: recursive  # 添加这一行
```

---

## 回滚计划（如果出现问题）

### 方案 1: 恢复到备份分支

```bash
cd /c/tecdo2/sql2bot
git checkout backup-before-frontend-migration
git checkout -b main-restored
git push origin main-restored --force
```

### 方案 2: 移除 Submodule，恢复原目录

```bash
# 移除 submodule
git submodule deinit -f frontend
git rm -f frontend
rm -rf .git/modules/frontend

# 恢复原 frontend 目录
git checkout backup-before-frontend-migration -- frontend

# 提交
git add frontend
git commit -m "chore: 回滚前端 submodule，恢复原目录结构"
git push
```

---

## 迁移后的工作流程

### 前端开发流程

```bash
# 1. 进入前端目录
cd frontend

# 2. 创建功能分支
git checkout -b feature/new-feature

# 3. 开发和提交
git add .
git commit -m "feat: 添加新功能"

# 4. 推送到前端仓库
git push origin feature/new-feature

# 5. 在前端仓库创建 PR，合并后更新本地
git checkout main
git pull origin main

# 6. 返回主项目，更新 submodule 引用
cd ..
git add frontend
git commit -m "chore: 更新前端到最新版本"
git push
```

### 后端开发流程

```bash
# 正常的 git 工作流程，不受影响
git checkout -b feature/backend-feature
# 开发...
git add .
git commit -m "feat: 后端新功能"
git push origin feature/backend-feature
```

### 更新前端到最新版本

```bash
# 在主项目根目录
git submodule update --remote --merge
git add frontend
git commit -m "chore: 更新前端 submodule"
git push
```

---

## 部署配置

### 开发环境（不变）

```bash
# 终端 1: 后端
cd /c/tecdo2/sql2bot
./mvnw spring-boot:run

# 终端 2: 前端
cd /c/tecdo2/sql2bot/frontend
npm run dev
```

### 生产环境

#### 方案 A: 分离部署（推荐）

**后端部署**:
```bash
cd /c/tecdo2/sql2bot
./mvnw clean package
java -jar target/sql2bot-*.jar
```

**前端部署**:
```bash
cd /c/tecdo2/sql2bot-frontend
npm run build
# 将 dist/ 部署到 Nginx/CDN
```

#### 方案 B: 集成部署

使用 Maven Frontend Plugin（配置已在 FRONTEND_MIGRATION_GUIDE.md 中）

---

## 检查清单

### 迁移完成检查
- [ ] 前端仓库已创建并推送成功
- [ ] 主项目已配置 submodule
- [ ] 本地测试通过
- [ ] 新克隆测试通过
- [ ] 团队成员已通知
- [ ] CI/CD 已更新
- [ ] 文档已更新

### 后续监控
- [ ] 第一周：关注团队成员是否遇到问题
- [ ] 第一个月：确认 CI/CD 正常运行
- [ ] 持续：收集反馈，优化流程

---

## 常见问题

### Q1: 团队成员拉取代码后 frontend 目录为空？
```bash
git submodule init
git submodule update
```

### Q2: 如何更新 submodule 到最新版本？
```bash
git submodule update --remote --merge
```

### Q3: 前端修改后如何提交？
```bash
cd frontend
git add .
git commit -m "feat: xxx"
git push origin main
cd ..
git add frontend
git commit -m "chore: 更新前端"
git push
```

### Q4: 如何切换前端的分支？
```bash
cd frontend
git checkout develop
cd ..
git add frontend
git commit -m "chore: 切换前端到 develop 分支"
```

---

## 联系支持

如遇到问题：
1. 查看 FRONTEND_MIGRATION_GUIDE.md
2. 查看 Git Submodule 官方文档
3. 联系项目负责人

---

**执行时间估计**: 30-60 分钟
**风险等级**: 低（有完整的回滚方案）
**建议执行时间**: 非高峰期，确保有时间处理可能的问题

---

**准备好了吗？按照上述步骤开始迁移！** 🚀
