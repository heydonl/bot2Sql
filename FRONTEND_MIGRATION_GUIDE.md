# 前后端分离迁移指南

## 当前问题

前端项目 `frontend/` 直接放在 `sql2bot` 项目中，不利于：
- 独立开发和部署
- 版本控制管理
- 团队协作和权限管理

## 解决方案：使用 Git Submodule

将前端项目独立为 `sql2bot-frontend` 仓库，通过 git submodule 引入。

---

## 迁移步骤

### 第一步：创建独立的前端仓库

```bash
# 1. 在 GitHub/GitLab 上创建新仓库 sql2bot-frontend

# 2. 将当前 frontend 目录复制到新位置
cd /c/tecdo2
cp -r sql2bot/frontend sql2bot-frontend

# 3. 初始化 git 仓库
cd sql2bot-frontend
git init
git add .
git commit -m "chore: 初始化前端项目"

# 4. 关联远程仓库
git remote add origin <your-frontend-repo-url>
git branch -M main
git push -u origin main
```

### 第二步：在主项目中删除 frontend 目录

```bash
cd /c/tecdo2/sql2bot

# 1. 删除 frontend 目录
rm -rf frontend

# 2. 提交更改
git add .
git commit -m "chore: 移除前端目录，准备使用 submodule"
```

### 第三步：添加 Submodule

```bash
cd /c/tecdo2/sql2bot

# 添加前端项目作为 submodule
git submodule add <your-frontend-repo-url> frontend

# 提交 submodule 配置
git add .gitmodules frontend
git commit -m "chore: 添加前端项目作为 submodule"
git push
```

### 第四步：更新 .gitignore

确保主项目的 `.gitignore` 不会忽略 submodule：

```gitignore
# 不要添加 frontend/ 到 .gitignore
# submodule 会自动管理
```

---

## 使用 Submodule 的工作流程

### 克隆项目（包含 submodule）

```bash
# 方式 1: 克隆时同时初始化 submodule
git clone --recurse-submodules <sql2bot-repo-url>

# 方式 2: 先克隆，再初始化 submodule
git clone <sql2bot-repo-url>
cd sql2bot
git submodule init
git submodule update
```

### 更新前端代码

```bash
# 进入 frontend 目录
cd frontend

# 切换到 main 分支
git checkout main

# 拉取最新代码
git pull origin main

# 返回主项目
cd ..

# 提交 submodule 更新
git add frontend
git commit -m "chore: 更新前端 submodule"
git push
```

### 在前端项目中开发

```bash
# 进入 frontend 目录
cd frontend

# 创建功能分支
git checkout -b feature/new-feature

# 开发和提交
git add .
git commit -m "feat: 添加新功能"

# 推送到前端仓库
git push origin feature/new-feature

# 在前端仓库创建 PR，合并后
git checkout main
git pull origin main

# 返回主项目，更新 submodule 引用
cd ..
git add frontend
git commit -m "chore: 更新前端到最新版本"
git push
```

### 更新所有 submodule

```bash
# 在主项目根目录
git submodule update --remote --merge
```

---

## 项目结构（使用 Submodule 后）

```
sql2bot/                          # 主项目（后端）
├── src/
├── pom.xml
├── .gitmodules                   # submodule 配置
├── frontend/                     # submodule（指向 sql2bot-frontend）
│   ├── src/
│   ├── package.json
│   └── vite.config.js
└── README.md
```

`.gitmodules` 文件内容：
```ini
[submodule "frontend"]
    path = frontend
    url = <your-frontend-repo-url>
    branch = main
```

---

## 部署配置

### 开发环境

保持不变，前端和后端分别启动：

```bash
# 终端 1: 启动后端
cd sql2bot
./mvnw spring-boot:run

# 终端 2: 启动前端
cd sql2bot/frontend
npm run dev
```

### 生产环境

#### 方案 1: 分离部署（推荐）

**后端**:
```bash
cd sql2bot
./mvnw clean package
java -jar target/sql2bot-*.jar
```

**前端**:
```bash
cd sql2bot-frontend
npm run build
# 将 dist/ 目录部署到 Nginx/CDN
```

#### 方案 2: 集成部署

在后端项目中添加 Maven 插件，自动构建前端：

