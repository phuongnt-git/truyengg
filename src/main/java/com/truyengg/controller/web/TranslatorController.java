package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TranslatorController {

  @GetMapping("/translator/stories")
  public String translatorStories() {
    return "translator/stories";
  }

  @GetMapping("/translator/upload")
  public String translatorUpload() {
    return "translator/upload-story";
  }
}
