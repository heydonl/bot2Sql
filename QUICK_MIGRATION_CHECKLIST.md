# 快速执行清单

## 准备阶段 (5分钟)

```bash
# 1. 检查状态
cd /c/tecdo2/sql2bot
git status

# 2. 提交所有更改
git add .
git commit -m "chore: 迁移前保存状态"
git push

# 3. 创建备份分支
git checkout -b backup-before-frontend-migration
git push origin backup-before-frontend-migration
git checkout master  # 或 main
```

## 执行阶段 (15分钟)

### 步骤 1: 创建前端仓库
- [ ] 在 GitHub/GitLab 创建 `sql2bot-frontend` 仓库（Private）
- [ ] 记录仓库 URL: ___________________________

### 步骤 2: 运行迁移脚本
```bash
cd /c/tecdo2/sql2bot
migrate-frontend.bat  # Windows
# 或
./migrate-frontend.sh  # Linux/Mac
```

### 步骤 3: 推送前端代码
```bash
cd /c/tecdo2/sql2bot-frontend
git remote add origin <your-frontend-repo-url>
git branch -M main
git push -u origin main
```

### 步骤 4: 配置 Submodule
```bash
cd /c/tecdo2/sql2bot
rm -rf frontend
git add frontend
git commit -m "chore: 移除前端目录"
git submodule add <your-frontend-repo-url> frontend
git add .gitmodules frontend
git commit -m "chore: 添加前端 submodule"
git push
```

## 验证阶段 (10分钟)

```bash
# 1. 测试克隆
cd /c/temp
git clone --recurse-submodules <your-sql2bot-repo-url> test
cd test

# 2. 测试启动
./mvnw spring-boot:run  # 终端1
cd frontend && npm install && npm run dev  # 终端2

# 3. 访问测试
# http://localhost:8082 - 后端
# http://localhost:3000 - 前端

# 4. 清理
cd /c/temp && rm -rf test
```

## 完成阶段 (5分钟)

- [ ] 通知团队成员
- [ ] 更新 CI/CD 配置（如果有）
- [ ] 删除本地测试目录

## 快速命令参考

```bash
# 克隆项目（团队成员使用）
git clone --recurse-submodules <repo-url>

# 更新 submodule
git submodule update --remote --merge

# 前端开发提交
cd frontend
git add . && git commit -m "feat: xxx" && git push
cd .. && git add frontend && git commit -m "chore: 更新前端" && git push
```

## 回滚（如果需要）

```bash
git checkout backup-before-frontend-migration
git checkout -b main-restored
git push origin main-restored --force
```

---

**总耗时**: 约 35 分钟
**难度**: ⭐⭐☆☆☆

详细说明见: `MIGRATION_EXECUTION_PLAN.md`
