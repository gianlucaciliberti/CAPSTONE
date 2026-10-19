package it.squadra.gestionale.config;

import it.squadra.gestionale.services.AdminBootstrapService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** All'avvio crea il primo Admin se configurato tramite ADMIN_BOOTSTRAP_* (vedi {@link AdminBootstrapService}). */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService adminBootstrapService;

    public AdminBootstrapRunner(AdminBootstrapService adminBootstrapService) {
        this.adminBootstrapService = adminBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminBootstrapService.createFirstAdminIfConfigured();
    }
}
