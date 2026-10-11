package de.team.betriebsrente.config;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.keycloak.issuer-uri}") String issuerUri,
            @Value("${app.keycloak.backchannel-issuer-uri}") String backchannelIssuerUri,
            @Value("${app.keycloak.client-id}") String clientId,
            @Value("${app.keycloak.client-secret}") String clientSecret) {
        String publicOidc = issuerUri + "/protocol/openid-connect";
        String backchannelOidc = backchannelIssuerUri + "/protocol/openid-connect";
        ClientRegistration keycloak = ClientRegistration.withRegistrationId("keycloak")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName("Keycloak")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri(issuerUri)
                .authorizationUri(publicOidc + "/auth")
                .tokenUri(backchannelOidc + "/token")
                .userInfoUri(backchannelOidc + "/userinfo")
                .userNameAttributeName("preferred_username")
                .jwkSetUri(backchannelOidc + "/certs")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", publicOidc + "/logout"))
                .build();
        return new InMemoryClientRegistrationRepository(keycloak);
    }

    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> keycloakOidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser oidcUser = delegate.loadUser(request);
            Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
            Map<String, Object> realmAccess = oidcUser.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
                for (Object role : roles) {
                    if ("USER".equals(role) || "ADMIN".equals(role)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                    if ("ADMIN".equals(role)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    }
                }
            }
            return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(),
                    "preferred_username");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            OAuth2UserService<OidcUserRequest, OidcUser> keycloakOidcUserService,
            ClientRegistrationRepository registrations) throws Exception {
        OidcClientInitiatedLogoutSuccessHandler logoutHandler =
                new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logoutHandler.setPostLogoutRedirectUri("{baseUrl}/login?logout");

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/oauth2/**", "/login/oauth2/**")
                        .permitAll()
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().hasAnyRole("USER", "ADMIN"))
                .oauth2Login(login -> login
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(keycloakOidcUserService))
                        .defaultSuccessUrl("/", true))
                .logout(logout -> logout
                        .logoutSuccessHandler(logoutHandler)
                        .permitAll());
        return http.build();
    }
}
