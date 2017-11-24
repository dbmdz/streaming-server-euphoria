package de.digitalcollections.streaming.euphoria.config;

import java.util.Locale;
import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

@Configuration
@EnableAspectJAutoProxy
public class SpringConfigWeb extends WebMvcConfigurerAdapter {

//  @Autowired
//  private RequestMappingHandlerAdapter requestMappingHandlerAdapter;
//
//  @PostConstruct
//  public void init() {
//    requestMappingHandlerAdapter.setIgnoreDefaultModelOnRedirect(true);
//  }
  /**
   * Handles HTTP GET requests for /resources/** by efficiently serving up static resources in the
   * ${symbol_dollar}{webappRoot}/resources directory
   * @param registry resourcehandler registry
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/favicon.ico").addResourceLocations("/favicon.ico");
    registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/").setCachePeriod(Integer.MAX_VALUE);
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
  }

  @Bean(name = "localeResolver")
  public LocaleResolver sessionLocaleResolver() {
    SessionLocaleResolver localeResolver = new SessionLocaleResolver();
    localeResolver.setDefaultLocale(Locale.GERMAN);
    return localeResolver;
  }
}
