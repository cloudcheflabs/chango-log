package co.cloudcheflabs.chango.log.config;

import co.cloudcheflabs.chango.log.util.StringUtils;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.util.Properties;

import static co.cloudcheflabs.chango.log.Chango.ENV_CHANGO_LOG_CONFIGURATION_PATH;

@Configuration
public class ConfigurationLoader {

    @Bean
    public Properties configuration() {
        String confPath = StringUtils.getEnv(ENV_CHANGO_LOG_CONFIGURATION_PATH);

        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(confPath));
        yaml.afterPropertiesSet();
        Properties props = yaml.getObject();

        return props;
    }
}
