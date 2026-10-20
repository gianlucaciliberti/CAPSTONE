package it.squadra.gestionale.services;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.services.EmailService.EmailBody;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Composizione delle email con Thymeleaf. Ogni email ha due template in {@code resources/templates/email}:
 * {@code <nome>.html} (layout responsive con tabelle e stili inline, adatto ai client di posta) e
 * {@code <nome>.txt} (versione testuale). I valori inseriti vengono sottoposti a escape automatico
 * ({@code th:text}), quindi note e nomi scritti dagli utenti non possono iniettare HTML.
 */
@Service
public class EmailTemplateService {

    static final Locale ITALIAN = Locale.ITALIAN;
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'alle ore' HH:mm", ITALIAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", ITALIAN);

    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;
    private final String frontendUrl;
    private final String teamName;
    private final ZoneId zone;

    public EmailTemplateService(AppProperties properties) {
        this.htmlEngine = engine(TemplateMode.HTML, ".html");
        this.textEngine = engine(TemplateMode.TEXT, ".txt");
        this.frontendUrl = properties.frontendUrl().replaceAll("/+$", "");
        this.teamName = properties.mail().fromName();
        this.zone = properties.timezone();
    }

    /**
     * Compone l'email {@code template} con le variabili indicate. Sono sempre disponibili anche
     * {@code teamName} e {@code frontendUrl}.
     */
    public EmailBody render(String template, Map<String, Object> variables) {
        Map<String, Object> all = new HashMap<>(variables);
        all.putIfAbsent("teamName", teamName);
        all.putIfAbsent("frontendUrl", frontendUrl);
        Context context = new Context(ITALIAN, all);
        return new EmailBody(textEngine.process("email/" + template, context).strip() + "\n",
                htmlEngine.process("email/" + template, context));
    }

    /** "sabato 7 novembre 2026 alle ore 15:30", nel fuso della squadra. */
    public String formatDateTime(Instant instant) {
        return instant == null ? null : DATE_TIME.format(instant.atZone(zone));
    }

    public String formatTime(Instant instant) {
        return instant == null ? null : TIME.format(instant.atZone(zone));
    }

    public String link(String path) {
        return frontendUrl + path;
    }

    private static TemplateEngine engine(TemplateMode mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
