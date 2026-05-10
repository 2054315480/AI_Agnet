@echo off
chcp 65001 >nul
echo ========================================
echo   AI 智能客服系统 - 开发环境启动脚本
echo   （推荐使用 Docker Compose: docker-start.bat）
echo ========================================
echo.

:: 检测是否安装了 Docker
where docker >nul 2>&1
if %errorlevel%==0 (
    echo [提示] 检测到 Docker，推荐使用 docker-start.bat 一键部署
    echo        继续使用本地开发模式启动...
    echo.
)

:: ========== 前置检查 ==========
echo [1/4] 检查环境...
where java >nul 2>&1 || (echo [ERROR] 未找到 Java，请安装 JDK 21+ && pause && exit /b 1)
where node >nul 2>&1 || (echo [ERROR] 未找到 Node.js，请安装 Node.js 18+ && pause && exit /b 1)
where mvn >nul 2>&1 || (echo [ERROR] 未找到 Maven，请安装 Maven 3.8+ && pause && exit /b 1)

echo        Java: 已安装
echo        Node: 已安装
echo        Maven: 已安装

:: ========== 后端启动 ==========
echo.
echo [2/4] 编译并启动后端服务 (端口 8123)...
start "AI-Agent-Backend" cmd /c "cd /d "%~dp0.." && mvn spring-boot:run -Dmaven.test.skip=true"
echo       后端正在启动中，请等待约 20 秒...

:: 等待后端就绪
timeout /t 25 /nobreak >nul

:: ========== 前端启动 ==========
echo.
echo [3/4] 安装前端依赖并启动前端 (端口 5173)...
start "AI-Agent-Frontend" cmd /c "cd /d "%~dp0..\AI_Agent_Fronted" && npm install && npm run dev"
echo       前端正在启动中，请等待约 10 秒...

:: ========== 完成 ==========
echo.
echo [4/4] 启动完成！
echo.
echo ========================================
echo   后端 API:  http://localhost:8123/api
echo   接口文档:  http://localhost:8123/api/doc.html
echo   前端界面:  http://localhost:5173
echo ========================================
echo.
echo 按任意键打开前端页面...
pause >nul
start http://localhost:5173
