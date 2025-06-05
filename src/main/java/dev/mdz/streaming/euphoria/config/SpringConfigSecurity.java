package dev.mdz.streaming.euphoria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SpringConfigSecurity extends WebSecurityConfigurerAdapter {

  @Value("${spring.security.user.name}")
  private String actuatorUsername;

  @Value("${spring.security.user.password}")
  private String actuatorPassword;

  @Override
  protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.inMemoryAuthentication()
        .passwordEncoder(passwordEncoderDummy())
        .withUser(User.withUsername(actuatorUsername).password(actuatorPassword).roles("ACTUATOR"));
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    // Monitoring:
    // see
    // https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#production-ready-endpoints
    http.antMatcher("/monitoring/**")
        .authorizeRequests()
        .requestMatchers(EndpointRequest.to(InfoEndpoint.class, HealthEndpoint.class))
        .permitAll()
        .requestMatchers(EndpointRequest.to("prometheus", "version"))
        .permitAll()
        .requestMatchers(EndpointRequest.toAnyEndpoint())
        .hasRole("ACTUATOR")
        .and()
        .httpBasic();
  }

  private PasswordEncoder passwordEncoderDummy() {
    return new PasswordEncoder() {
      @Override
      public String encode(CharSequence rawPassword) {
        return rawPassword.toString();
      }

      @Override
      public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return rawPassword.toString().equals(encodedPassword);
      }
    };
  }
}
