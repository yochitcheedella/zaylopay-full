package com.zaylope.registration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import java.util.List;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

	private final String allowedOrigin;

	public WebConfiguration(@Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin) {
		this.allowedOrigin = allowedOrigin;
	}

	/** Session filters can finish a request before MVC sees it, including expected 401s. */
	@Bean
	public FilterRegistrationBean<CorsFilter> applicantCorsFilter() {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(List.of(allowedOrigin));
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Content-Type", "X-Zaylope-Request", "Authorization", "X-Admin-Token"));
		cors.setExposedHeaders(List.of("Retry-After"));
		cors.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/v1/**", cors);
		FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
		return registration;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/v1/**")
				.allowedOrigins(allowedOrigin)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("Content-Type", "X-Zaylope-Request", "Authorization", "X-Admin-Token")
                .exposedHeaders("Retry-After")
				.allowCredentials(true);
	}
}

