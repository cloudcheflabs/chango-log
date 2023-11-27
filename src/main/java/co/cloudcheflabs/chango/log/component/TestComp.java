package co.cloudcheflabs.chango.log.component;

import co.cloudcheflabs.chango.log.Chango;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class TestComp implements InitializingBean {

    private static Logger LOG = LoggerFactory.getLogger(TestComp.class);

    @Autowired
    private Properties configuration;


    @Override
    public void afterPropertiesSet() throws Exception {
        LOG.info("age: {}", configuration.getProperty("test.age"));
        LOG.info("name: {}", configuration.getProperty("test.name"));
    }
}
