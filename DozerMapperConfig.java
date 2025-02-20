package it.fastpoint.amedea.api.configuration;

import com.github.dozermapper.spring.DozerBeanMapperFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;

@Configuration
public class DozerMapperConfig {

    @Bean
    public DozerBeanMapperFactoryBean dozerMapper(ResourcePatternResolver resourcePatternResolver) throws IOException {
        DozerBeanMapperFactoryBean factoryBean = new DozerBeanMapperFactoryBean();
        factoryBean.setMappingFiles(resourcePatternResolver.getResources("classpath*:/*mapping.xml"));
        return factoryBean;
    }
}
