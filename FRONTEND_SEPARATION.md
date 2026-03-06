# 前后端分离架构说明

## 当前状态

前端代码目前位于 `sql2bot/frontend/` 目录中。

## 推荐架构

将前端独立为 `sql2bot-frontend` 仓库，通过 **Git Submodule** 方式引入主项目。

### 优势

1. **独立版本控制** - 前后端各自管理版本和发布
2. **团队协作** - 前端和后端团队可以独立工作
3. **灵活部署** - 支持分离部署或集成部署
4. **代码复用** - 前端可被其他项目引用
5. **清晰边界** - 明确的项目边界和职责划分

## 快速迁移

### 自动迁移（推荐）

**Linux/Mac**:
```bash
chmod +x migrate-frontend.sh
./migrate-frontend.sh
```

**Windows**:
```cmd
migrate-frontend.bat
```

### 手动迁移

详见：[前端迁移指南](./FRONTEND_MIGRATION_GUIDE.md)

## 迁移后的项目结构

```
sql2bot/                    # 后端仓库
├── src/
├── pom.xml
├── .gitmodules            # submodule 配置
└── frontend/              # submodule -> sql2bot-frontend

sql2bot-frontend/          # 前端仓库（独立）
├── src/
├── package.json
└── vite.config.js
```

## 使用 Submodule

### 克隆项目
```bash
# 包含 submodule
git clone --recurse-submodules <repo-url>

# 或者先克隆再初始化
git clone <repo-url>
cd sql2bot
git submodule init
git submodule update
```

### 更新前端
```bash
cd frontend
git pull origin main
cd ..
git add frontend
git commit -m "chore: 更新前端"
```

### 开发流程不变
```bash
# 后端
./mvnw spring-boot:run

# 前端
cd frontend
npm run dev
```

## 文档

- [详细迁移指南](./FRONTEND_MIGRATION_GUIDE.md) - 完整的迁移步骤和说明
- [README.md](./README.md) - 项目主文档
- [.gitmodules.example](./.gitmodules.example) - Submodule 配置示例

## 注意事项

1. 迁移前请确保代码已提交
2. 建议先在测试分支上操作
3. 团队成员需要了解 submodule 的使用方式
4. CI/CD 配置需要添加 `--recurse-submodules` 参数

## 何时迁移

建议在以下情况下进行迁移：
- ✅ 项目进入稳定开发阶段
- ✅ 需要前后端独立部署
- ✅ 团队规模扩大，需要分工协作
- ✅ 前端需要被其他项目复用

## 不迁移的情况

如果满足以下条件，可以暂时不迁移：
- 项目处于快速原型阶段
- 团队规模很小（1-2人）
- 前后端紧密耦合，不需要独立部署
- 不需要版本独立管理

---

**推荐**: 对于生产环境项目，建议使用 Git Submodule 方式管理前端代码。
