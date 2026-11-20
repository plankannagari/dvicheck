package com.dvicheck.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the admin shell (src/main/resources/static/admin/) at /admin/ (Day 38). Spring
 * Boot only maps a welcome page for the site root, not for sub-folders.
 *
 * /admin redirects to /admin/ (rather than serving the page) so the page's relative
 * references (admin.js, admin.css) resolve under /admin/ instead of the site root.
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/admin", "/admin/");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }
}
