package de.digitalcollections.streaming.euphoria.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Root context.
 */
@Configuration
@ComponentScan(basePackages = {
  "de.digitalcollections.commons.springboot.actuator",
  "de.digitalcollections.commons.springboot.contributor",
  "de.digitalcollections.commons.springboot.monitoring",
  "de.digitalcollections.commons.file.config"
})
public class SpringConfig {
}
