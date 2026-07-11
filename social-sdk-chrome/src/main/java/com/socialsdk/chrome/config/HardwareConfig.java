package com.socialsdk.chrome.config;

import java.io.Serializable;
import java.util.Random;

/**
 * 硬件伪装配置
 * 提供CPU、内存、GPU等硬件特征的伪装配置
 */
public class HardwareConfig implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private static final Random RANDOM = new Random();

    // ==================== CPU伪装配置 ====================

    /**
     * 是否启用CPU伪装
     */
    private boolean cpuSpoofEnabled = true;

    /**
     * 伪造的CPU核心数
     */
    private int cpuCores = 8;

    /**
     * 伪造的CPU逻辑处理器数
     */
    private int logicalProcessors = 16;

    /**
     * CPU型号名称
     */
    private String cpuModel = "Intel(R) Core(TM) i7-10700K CPU @ 3.80GHz";

    /**
     * CPU架构
     */
    private String cpuArchitecture = "x86";

    /**
     * CPU供应商
     */
    private String cpuVendor = "GenuineIntel";

    /**
     * CPU特性标志 (逗号分隔)
     */
    private String cpuFeatures = "fpu,vme,de,pse,tsc,msr,pae,mce,cx8,apic,sep,mtrr,pge,mca,cmov,pat,pse36,clflush,mmx,fxsum,sse,sse2,sse3,ssse3,sse4.1,sse4.2,popcnt,avx,avx2,aes,rdrand";

    /**
     * 缓存大小 (KB)
     */
    private int cacheSizeKB = 16384;

    // ==================== 内存伪装配置 ====================

    /**
     * 是否启用内存伪装
     */
    private boolean memorySpoofEnabled = true;

    /**
     * 伪造的设备内存 (GB)
     */
    private float deviceMemoryGB = 16.0f;

    /**
     * 伪造的可用内存 (MB)
     */
    private long availableMemoryMB = 8192L;

    /**
     * 伪造的已使用内存百分比
     */
    private float usedMemoryPercent = 45.0f;

    // ==================== GPU伪装配置 ====================

    /**
     * 是否启用GPU伪装
     */
    private boolean gpuSpoofEnabled = true;

    /**
     * 伪造的GPU Vendor
     */
    private String gpuVendor = "Google Inc. (NVIDIA)";

    /**
     * 伪造的GPU Renderer
     */
    private String gpuRenderer = "ANGLE (NVIDIA GeForce RTX 3080 Direct3D11 vs_0_0 mb_0_0)";

    /**
     * 伪造的WebGL Vendor
     */
    private String webglVendor = "Google Inc.";

    /**
     * 伪造的WebGL Renderer
     */
    private String webglRenderer = "ANGLE (NVIDIA GeForce RTX 3080 Direct3D11 vs_0_0 mb_0_0)";

    /**
     * 伪造的WebGL版本
     */
    private String webglVersion = "WebGL 2.0 (OpenGL ES 3.0 Chromium 120.0.0.0)";

    /**
     * 伪造的GPU驱动版本
     */
    private String gpuDriverVersion = "536.99";

    /**
     * 伪造的GPU驱动日期
     */
    private String gpuDriverDate = "2024-01-01";

    /**
     * 伪造的GPU视频内存 (MB)
     */
    private int gpuVideoMemoryMB = 10240;

    // ==================== 电池状态伪装配置 ====================

    /**
     * 是否启用电池状态伪装
     */
    private boolean batterySpoofEnabled = false;

    /**
     * 伪造的电池电量 (0-100)
     */
    private int batteryLevel = 100;

    /**
     * 是否正在充电
     */
    private boolean charging = true;

    /**
     * 伪造的充电时间 (秒，-1表示未知)
     */
    private int chargingTime = -1;

    /**
     * 伪造的放电时间 (秒，-1表示未知)
     */
    private int dischargingTime = -1;

    // ==================== 处理器伪装配置 ====================

    /**
     * 是否启用处理器伪装
     */
    private boolean processorSpoofEnabled = true;

    /**
     * 伪造的处理器速度 (MHz)
     */
    private int processorSpeedMHz = 3800;

    /**
     * 伪造的系统 uptime (秒)
     */
    private long systemUptimeSeconds = 86400L + RANDOM.nextInt(86400);

    // ==================== 存储伪装配置 ====================

    /**
     * 是否启用存储伪装
     */
    private boolean storageSpoofEnabled = true;

    /**
     * 伪造的硬盘总空间 (GB)
     */
    private long totalDiskSpaceGB = 512L;

    /**
     * 伪造的可用硬盘空间 (GB)
     */
    private long availableDiskSpaceGB = 256L;

    // ==================== 网络伪装配置 ====================

    /**
     * 是否启用网络伪装
     */
    private boolean networkSpoofEnabled = true;

    /**
     * 伪造的网络接口类型
     */
    private String networkInterface = "ethernet";

    /**
     * 伪造的MAC地址
     */
    private String macAddress = "00:1A:2B:3C:4D:5E";

    /**
     * 伪造的IP地址
     */
    private String ipAddress = "192.168.1.100";

    /**
     * 伪造的主机名
     */
    private String hostname = "DESKTOP-ABC1234";

    // ==================== 预置配置 ====================

    /**
     * 获取高性能PC配置
     */
    public static HardwareConfig highPerformanceConfig() {
        HardwareConfig config = new HardwareConfig();
        config.setCpuCores(8);
        config.setLogicalProcessors(16);
        config.setCpuModel("Intel(R) Core(TM) i7-10700K CPU @ 3.80GHz");
        config.setDeviceMemoryGB(16.0f);
        config.setAvailableMemoryMB(8192L);
        config.setGpuRenderer("ANGLE (NVIDIA GeForce RTX 3080 Direct3D11 vs_0_0 mb_0_0)");
        config.setGpuVideoMemoryMB(10240);
        config.setProcessorSpeedMHz(3800);
        return config;
    }

    /**
     * 获取普通PC配置
     */
    public static HardwareConfig normalConfig() {
        HardwareConfig config = new HardwareConfig();
        config.setCpuCores(4);
        config.setLogicalProcessors(8);
        config.setCpuModel("Intel(R) Core(TM) i5-10400F CPU @ 2.90GHz");
        config.setDeviceMemoryGB(8.0f);
        config.setAvailableMemoryMB(4096L);
        config.setGpuRenderer("ANGLE (NVIDIA GeForce GTX 1660 Super Direct3D11 vs_0_0 mb_0_0)");
        config.setGpuVideoMemoryMB(6144);
        config.setProcessorSpeedMHz(2900);
        return config;
    }

    /**
     * 获取Mac配置
     */
    public static HardwareConfig macConfig() {
        HardwareConfig config = new HardwareConfig();
        config.setCpuCores(8);
        config.setLogicalProcessors(16);
        config.setCpuModel("Apple M1 Pro");
        config.setCpuArchitecture("ARM64");
        config.setCpuVendor("Apple");
        config.setDeviceMemoryGB(16.0f);
        config.setAvailableMemoryMB(8192L);
        config.setGpuVendor("Apple");
        config.setGpuRenderer("Apple M1 Pro");
        config.setProcessorSpeedMHz(2400);
        return config;
    }

    /**
     * 随机化配置
     */
    public HardwareConfig randomize() {
        // 随机CPU核心数
        int[][] coreConfigs = {
            {4, 8}, {6, 12}, {8, 16}, {12, 24}
        };
        int[] selected = coreConfigs[RANDOM.nextInt(coreConfigs.length)];
        this.cpuCores = selected[0];
        this.logicalProcessors = selected[1];

        // 随机内存
        float[] memoryConfigs = {8.0f, 16.0f, 32.0f};
        this.deviceMemoryGB = memoryConfigs[RANDOM.nextInt(memoryConfigs.length)];
        this.availableMemoryMB = (long) (deviceMemoryGB * 1024 * (0.4 + RANDOM.nextDouble() * 0.3));

        // 随机处理器速度
        this.processorSpeedMHz = 2000 + RANDOM.nextInt(2500);

        // 随机系统运行时间
        this.systemUptimeSeconds = 86400L + RANDOM.nextInt(604800);

        // 随机MAC地址
        this.macAddress = String.format("00:1A:2B:%02X:%02X:%02X",
            RANDOM.nextInt(256), RANDOM.nextInt(256), RANDOM.nextInt(256));

        // 随机IP地址
        this.ipAddress = "192.168." + (1 + RANDOM.nextInt(254)) + "." + (1 + RANDOM.nextInt(254));

        return this;
    }

    // ==================== Getters and Setters ====================

    public boolean isCpuSpoofEnabled() {
        return cpuSpoofEnabled;
    }

    public void setCpuSpoofEnabled(boolean cpuSpoofEnabled) {
        this.cpuSpoofEnabled = cpuSpoofEnabled;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(int cpuCores) {
        this.cpuCores = cpuCores;
    }

    public int getLogicalProcessors() {
        return logicalProcessors;
    }

    public void setLogicalProcessors(int logicalProcessors) {
        this.logicalProcessors = logicalProcessors;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public String getCpuArchitecture() {
        return cpuArchitecture;
    }

    public void setCpuArchitecture(String cpuArchitecture) {
        this.cpuArchitecture = cpuArchitecture;
    }

    public String getCpuVendor() {
        return cpuVendor;
    }

    public void setCpuVendor(String cpuVendor) {
        this.cpuVendor = cpuVendor;
    }

    public String getCpuFeatures() {
        return cpuFeatures;
    }

    public void setCpuFeatures(String cpuFeatures) {
        this.cpuFeatures = cpuFeatures;
    }

    public int getCacheSizeKB() {
        return cacheSizeKB;
    }

    public void setCacheSizeKB(int cacheSizeKB) {
        this.cacheSizeKB = cacheSizeKB;
    }

    public boolean isMemorySpoofEnabled() {
        return memorySpoofEnabled;
    }

    public void setMemorySpoofEnabled(boolean memorySpoofEnabled) {
        this.memorySpoofEnabled = memorySpoofEnabled;
    }

    public float getDeviceMemoryGB() {
        return deviceMemoryGB;
    }

    public void setDeviceMemoryGB(float deviceMemoryGB) {
        this.deviceMemoryGB = deviceMemoryGB;
    }

    public long getAvailableMemoryMB() {
        return availableMemoryMB;
    }

    public void setAvailableMemoryMB(long availableMemoryMB) {
        this.availableMemoryMB = availableMemoryMB;
    }

    public float getUsedMemoryPercent() {
        return usedMemoryPercent;
    }

    public void setUsedMemoryPercent(float usedMemoryPercent) {
        this.usedMemoryPercent = usedMemoryPercent;
    }

    public boolean isGpuSpoofEnabled() {
        return gpuSpoofEnabled;
    }

    public void setGpuSpoofEnabled(boolean gpuSpoofEnabled) {
        this.gpuSpoofEnabled = gpuSpoofEnabled;
    }

    public String getGpuVendor() {
        return gpuVendor;
    }

    public void setGpuVendor(String gpuVendor) {
        this.gpuVendor = gpuVendor;
    }

    public String getGpuRenderer() {
        return gpuRenderer;
    }

    public void setGpuRenderer(String gpuRenderer) {
        this.gpuRenderer = gpuRenderer;
    }

    public String getWebglVendor() {
        return webglVendor;
    }

    public void setWebglVendor(String webglVendor) {
        this.webglVendor = webglVendor;
    }

    public String getWebglRenderer() {
        return webglRenderer;
    }

    public void setWebglRenderer(String webglRenderer) {
        this.webglRenderer = webglRenderer;
    }

    public String getWebglVersion() {
        return webglVersion;
    }

    public void setWebglVersion(String webglVersion) {
        this.webglVersion = webglVersion;
    }

    public String getGpuDriverVersion() {
        return gpuDriverVersion;
    }

    public void setGpuDriverVersion(String gpuDriverVersion) {
        this.gpuDriverVersion = gpuDriverVersion;
    }

    public String getGpuDriverDate() {
        return gpuDriverDate;
    }

    public void setGpuDriverDate(String gpuDriverDate) {
        this.gpuDriverDate = gpuDriverDate;
    }

    public int getGpuVideoMemoryMB() {
        return gpuVideoMemoryMB;
    }

    public void setGpuVideoMemoryMB(int gpuVideoMemoryMB) {
        this.gpuVideoMemoryMB = gpuVideoMemoryMB;
    }

    public boolean isBatterySpoofEnabled() {
        return batterySpoofEnabled;
    }

    public void setBatterySpoofEnabled(boolean batterySpoofEnabled) {
        this.batterySpoofEnabled = batterySpoofEnabled;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = Math.max(0, Math.min(100, batteryLevel));
    }

    public boolean isCharging() {
        return charging;
    }

    public void setCharging(boolean charging) {
        this.charging = charging;
    }

    public int getChargingTime() {
        return chargingTime;
    }

    public void setChargingTime(int chargingTime) {
        this.chargingTime = chargingTime;
    }

    public int getDischargingTime() {
        return dischargingTime;
    }

    public void setDischargingTime(int dischargingTime) {
        this.dischargingTime = dischargingTime;
    }

    public boolean isProcessorSpoofEnabled() {
        return processorSpoofEnabled;
    }

    public void setProcessorSpoofEnabled(boolean processorSpoofEnabled) {
        this.processorSpoofEnabled = processorSpoofEnabled;
    }

    public int getProcessorSpeedMHz() {
        return processorSpeedMHz;
    }

    public void setProcessorSpeedMHz(int processorSpeedMHz) {
        this.processorSpeedMHz = processorSpeedMHz;
    }

    public long getSystemUptimeSeconds() {
        return systemUptimeSeconds;
    }

    public void setSystemUptimeSeconds(long systemUptimeSeconds) {
        this.systemUptimeSeconds = systemUptimeSeconds;
    }

    public boolean isStorageSpoofEnabled() {
        return storageSpoofEnabled;
    }

    public void setStorageSpoofEnabled(boolean storageSpoofEnabled) {
        this.storageSpoofEnabled = storageSpoofEnabled;
    }

    public long getTotalDiskSpaceGB() {
        return totalDiskSpaceGB;
    }

    public void setTotalDiskSpaceGB(long totalDiskSpaceGB) {
        this.totalDiskSpaceGB = totalDiskSpaceGB;
    }

    public long getAvailableDiskSpaceGB() {
        return availableDiskSpaceGB;
    }

    public void setAvailableDiskSpaceGB(long availableDiskSpaceGB) {
        this.availableDiskSpaceGB = availableDiskSpaceGB;
    }

    public boolean isNetworkSpoofEnabled() {
        return networkSpoofEnabled;
    }

    public void setNetworkSpoofEnabled(boolean networkSpoofEnabled) {
        this.networkSpoofEnabled = networkSpoofEnabled;
    }

    public String getNetworkInterface() {
        return networkInterface;
    }

    public void setNetworkInterface(String networkInterface) {
        this.networkInterface = networkInterface;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * 生成硬件伪装的JavaScript代码
     */
    public String generateJavascriptInjection() {
        StringBuilder sb = new StringBuilder();
        sb.append("(function() {\n");

        // CPU伪装
        if (cpuSpoofEnabled) {
            sb.append("  Object.defineProperties(navigator, {\n");
            sb.append("    hardwareConcurrency: { get: () => ").append(logicalProcessors).append(", configurable: true }\n");
            sb.append("  });\n");
            
            sb.append("  if (window.performance && window.performance.hardwareConcurrency !== undefined) {\n");
            sb.append("    Object.defineProperty(window.performance, 'hardwareConcurrency', { get: () => ").append(logicalProcessors).append(" });\n");
            sb.append("  }\n");
        }

        // 内存伪装
        if (memorySpoofEnabled) {
            sb.append("  Object.defineProperties(navigator, {\n");
            sb.append("    deviceMemory: { get: () => ").append((int) deviceMemoryGB).append(", configurable: true }\n");
            sb.append("  });\n");
        }

        // 电池状态伪装
        if (batterySpoofEnabled) {
            sb.append("  if ('getBattery' in navigator) {\n");
            sb.append("    navigator.getBattery = function() {\n");
            sb.append("      return Promise.resolve({\n");
            sb.append("        level: ").append(batteryLevel / 100.0).append(",\n");
            sb.append("        charging: ").append(charging).append(",\n");
            sb.append("        chargingTime: ").append(chargingTime).append(",\n");
            sb.append("        dischargingTime: ").append(dischargingTime).append(",\n");
            sb.append("        addEventListener: function() {}\n");
            sb.append("      });\n");
            sb.append("    };\n");
            sb.append("  }\n");
        }

        // 处理器速度伪装
        if (processorSpoofEnabled) {
            sb.append("  if (window.performance && window.performance.now) {\n");
            sb.append("    var originalNow = window.performance.now.bind(window.performance);\n");
            sb.append("    window.performance.now = function() {\n");
            sb.append("      return originalNow() + ").append(systemUptimeSeconds * 1000).append(";\n");
            sb.append("    };\n");
            sb.append("  }\n");
        }

        sb.append("})();\n");
        return sb.toString();
    }
}
