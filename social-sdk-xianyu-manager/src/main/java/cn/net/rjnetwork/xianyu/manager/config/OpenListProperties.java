package cn.net.rjnetwork.xianyu.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openlist")
public class OpenListProperties {

    private String url = "http://127.0.0.1:5244";
    private String username = "admin";
    private String password = "openlist";
    private String dataDir = "./data/openlist";
    private String executableName = "openlist.exe";
    private int port = 5244;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public String getExecutableName() { return executableName; }
    public void setExecutableName(String executableName) { this.executableName = executableName; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
}
