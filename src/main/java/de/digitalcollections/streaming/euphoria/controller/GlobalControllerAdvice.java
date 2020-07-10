package de.digitalcollections.streaming.euphoria.controller;

import de.digitalcollections.streaming.euphoria.config.WebjarProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

  private final Map<String, String> webjarVersions;

  public GlobalControllerAdvice(WebjarProperties webjarProperties) {
    this.webjarVersions = webjarProperties.getVersions();
  }

  /** Adds the webjar versions read from yaml files as global model attribute. */
  @ModelAttribute("webjarVersions")
  public Map<String, String> getWebjarVersions() {
    return webjarVersions;
  }
}
