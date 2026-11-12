package com.venuenexus.dev;

import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.repository.AppUserRepository;
import com.venuenexus.venue.entity.Location;
import com.venuenexus.venue.entity.Organisation;
import com.venuenexus.venue.entity.Tenant;
import com.venuenexus.venue.repository.LocationRepository;
import com.venuenexus.venue.repository.OrganisationRepository;
import com.venuenexus.venue.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("local")
public class LocalBootstrapConfig {

    @Bean
    CommandLineRunner localBootstrap(
            TenantRepository tenantRepository,
            OrganisationRepository organisationRepository,
            LocationRepository locationRepository,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${venuenexus.dev.bootstrap-enabled:false}") boolean enabled,
            @Value("${venuenexus.dev.admin-email:admin@localhost}") String email,
            @Value("${venuenexus.dev.admin-password:}") String password) {
        return args -> {
            if (!enabled || password.isBlank()) {
                return;
            }

            Tenant tenant = tenantRepository.findByName("Local Demo Tenant")
                    .orElseGet(() -> tenantRepository.save(Tenant.builder()
                            .name("Local Demo Tenant")
                            .status("ACTIVE")
                            .branding("{}")
                            .build()));
            Organisation organisation = organisationRepository.findByTenantIdAndName(tenant.getId(), "Local Demo Organisation")
                    .orElseGet(() -> organisationRepository.save(Organisation.builder()
                            .tenantId(tenant.getId())
                            .name("Local Demo Organisation")
                            .status("ACTIVE")
                            .build()));
            Location location = locationRepository
                    .findByTenantIdAndOrganisationIdAndName(
                            tenant.getId(), organisation.getId(), "Local Demo Location")
                    .orElseGet(() -> locationRepository.save(Location.builder()
                        .tenantId(tenant.getId())
                        .organisationId(organisation.getId())
                        .name("Local Demo Location")
                        .timezone("UTC")
                        .currency("USD")
                        .config("{}")
                        .build()));

            if (appUserRepository.findByEmailIgnoreCase(email).isEmpty()) {
                appUserRepository.save(AppUser.builder()
                        .tenantId(tenant.getId())
                        .organisationId(organisation.getId())
                        .locationId(location.getId())
                        .email(email.toLowerCase().trim())
                        .passwordHash(passwordEncoder.encode(password))
                        .displayName("Local Demo Administrator")
                        .role("TENANT_ADMIN")
                        .status("ACTIVE")
                        .available(true)
                        .failedLoginCount(0)
                        .build());
            }
        };
    }
}
