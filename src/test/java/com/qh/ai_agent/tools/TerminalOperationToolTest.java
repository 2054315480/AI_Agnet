package com.qh.ai_agent.tools;

import com.qh.ai_agent.AiAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TerminalOperationTool 测试类
 */
@SpringBootTest(classes = AiAgentApplication.class)
public class TerminalOperationToolTest {

    @Autowired
    private TerminalOperationTool terminalOperationTool;

    @Test
    public void testGetCurrentDirectory() {
        // 测试获取当前目录
        String result = terminalOperationTool.getCurrentDirectory();
        System.out.println("当前目录:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testListFiles() {
        // 测试列出文件
        String result = terminalOperationTool.listFiles(null, true);
        System.out.println("文件列表（详细信息）:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testExecuteCommand() {
        // 测试执行简单命令
        String result = terminalOperationTool.executeCommand("echo Hello World");
        System.out.println("执行 echo 命令:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testReadFile() {
        // 测试读取文件（读取pom.xml的前20行）
        String result = terminalOperationTool.readFileByCommand("pom.xml", 20);
        System.out.println("读取 pom.xml 前20行:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testReadFileWithFullPath() {
        // 测试使用绝对路径读取文件
        String result = terminalOperationTool.readFileByCommand("D:/AI_Agent/pom.xml", 10);
        System.out.println("使用绝对路径读取 pom.xml 前10行:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testSearchInFile() {
        // 测试搜索文件内容
        String result = terminalOperationTool.searchInFile("spring-boot", "pom.xml", true);
        System.out.println("在 pom.xml 中搜索 'spring-boot':");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testGitStatus() {
        // 测试Git状态
        String result = terminalOperationTool.gitStatus();
        System.out.println("Git 状态:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testGitBranch() {
        // 测试查看Git分支
        String result = terminalOperationTool.gitBranch();
        System.out.println("当前 Git 分支:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testGitLog() {
        // 测试查看Git日志
        String result = terminalOperationTool.gitLog(5);
        System.out.println("最近5条Git提交:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testCreateDirectory() {
        // 测试创建目录
        String result = terminalOperationTool.createDirectory("./test-dir");
        System.out.println("创建目录结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testSecurityCheck() {
        // 测试安全检查 - 尝试执行危险命令
        String result = terminalOperationTool.executeCommand("rm -rf /");
        System.out.println("执行危险命令（应该被拒绝）:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testWhitelistCheck() {
        // 测试白名单检查 - 尝试执行不在白名单中的命令
        String result = terminalOperationTool.executeCommand("unknown-command");
        System.out.println("执行不在白名单中的命令（应该被拒绝）:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testListFilesWithPath() {
        // 测试列出指定目录的文件
        String result = terminalOperationTool.listFiles("src/main/java", false);
        System.out.println("列出 src/main/java 目录的文件:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }
}
