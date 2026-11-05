package org.ivyhongzhan.rms;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class FrontendController {
    @GetMapping("/") String index() { return "forward:/static/index.html"; }
    @GetMapping("/portfolio/") String portfolio() { return "forward:/portfolio/index.html"; }
}
