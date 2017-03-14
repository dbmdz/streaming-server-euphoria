package de.digitalcollections.streaming.euphoria.webapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for serving different view pages.
 */
@Controller
public class ViewController {

  @RequestMapping(value = {"", "/"}, method = RequestMethod.GET)
  public String viewHomepage(Model model) {
    model.addAttribute("menu", "home");
    return "index";
  }

  @RequestMapping(value = "/audio/play", method = RequestMethod.POST)
  public String playAudioPost(@RequestParam String identifier) {
    return "redirect:/audio/" + identifier + "/play.html";
  }

  @RequestMapping(value = "/audio/{identifier}/play.html", method = RequestMethod.GET)
  public String playAudioGet(@PathVariable String identifier, Model model) {
    model.addAttribute("identifier", identifier);
    return "audio/play";
  }

  @RequestMapping(value = "/video/view", method = RequestMethod.POST)
  public String viewVideoPost(@RequestParam String identifier) {
    return "redirect:/video/" + identifier + "/view.html";
  }

  @RequestMapping(value = "/video/{identifier}/view.html", method = RequestMethod.GET)
  public String viewVideoGet(@PathVariable String identifier, Model model) {
    model.addAttribute("identifier", identifier);
    return "video/view";
  }
}
