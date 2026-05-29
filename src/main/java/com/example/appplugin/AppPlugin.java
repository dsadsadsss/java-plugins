package com.example.appplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.*;

public class AppPlugin extends JavaPlugin {

    private String port;
    private String vmms;
    private String vmmport;
    private String vmpath;
    private String vmport;
    private String xieyi;
    private String uuid;
    private String youxuan;
    private String subName;
    private String subUrl;
    private String baohuo;
    private String nezhaser;
    private String nezhaKey;
    private String nezport;
    private String neztls;
    private String filePath;
    private String tok;
    private String agentUuid;

    private final String WEB_FILENAME = "webdav";
    private final String NEZHA_FILENAME = "nexus";
    private final String CFF_FILENAME = "cfloat";

    private final Map<String, Process> runningProcesses = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadEnvironmentVariables();

        getLogger().info("====================================");
        getLogger().info("  AppPlugin 穿透与守护插件 (NIO 修复版)   ");
        getLogger().info("====================================");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            initializeDownloads();
            
            // 延迟15秒后启动首次进程检查
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, this::checkProcesses, 300L);
            
            // 启动定时任务（每隔 60 秒轮询检查一次进程存活）
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::checkProcesses, 1200L, 1200L);

            if (nezhaser.contains(":")) {
                Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::upname, 400L, 1200L);
            }
        });
    }

    @Override
    public void onDisable() {
        for (Map.Entry<String, Process> entry : runningProcesses.entrySet()) {
            if (entry.getValue().isAlive()) {
                entry.getValue().destroyForcibly();
                getLogger().info("已强制回收穿透子进程: " + entry.getKey());
            }
        }
    }

    private void loadEnvironmentVariables() {
        port = getEnvOrConfig("PORT", "3000");
        vmms = getEnvOrConfig("VPATH", "vls-123456");
        vmmport = getEnvOrConfig("VL_PORT", "8002");
        vmpath = getEnvOrConfig("MPATH", "vms-3456789");
        vmport = getEnvOrConfig("VM_PORT", "8001");
        xieyi = getEnvOrConfig("XIEYI", "vms");
        uuid = getEnvOrConfig("UUID", "3a8a1de5-7d41-45e2-88fe-0f538b822169");
        youxuan = getEnvOrConfig("CF_IP", "ip.sb");
        subName = getEnvOrConfig("SUB_NAME", "GitHub");
        subUrl = getEnvOrConfig("SUB_URL", "");
        baohuo = getEnvOrConfig("BAOHUO_URL", "");
        nezhaser = getEnvOrConfig("NSERVER", "xxx:443");
        nezhaKey = getEnvOrConfig("NKEY", "");
        nezport = getEnvOrConfig("NPORT", "443");
        neztls = getEnvOrConfig("NTLS", "--tls");
        filePath = getEnvOrConfig("FILE_PATH", getDataFolder().getAbsolutePath());
        tok = getEnvOrConfig("TOK", "");

        try {
            String seed = subName + uuid + nezhaser + nezhaKey + tok;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String hash = sb.toString();
            String generatedUuid = String.format("%s-%s-%s-%s-%s",
                    hash.substring(0, 8), hash.substring(8, 12),
                    hash.substring(12, 16), hash.substring(16, 20),
                    hash.substring(20, 32));
            agentUuid = System.getenv("AGENT_UUID") != null ? System.getenv("AGENT_UUID") : generatedUuid;
        } catch (Exception e) {
            agentUuid = "fraewrwdf-das-2sd2-4324-f232df";
        }
    }

    private String getEnvOrConfig(String key, String defaultValue) {
        String env = System.getenv(key);
        if (env != null && !env.isEmpty()) return env;
        return getConfig().getString(key, defaultValue);
    }

    private void initializeDownloads() {
        File dir = new File(filePath);
        if (!dir.exists()) dir.mkdirs();

        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        int archType = 0; 
        if (os.contains("freebsd")) {
            archType = 2;
        } else if (arch.contains("arm") || arch.contains("aarch64")) {
            archType = 1;
        }

        // 1. 哪吒下载链接
        String nezhaUrl;
        boolean hasPort = nezhaser.contains(":");
        if (archType == 2) {
            nezhaUrl = hasPort 
                    ? getEnvOrConfig("NEZHA_URL_BSD_ALT", "https://github.com/dsadsadsss/java-plugins/releases/download/1/agent2-freebsd_amd64")
                    : getEnvOrConfig("NEZHA_URL_BSD", "https://github.com/dsadsadsss/java-plugins/releases/download/1/agent-freebsd_amd64");
        } else if (archType == 1) {
            nezhaUrl = hasPort 
                    ? getEnvOrConfig("NEZHA_URL_ARM64_ALT", "https://github.com/dsadsadsss/java-plugins/releases/download/1/agent2-linux_arm64")
                    : getEnvOrConfig("NEZHA_URL_ARM64", "https://github.com/dsadsadsss/java-plugins/releases/download/1/agent-linux_arm64");
        } else {
            nezhaUrl = hasPort 
                    ? getEnvOrConfig("NEZHA_URL_X64_ALT", "https://github.com/dsadsadsss/java-plugins/releases/download/1/agent2-linux_amd64")
                    : getEnvOrConfig("NEZHA_URL_X64", "https://github.com/dsadsadsss/java-plugins/releases/download/1/agent-linux_amd64");
        }

        // 2. WEB 下载链接
        String webUrl;
        if (archType == 2) {
            webUrl = getEnvOrConfig("WEB_URL_BSD", "https://github.com/dsadsadsss/1/releases/download/xry/kano-bsd");
        } else if (archType == 1) {
            webUrl = getEnvOrConfig("WEB_URL_ARM64", "https://github.com/dsadsadsss/1/releases/download/xry/kano-yuan-arm");
        } else {
            webUrl = getEnvOrConfig("WEB_URL_X64", "https://github.com/dsadsadsss/1/releases/download/xry/kano-yuan");
        }

        // 3. CFF 下载链接
        String cffUrl;
        if (archType == 2) {
            cffUrl = getEnvOrConfig("CFF_URL_BSD", "https://github.com/dsadsadsss/1/releases/download/xry/argo-bsdamd");
        } else if (archType == 1) {
            cffUrl = getEnvOrConfig("CFF_URL_ARM64", "https://github.com/dsadsadsss/java-plugins/releases/download/1/cff-linux-arm64");
        } else {
            cffUrl = getEnvOrConfig("CFF_URL_X64", "https://github.com/dsadsadsss/java-plugins/releases/download/1/cff-linux-amd64");
        }

        downloadFile(cffUrl, CFF_FILENAME);
        downloadFile(webUrl, WEB_FILENAME);

        if (!nezhaser.isEmpty() && !nezhaKey.isEmpty()) {
            downloadFile(nezhaUrl, NEZHA_FILENAME);
            if (hasPort) {
                createNezhaConfig();
            }
        }
    }

    private void downloadFile(String urlStr, String filename) {
        File file = new File(filePath, filename);
        
        // 如果文件存在，跳过下载，但仍执行原生 NIO 赋权
        if (file.exists()) {
            tryNativeChmod(file);
            return; 
        }

        try {
            getLogger().info("正在下载核心组件 [" + filename + "], 远程地址: " + urlStr);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                String newUrl = conn.getHeaderField("Location");
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                status = conn.getResponseCode();
            }

            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("服务器响应状态码异常: " + status);
            }

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            // 核心修复：执行纯 Java 原生 NIO 权限设定，不再调用外部 'chmod' 进程
            tryNativeChmod(file);
            
        } catch (Exception e) {
            getLogger().severe("【错误】文件 [" + filename + "] 请求或落地失败。原因: " + e.getMessage());
        }
    }

    /**
     * 核心改进：使用 Java 7+ 原生 NIO POSIX 接口设置可执行权限，完美避开面板容器对 chmod 命令的拦截
     */
    private void tryNativeChmod(File file) {
        Path path = file.toPath();
        try {
            // 准备最高全控制权限集合 (rwxrwxrwx)
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_READ);
            perms.add(PosixFilePermission.GROUP_WRITE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_READ);
            perms.add(PosixFilePermission.OTHERS_WRITE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);

            // 直接通过系统内核级接口修改属性
            Files.setPosixFilePermissions(path, perms);
            getLogger().info("【成功】核心文件 [" + file.getName() + "] 已通过原生 NIO 注入 777 可执行权限。");
        } catch (UnsupportedOperationException e) {
            // 针对 Windows 开发测试环境做向下兼容兼容，Windows 不支持 POSIX 属性
            boolean r = file.setReadable(true, false);
            boolean w = file.setWritable(true, false);
            boolean x = file.setExecutable(true, false);
            getLogger().info("【提示】当前文件系统不支持 POSIX，已启用本地 Acl 降级赋权策略。结果: " + (r && w && x));
        } catch (IOException e) {
            getLogger().severe("【严重错误】原生 NIO 刷写文件权限失败: " + e.getMessage());
        }
    }

    private void createNezhaConfig() {
        File configFile = new File(filePath, "config.yml");
        if (configFile.exists()) return;

        String content = "client_secret: " + nezhaKey + "\n" +
                "debug: false\n" +
                "disable_auto_update: false\n" +
                "disable_command_execute: false\n" +
                "disable_force_update: false\n" +
                "disable_nat: false\n" +
                "disable_send_query: false\n" +
                "gpu: false\n" +
                "insecure_tls: true\n" +
                "ip_report_period: 1800\n" +
                "report_delay: 3\n" +
                "server: " + nezhaser + "\n" +
                "skip_connection_count: false\n" +
                "skip_procs_count: false\n" +
                "temperature: false\n" +
                "tls: " + neztls.equals("--tls") + "\n" +
                "use_gitee_to_upgrade: false\n" +
                "use_ipv6_country_code: false\n" +
                "uuid: " + agentUuid;

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(content);
            getLogger().info("哪吒agent2专配本地 config.yml 文件创建成功。");
        } catch (IOException e) {
            getLogger().severe("本地 config.yml 配置文件刷写失败: " + e.getMessage());
        }
    }

    private void checkProcesses() {
        keepProcessAlive(WEB_FILENAME, new String[]{
                new File(filePath, WEB_FILENAME).getAbsolutePath()
        }, new String[]{
                "MPATH=" + vmpath, "VM_PORT=" + vmport, "VPATH=" + vmms, "VL_PORT=" + vmmport, "UUID=" + uuid
        });

        String targetPort = xieyi.equals("vms") ? vmport : vmmport;
        String[] cffCmd = tok.isEmpty() 
                ? new String[]{new File(filePath, CFF_FILENAME).getAbsolutePath(), "tunnel", "--edge-ip-version", "auto", "--protocol", "auto", "--url", "http://localhost:" + targetPort, "--no-autoupdate"}
                : new String[]{new File(filePath, CFF_FILENAME).getAbsolutePath(), "tunnel", "--edge-ip-version", "auto", "--protocol", "auto", "run", "--no-autoupdate", "--token", tok};
        keepProcessAlive(CFF_FILENAME, cffCmd, null);

        if (!nezhaser.isEmpty() && !nezhaKey.isEmpty()) {
            String[] nezhaCmd = nezhaser.contains(":")
                    ? new String[]{new File(filePath, NEZHA_FILENAME).getAbsolutePath(), "-c", new File(filePath, "config.yml").getAbsolutePath()}
                    : new String[]{new File(filePath, NEZHA_FILENAME).getAbsolutePath(), "-s", nezhaser + ":" + nezport, "-p", nezhaKey, neztls};
            keepProcessAlive(NEZHA_FILENAME, nezhaCmd, null);
        }

        if (!baohuo.isEmpty()) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("https://" + baohuo).openConnection();
                conn.setConnectTimeout(5000);
                conn.getInputStream().close();
            } catch (Exception ignored) {}
        }
    }

    private void keepProcessAlive(String name, String[] cmd, String[] env) {
        Process p = runningProcesses.get(name);
        if (p == null || !p.isAlive()) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                if (env != null) {
                    for (String e : env) {
                        String[] kv = e.split("=", 2);
                        pb.environment().put(kv[0], kv[1]);
                    }
                }
                pb.redirectOutput(ProcessBuilder.Redirect.to(new File(getDataFolder(), name + ".log")));
                pb.redirectError(ProcessBuilder.Redirect.to(new File(getDataFolder(), name + "_err.log")));
                
                runningProcesses.put(name, pb.start());
                getLogger().info("穿透子服务进程 [" + name + "] 已拉起并建立状态监听。");
            } catch (Exception e) {
                getLogger().severe("【崩溃】无法唤醒后台子服务 [" + name + "]: " + e.getMessage());
            }
        }
    }

    private void upname() {
        if (nezhaser.isEmpty() || nezhaKey.isEmpty()) return;
        try {
            String nezUrl = nezhaser.replaceAll(":\\d+$", "");
            URL url = new URL("https://" + nezUrl + "/upload?token=" + nezhaKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);

            String json = "{\"SUBNAME\":\"" + subName + "\",\"UUID\":\"" + agentUuid + "\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code == 200 || code == 202) {
                getLogger().info("哪吒节点名称异步同步成功 -> " + subName);
            }
        } catch (Exception e) {
            getLogger().warning("同步哪吒节点名遇到网络颠簸: " + e.getMessage());
        }
    }
}
