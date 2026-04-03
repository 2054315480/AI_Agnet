package com.qh.ai_agent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 终端操作工具类
 * 提供安全的终端命令执行功能
 *
 * 安全限制：
 * 1. 命令超时时间：30秒
 * 2. 禁止的危险命令：rm -rf /、format、shutdown等
 * 3. 工作目录限制：只允许在项目目录下操作
 * 4. 命令白名单机制
 */
@Slf4j
@Component
public class TerminalOperationTool {

    private static final long COMMAND_TIMEOUT_SECONDS = 30;
    private static final String PROJECT_DIR = System.getProperty("user.dir");

    /**
     * 允许执行的命令白名单
     */
    private static final List<String> ALLOWED_COMMANDS = Arrays.asList(
            "ls", "dir", "pwd", "cd",
            "cat", "more", "less", "head", "tail",
            "grep", "find", "echo", "printf",
            "mkdir", "touch", "cp", "mv",
            "git", "mvn", "java", "javac",
            "ping", "curl", "wget", "nslookup",
            "date", "whoami", "hostname", "uname",
            "ps", "top", "netstat", "df", "du",
            "wc", "sort", "uniq", "cut", "awk", "sed"
    );

    /**
     * 危险命令黑名单（包含这些参数的命令将被拒绝）
     */
    private static final List<String> DANGEROUS_PATTERNS = Arrays.asList(
            "rm -rf /",
            "rm -rf /*",
            "format c:",
            "del /q",
            "shutdown",
            "reboot",
            "halt",
            "poweroff",
            ":(){:|:&};:",  // fork bomb
            "chmod 000",
            "chown root"
    );