```xml
<!-- pom.xml -->
<build>
    <plugins>
        <!-- Frontend Maven Plugin -->
        <plugin>
            <groupId>com.github.eirslett</groupId>
            <artifactId>frontend-maven-plugin</artifactId>
            <version>1.12.1</version>
            <configuration>
                <workingDirectory>frontend</workingDirectory>
            </configuration>
            <executions>
                <!-- 安装 Node 和 npm -->
                <execution>
                    <id>install node and npm</id>
                    <goals>
                        <goal>install-node-and-npm</goal>
                    </goals>
                    <configuration>
                        <nodeVersion>v18.17.0</nodeVersion>
                    </configuration>
                </execution>
                <!-- 安装依赖 -->
                <execution>
                    <id>npm install</id>
                    <goals>
                        <goal>npm</goal>
                    </goals>
                    <configuration>
                        <arguments>install</arguments>
                    </configuration>
                </execution>
                <!-- 构建前端 -->
                <execution>
                    <id>npm run build</id>
                    <goals>
                        <goal>npm</goal>
                    </goals>
                    <configuration>
                        <arguments>run build</arguments>
                    </configuration>
                </execution>
            </executions>
        </plugin>

        <!-- 复制前端构建产物到 static 目录 -->
        <plugin>
            <artifactId>maven-resources-plugin</artifactId>
            <executions>
                <execution>
                    <id>copy-frontend</id>
                    <phase>prepare-package</phase>
                    <goals>
                        <goal>copy-resources</goal>
                    </goals>
                    <configuration>
                        <outputDirectory>${project.build.outputDirectory}/static</outputDirectory>
                        <resources>
                            <resource>
                                <directory>frontend/dist</directory>
                            </resource>
                        </resources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## CI/CD 配置

### GitHub Actions 示例

**后端 CI (.github/workflows/backend.yml)**:
```yaml
name: Backend CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
        with:
          submodules: recursive  # 重要：拉取 submodule

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build with Maven
        run: ./mvnw clean package

      - name: Run tests
        run: ./mvnw test
```

**前端 CI (sql2bot-frontend/.github/workflows/frontend.yml)**:
```yaml
name: Frontend CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'

      - name: Install dependencies
        run: npm install

      - name: Build
        run: npm run build

      - name: Lint
        run: npm run lint
```

---

## 优势

### 使用 Submodule 的优势

1. **独立版本控制**
   - 前端和后端有各自的 commit 历史
   - 可以独立打 tag 和发布版本

2. **团队协作**
   - 前端团队和后端团队可以独立工作
   - 可以设置不同的仓库权限

3. **灵活部署**
   - 可以分离部署
   - 也可以集成部署

4. **代码复用**
   - 前端项目可以被其他项目引用
   - 便于维护多个环境（开发、测试、生产）

### 注意事项

1. **Submodule 更新**
   - 团队成员需要记得更新 submodule
   - 使用 `git submodule update --remote` 获取最新代码

2. **提交顺序**
   - 先提交前端更改到 sql2bot-frontend
   - 再在主项目中更新 submodule 引用

3. **分支管理**
   - 确保 submodule 指向正确的分支
   - 在 .gitmodules 中指定 branch

---

## 替代方案

如果不想使用 submodule，还有其他方案：

### 方案 2: Monorepo（使用 Lerna/Nx）

将前后端放在同一个仓库，但使用工具管理：

```
sql2bot-monorepo/
├── packages/
│   ├── backend/
│   └── frontend/
├── lerna.json
└── package.json
```

### 方案 3: 完全独立

前后端完全独立，不使用 submodule：

```
sql2bot/          # 后端仓库
sql2bot-frontend/ # 前端仓库（完全独立）
```

部署时分别构建和部署。

---

## 推荐

**推荐使用 Git Submodule 方案**，因为：
- 保持了前后端的独立性
- 主项目仍然可以包含前端代码（通过 submodule）
- 便于开发和部署
- 符合标准的前后端分离实践

---

## 快速迁移脚本

```bash
#!/bin/bash

# 迁移脚本
echo "开始迁移前端项目..."

# 1. 复制前端到新位置
cd /c/tecdo2
cp -r sql2bot/frontend sql2bot-frontend
cd sql2bot-frontend

# 2. 初始化前端仓库
git init
git add .
git commit -m "chore: 初始化前端项目"

echo "前端仓库已初始化"
echo "请手动执行以下步骤："
echo "1. 在 GitHub/GitLab 创建 sql2bot-frontend 仓库"
echo "2. 执行: git remote add origin <your-frontend-repo-url>"
echo "3. 执行: git push -u origin main"
echo ""
echo "然后在主项目中："
echo "cd /c/tecdo2/sql2bot"
echo "rm -rf frontend"
echo "git submodule add <your-frontend-repo-url> frontend"
echo "git commit -m 'chore: 添加前端 submodule'"
```

---

## 总结

使用 Git Submodule 可以：
- ✅ 保持前后端独立
- ✅ 便于团队协作
- ✅ 灵活的部署选项
- ✅ 清晰的版本管理

按照上述步骤操作即可完成迁移。
