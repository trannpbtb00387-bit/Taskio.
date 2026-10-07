package com.poly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.poly.service.AuthService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain taskioSecurity(HttpSecurity http, AuthService authService) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/about", "/login", "/register", "/shop", "/shop/**", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/promotions/**", "/employees/**", "/orders/*/assign").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .successHandler((request, response, authentication) -> {
                    request.getSession(true).setAttribute("userId", authService.userId(authentication.getName()));
                    response.sendRedirect("/dashboard");
                }).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/about?logout").permitAll());
        return http.build();
    }

    /** Plain-text matching is retained only because this coursework demo explicitly stores raw passwords. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new PasswordEncoder() {
            @Override public String encode(CharSequence rawPassword) { return rawPassword.toString(); }
            @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encodedPassword != null && encodedPassword.contentEquals(rawPassword);
            }
        };
    }
}
