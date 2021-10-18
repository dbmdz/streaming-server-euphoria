package de.digitalcollections.streaming.euphoria.config;

import de.digitalcollections.commons.springmvc.interceptors.CurrentUrlAsModelAttributeHandlerInterceptor;
import java.util.Locale;
import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

@Configuration
@EnableAspectJAutoProxy
public class SpringConfigWeb implements WebMvcConfigurer {

  private static final Logger LOGGER = LoggerFactory.getLogger(SpringConfigWeb.class);

  /**
   * Handles HTTP GET requests for /resources/** by efficiently serving up static resources in the
   * ${symbol_dollar}{webappRoot}/resources directory
   *
   * @param registry resourcehandler registry
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/favicon.ico").addResourceLocations("/favicon.ico");
  }

  @Bean
  public LayoutDialect layoutDialect() {
    return new LayoutDialect();
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    LocaleChangeInterceptor localeChangeInterceptor = new LocaleChangeInterceptor();
    localeChangeInterceptor.setParamName("language");
    registry.addInterceptor(localeChangeInterceptor);

    CurrentUrlAsModelAttributeHandlerInterceptor currentUrlAsModelAttributeHandlerInterceptor =
        new CurrentUrlAsModelAttributeHandlerInterceptor();
    currentUrlAsModelAttributeHandlerInterceptor.deleteParams("language");
    registry.addInterceptor(currentUrlAsModelAttributeHandlerInterceptor);
  }

  @Bean
  public LocaleResolver localeResolver() {
    SessionLocaleResolver localeResolver = new SessionLocaleResolver();
    localeResolver.setDefaultLocale(Locale.ENGLISH);
    return localeResolver;
  }
}
