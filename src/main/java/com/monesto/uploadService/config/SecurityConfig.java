package com.monesto.uploadService.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import com.monesto.uploadService.utils.UploadServiceUtil;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	
	private static final String USER_NAME = "user.name";
	private static final String ACCESS_KEY = "access.key";

	
	@Bean
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception { 
		http.authorizeHttpRequests((requests) -> requests.anyRequest().authenticated());
		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.httpBasic(withDefaults());
		return http.build();
	}
	
	@Bean
	UserDetailsService userDetailsService(){
		return new InMemoryUserDetailsManager(User
				.withUsername(UploadServiceUtil.properties.getProperty(USER_NAME))
				.password(passwordEncoder().encode(UploadServiceUtil.properties.getProperty(ACCESS_KEY)))
				.build());
	}
	
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