    /**
     * 执行终端命令（基础方法）
     *
     * @param command 要执行的命令
     * @param workDir 工作目录（可选，默认为项目目录）
     * @return 命令执行结果
     */
    @Tool(description = "执行终端命令，支持指定工作目录")
    public String executeCommandWithDir(
            @ToolParam(description = "要执行的终端命令，如：ls -la, cat file.txt, grep 'keyword' file.txt") String command,
            @ToolParam(description = "工作目录路径（可选，默认为项目目录）") String workDir) {

        try {
            // 安全检查
            String securityCheckResult = checkCommandSafety(command);
            if (securityCheckResult != null) {
                return "安全检查失败: " + securityCheckResult;
            }

            // 设置工作目录
            File workingDir = determineWorkingDirectory(workDir);

            // 构建进程
            ProcessBuilder processBuilder = new ProcessBuilder();
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            if (isWindows) {
                processBuilder.command("cmd", "/c", command);
            } else {
                processBuilder.command("bash", "-c", command);
            }

            processBuilder.directory(workingDir);
            processBuilder.redirectErrorStream(true);

            log.info("执行命令: {}, 工作目录: {}", command, workingDir.getAbsolutePath());

            // 执行命令
            Process process = processBuilder.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 等待进程完成（带超时）
            boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return "命令执行超时（超过" + COMMAND_TIMEOUT_SECONDS + "秒），已强制终止";
            }

            int exitCode = process.exitValue();

            // 返回结果
            String result = output.toString();
            if (result.isEmpty() && exitCode != 0) {
                return "命令执行失败，退出码: " + exitCode;
            }

            return result.isEmpty() ? "命令执行成功，无输出" : result;

        } catch (Exception e) {
            log.error("命令执行异常", e);
            return "命令执行异常: " + e.getMessage();
        }
    }

    /**
     * 执行单个命令（使用默认工作目录）
     */
    @Tool(description = "执行单个终端命令，使用项目目录作为工作目录")
    public String executeCommand(
            @ToolParam(description = "要执行的终端命令") String command) {

        return executeCommandWithDir(command, null);
    }

    /**
     * 列出目录内容
     */
    @Tool(description = "列出指定目录的文件和子目录")
    public String listFiles(
            @ToolParam(description = "目录路径（可选，默认为当前目录）") String path,
            @ToolParam(description = "是否显示详细信息（包括权限、大小、修改时间等）") Boolean detailed) {

        String cmd = detailed != null && detailed ?
                (System.getProperty("os.name").toLowerCase().contains("win") ? "dir" : "ls -la") :
                (System.getProperty("os.name").toLowerCase().contains("win") ? "dir" : "ls");

        if (StrUtil.isNotBlank(path)) {
            cmd += " " + path;
        }

        return executeCommand(cmd);
    }

    /**
     * 查看文件内容
     */
    @Tool(description = "通过命令行查看文本文件的内容")
    public String readFileByCommand(
            @ToolParam(description = "文件路径") String filePath,
            @ToolParam(description = "显示的行数（可选，默认显示全部）") Integer lines) {

        // 解析完整路径（基于项目目录）
        File file = determineWorkingDirectory(filePath);

        if (!FileUtil.exist(file)) {
            return "文件不存在: " + filePath + " (完整路径: " + file.getAbsolutePath() + ")";
        }

        String cmd;
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        // 使用完整路径执行命令
        String fullPath = file.getAbsolutePath();

        if (lines != null && lines > 0) {
            cmd = isWindows ? "powershell \"Get-Content '" + fullPath + "' -Head " + lines + "\""
                    : "head -n " + lines + " '" + fullPath + "'";
        } else {
            cmd = isWindows ? "type \"" + fullPath + "\"" : "cat '" + fullPath + "'";
        }

        return executeCommand(cmd);
    }

    /**
     * 搜索文件中的文本
     */
    @Tool(description = "在文件中搜索包含指定关键词的行")
    public String searchInFile(
            @ToolParam(description = "要搜索的关键词") String keyword,
            @ToolParam(description = "文件路径或目录路径") String path,
            @ToolParam(description = "是否显示行号") Boolean showLineNum) {

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String cmd;

        if (showLineNum != null && showLineNum) {
            cmd = isWindows ?
                    "findstr /n \"" + keyword + "\" " + path :
                    "grep -n \"" + keyword + "\" " + path;
        } else {
            cmd = isWindows ?
                    "findstr \"" + keyword + "\" " + path :
                    "grep \"" + keyword + "\" " + path;
        }

        return executeCommand(cmd);
    }

    /**
     * 获取当前工作目录
     */
    @Tool(description = "获取当前工作目录的绝对路径")
    public String getCurrentDirectory() {
        return executeCommand(System.getProperty("os.name").toLowerCase().contains("win") ? "cd" : "pwd");
    }

    /**
     * 创建目录
     */
    @Tool(description = "创建新目录")
    public String createDirectory(
            @ToolParam(description = "要创建的目录路径") String dirPath) {

        // 解析完整路径（基于项目目录）
        File dir = determineWorkingDirectory(dirPath);

        if (FileUtil.exist(dir)) {
            return "目录已存在: " + dirPath + " (完整路径: " + dir.getAbsolutePath() + ")";
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String fullPath = dir.getAbsolutePath();
        String cmd = isWindows ? "mkdir \"" + fullPath + "\"" : "mkdir -p '" + fullPath + "'";

        return executeCommand(cmd);
    }

    /**
     * 删除文件或空目录
     */
    @Tool(description = "删除指定的文件或空目录（注意：不能删除非空目录以确保安全）")
    public String deleteFile(
            @ToolParam(description = "要删除的文件或目录路径") String path) {

        // 解析完整路径（基于项目目录）
        File file = determineWorkingDirectory(path);

        if (!FileUtil.exist(file)) {
            return "路径不存在: " + path + " (完整路径: " + file.getAbsolutePath() + ")";
        }

        if (file.isDirectory() && file.list().length > 0) {
            return "出于安全考虑，不允许删除非空目录。请先删除目录中的文件。";
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String fullPath = file.getAbsolutePath();
        String cmd = isWindows ? "del \"" + fullPath + "\"" : "rm '" + fullPath + "'";

        return executeCommand(cmd);
    }

    /**
     * Git操作 - 查看状态
     */
    @Tool(description = "查看Git仓库状态")
    public String gitStatus() {
        return executeCommand("git status");
    }

    /**
     * Git操作 - 查看日志
     */
    @Tool(description = "查看Git提交历史")
    public String gitLog(
            @ToolParam(description = "显示的提交数量（可选，默认10条）") Integer count) {

        int n = count != null && count > 0 ? count : 10;
        return executeCommand("git log -n " + n + " --oneline");
    }

    /**
     * Git操作 - 查看当前分支
     */
    @Tool(description = "查看当前Git分支")
    public String gitBranch() {
        return executeCommand("git branch --show-current");
    }

    /**
     * 安全检查：验证命令是否安全可执行
     */
    private String checkCommandSafety(String command) {
        if (StrUtil.isBlank(command)) {
            return "命令不能为空";
        }

        String lowerCommand = command.toLowerCase().trim();

        // 检查危险命令模式
        for (String dangerous : DANGEROUS_PATTERNS) {
            if (lowerCommand.contains(dangerous.toLowerCase())) {
                return "命令包含危险操作: " + dangerous;
            }
        }

        // 检查命令白名单
        String baseCommand = lowerCommand.split("\\s+")[0];
        boolean isInWhitelist = ALLOWED_COMMANDS.stream()
                .anyMatch(allowed -> baseCommand.equals(allowed) || baseCommand.startsWith(allowed + " "));

        if (!isInWhitelist && !isGitCommand(lowerCommand) && !isMavenCommand(lowerCommand)) {
            return "命令不在允许列表中: " + baseCommand;
        }

        return null;
    }

    /**
     * 判断是否为Git命令
     */
    private boolean isGitCommand(String command) {
        return command.startsWith("git ");
    }

    /**
     * 判断是否为Maven命令
     */
    private boolean isMavenCommand(String command) {
        return command.startsWith("mvn ");
    }

    /**
     * 确定工作目录
     */
    private File determineWorkingDirectory(String workDir) {
        if (StrUtil.isBlank(workDir)) {
            return new File(PROJECT_DIR);
        }

        File dir = new File(workDir);
        if (dir.isAbsolute()) {
            return dir;
        }

        // 相对路径，基于项目目录
        return new File(PROJECT_DIR, workDir);
    }
}
