package dev.mdz.streaming.euphoria.config;

import de.digitalcollections.commons.file.config.SpringConfigCommonsFile;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Root context. */
@Configuration
@ComponentScan(
    basePackages = {
      "de.digitalcollections.commons.springboot.actuator",
      "de.digitalcollections.commons.springboot.contributor",
      "de.digitalcollections.commons.springboot.monitoring"
    })
@Import(SpringConfigCommonsFile.class)
public class SpringConfig {}
