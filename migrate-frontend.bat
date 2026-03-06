@echo off
REM SQL2Bot 前端迁移脚本 (Windows)
REM 用途：将 frontend 目录迁移到独立仓库

echo ==========================================
echo SQL2Bot 前端迁移脚本 (Windows)
echo ==========================================
echo.

REM 检查当前目录
if not exist "frontend" (
    echo [错误] 未找到 frontend 目录
    echo 请在 sql2bot 项目根目录下运行此脚本
    pause
    exit /b 1
)

echo [成功] 找到 frontend 目录
echo.

echo 此脚本将执行以下操作：
echo 1. 将 frontend 目录复制到 ..\sql2bot-frontend
echo 2. 在新目录中初始化 git 仓库
echo 3. 创建初始提交
echo.
set /p confirm="是否继续？(Y/N): "
if /i not "%confirm%"=="Y" (
    echo 已取消
    pause
    exit /b 0
)

REM 步骤 1: 复制目录
echo.
echo 步骤 1: 复制 frontend 目录...
cd ..
if exist "sql2bot-frontend" (
    echo [警告] sql2bot-frontend 目录已存在
    set /p overwrite="是否覆盖？(Y/N): "
    if /i not "!overwrite!"=="Y" (
        echo 已取消
        pause
        exit /b 0
    )
    rmdir /s /q sql2bot-frontend
)

xcopy /E /I /Y sql2bot\frontend sql2bot-frontend
echo [成功] 复制完成

REM 步骤 2: 初始化 git 仓库
echo.
echo 步骤 2: 初始化 git 仓库...
cd sql2bot-frontend
git init
echo [成功] Git 仓库已初始化

REM 步骤 3: 创建 .gitignore
echo.
echo 步骤 3: 创建 .gitignore...
(
echo # Dependencies
echo node_modules/
echo package-lock.json
echo.
echo # Build output
echo dist/
echo dist-ssr/
echo.
echo # Editor
echo .vscode/
echo .idea/
echo *.swp
echo *.swo
echo *~
echo.
echo # OS
echo .DS_Store
echo Thumbs.db
echo.
echo # Logs
echo *.log
echo npm-debug.log*
echo yarn-debug.log*
echo yarn-error.log*
echo.
echo # Environment
echo .env
echo .env.local
echo .env.*.local
) > .gitignore
echo [成功] .gitignore 已创建

REM 步骤 4: 创建初始提交
echo.
echo 步骤 4: 创建初始提交...
git add .
git commit -m "chore: 初始化前端项目"
echo [成功] 初始提交已创建

REM 完成
echo.
echo ==========================================
echo [成功] 迁移完成！
echo ==========================================
echo.
echo 下一步操作：
echo.
echo 1. 在 GitHub/GitLab 创建新仓库 'sql2bot-frontend'
echo.
echo 2. 关联远程仓库：
echo    cd %CD%
echo    git remote add origin ^<your-frontend-repo-url^>
echo    git branch -M main
echo    git push -u origin main
echo.
echo 3. 在主项目中删除 frontend 目录并添加 submodule：
echo    cd ..\sql2bot
echo    rmdir /s /q frontend
echo    git add frontend
echo    git commit -m "chore: 移除前端目录，准备使用 submodule"
echo    git submodule add ^<your-frontend-repo-url^> frontend
echo    git commit -m "chore: 添加前端项目作为 submodule"
echo    git push
echo.
echo 4. 其他开发者克隆项目时：
echo    git clone --recurse-submodules ^<sql2bot-repo-url^>
echo.
echo 详细说明请参考: FRONTEND_MIGRATION_GUIDE.md
echo.
pause
