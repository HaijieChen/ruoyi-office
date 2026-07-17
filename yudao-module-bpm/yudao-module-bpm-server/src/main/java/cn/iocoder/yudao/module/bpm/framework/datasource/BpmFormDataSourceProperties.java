package cn.iocoder.yudao.module.bpm.framework.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic-form data-source runtime configuration.
 *
 * <p>Credentials are supplied only through externalized Spring properties. Production configuration maps
 * these fields to environment variables and never stores a password in source control.</p>
 */
@Data
@ConfigurationProperties(prefix = "bpm.form-data-source")
public class BpmFormDataSourceProperties {

    private int maxRows = 200;
    private int timeoutSeconds = 3;
    private Jdbc jdbc = new Jdbc();
    private Api api = new Api();

    @Data
    public static class Jdbc {
        private String url;
        private String username;
        private String password;
        private int maximumPoolSize = 2;
        private long connectionTimeoutMs = 3000L;
    }

    @Data
    public static class Api {
        private String baseUrl;
        private List<String> allowedPaths = new ArrayList<>();
        private int connectTimeoutSeconds = 3;
    }

}
