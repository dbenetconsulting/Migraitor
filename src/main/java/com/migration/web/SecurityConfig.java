package com.migration.web;

import com.migration.config.Secrets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.UUID;

/**
 * v1 had no login and a public /actuator/shutdown. Now: form login with uiusername / uipassword from
 * the secrets file, CSRF token in a cookie (read by app.js), only /actuator/health public.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService users(Secrets secrets, PasswordEncoder encoder) {
        String user = secrets.find("uiusername").orElse("admin");
        String password = secrets.find("uipassword").orElseGet(() -> {
            String generated = UUID.randomUUID().toString();
            log.warn("No 'uipassword' in the secrets file. Temporary password for '{}': {}", user, generated);
            return generated;
        });
        return new InMemoryUserDetailsManager(User.withUsername(user).password(encoder.encode(password)).roles("ADMIN").build());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .antMatchers("/login", "/css/**", "/js/**", "/img/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/migrationtool", true).permitAll())
                .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                // API calls without a session get 401 instead of the login page, so the UI can react.
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
                .headers(h -> h.frameOptions().deny());
        return http.build();
    }
}
