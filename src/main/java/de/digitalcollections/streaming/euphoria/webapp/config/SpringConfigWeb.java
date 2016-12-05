package de.digitalcollections.streaming.euphoria.webapp.config;

import com.github.mxab.thymeleaf.extras.dataattribute.dialect.DataAttributeDialect;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.thymeleaf.spring4.SpringTemplateEngine;
import org.thymeleaf.spring4.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ServletContextTemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolver;

/**
 * Spring context.
 */
@Configuration
@ComponentScan(basePackages = {
  "de.digitalcollections.streaming.euphoria.webapp.controller"
})
@EnableAspectJAutoProxy
@EnableWebMvc //Enables Spring's MVC annotations
@PropertySource(value = {
  "classpath:de/digitalcollections/streaming/euphoria/webapp/config/SpringConfigWeb-${spring.profiles.active:PROD}.properties"
})
public class SpringConfigWeb extends WebMvcConfigurerAdapter {

  @Bean
  public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
    return new PropertySourcesPlaceholderConfigurer();
  }

  @Value("${cacheTemplates}")
  private boolean cacheTemplates;

  @Autowired
  private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

  @PostConstruct
  public void init() {
    requestMappingHandlerAdapter.setIgnoreDefaultModelOnRedirect(true);
  }

  /**
   * Handles HTTP GET requests for /resources/** by efficiently serving up static resources in the
   * ${symbol_dollar}{webappRoot}/resources directory
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/css/**").addResourceLocations("/css/");
    registry.addResourceHandler("/favicon.ico").addResourceLocations("/favicon.ico");
    registry.addResourceHandler("/fonts/**").addResourceLocations("/fonts/");
    registry.addResourceHandler("/images/**").addResourceLocations("/images/");
    registry.addResourceHandler("/img/**").addResourceLocations("/img/");
    registry.addResourceHandler("/js/**").addResourceLocations("/js/");
    registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
  }

  @Override
  public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
    ByteArrayHttpMessageConverter bc = new ByteArrayHttpMessageConverter();
    List<MediaType> supported = new ArrayList<>();
    supported.add(MediaType.IMAGE_JPEG);
    bc.setSupportedMediaTypes(supported);
    converters.add(bc);
  }

  @Bean
  public TemplateResolver servletContextTemplateResolver() {
    ServletContextTemplateResolver templateResolver = new ServletContextTemplateResolver();
    templateResolver.setPrefix("/WEB-INF/templates/");
    templateResolver.setSuffix(".html");
    templateResolver.setCharacterEncoding("UTF-8");
    templateResolver.setTemplateMode("HTML5");
    templateResolver.setCacheable(cacheTemplates);
    templateResolver.setOrder(2);
    return templateResolver;
  }

  @Bean
  public SpringTemplateEngine templateEngine() {
    SpringTemplateEngine templateEngine = new SpringTemplateEngine();
    templateEngine.addTemplateResolver(servletContextTemplateResolver());
    // Activate Thymeleaf LayoutDialect[1] (for 'layout'-namespace)
    // [1] https://github.com/ultraq/thymeleaf-layout-dialect
    templateEngine.addDialect(new LayoutDialect());
    templateEngine.addDialect(new DataAttributeDialect());
    return templateEngine;
  }

  @Bean
  public ViewResolver viewResolver() {
    ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
    viewResolver.setTemplateEngine(templateEngine());
    viewResolver.setContentType("text/html; charset=UTF-8");
    viewResolver.setCharacterEncoding("UTF-8");
    return viewResolver;
  }
}
