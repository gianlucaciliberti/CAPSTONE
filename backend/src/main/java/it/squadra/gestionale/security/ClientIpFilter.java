package it.squadra.gestionale.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ricava l'IP reale del client e lo schema (http/https) dietro i proxy di produzione, per rate limiting, audit log,
 * cookie e HSTS. Sostituisce il filtro standard di Spring ({@code server.forward-headers-strategy=none}).
 *
 * <p>Su Render la richiesta attraversa Cloudflare, il proxy interno di Render e, passando dalla rewrite del sito
 * statico, anche il sito statico stesso. Ogni passaggio <b>aggiunge</b> un indirizzo in fondo a
 * {@code X-Forwarded-For}, senza cancellare quelli inviati dal client: i primi valori sono quindi falsificabili.
 * Per questo l'elenco si legge da destra, saltando gli indirizzi dell'infrastruttura fidata; il primo indirizzo
 * rimasto è il client. Misure reali (fase 8): diretto
 * {@code [falsi], client, 172.x (Cloudflare), 10.x (Render)}; dal sito
 * {@code [falsi], client, 162.158.x, 162.158.x, 74.220.48.x (Render), 172.x, 10.x}.
 *
 * <p>Gli header si considerano solo se la connessione arriva da un indirizzo fidato (in produzione il proxy di
 * Render si collega dalla rete locale): un client che si collega direttamente non può impostarli.
 */
public class ClientIpFilter extends OncePerRequestFilter {

    /**
     * Indirizzi dell'infrastruttura. Intervalli Cloudflare da https://www.cloudflare.com/ips (ottobre 2026);
     * 74.220.48.0/20 è assegnato a Render (registro ARIN). Da aggiornare se i fornitori cambiano i loro indirizzi.
     */
    static final List<Cidr> TRUSTED_PROXIES = List.of(
            // loopback e reti private (proxy interno di Render, sviluppo locale)
            Cidr.parse("127.0.0.0/8"), Cidr.parse("10.0.0.0/8"), Cidr.parse("172.16.0.0/12"),
            Cidr.parse("192.168.0.0/16"), Cidr.parse("100.64.0.0/10"), Cidr.parse("::1/128"),
            Cidr.parse("fc00::/7"),
            // Render
            Cidr.parse("74.220.48.0/20"),
            // Cloudflare IPv4
            Cidr.parse("173.245.48.0/20"), Cidr.parse("103.21.244.0/22"), Cidr.parse("103.22.200.0/22"),
            Cidr.parse("103.31.4.0/22"), Cidr.parse("141.101.64.0/18"), Cidr.parse("108.162.192.0/18"),
            Cidr.parse("190.93.240.0/20"), Cidr.parse("188.114.96.0/20"), Cidr.parse("197.234.240.0/22"),
            Cidr.parse("198.41.128.0/17"), Cidr.parse("162.158.0.0/15"), Cidr.parse("104.16.0.0/13"),
            Cidr.parse("104.24.0.0/14"), Cidr.parse("172.64.0.0/13"), Cidr.parse("131.0.72.0/22"),
            // Cloudflare IPv6
            Cidr.parse("2400:cb00::/32"), Cidr.parse("2606:4700::/32"), Cidr.parse("2803:f800::/32"),
            Cidr.parse("2405:b500::/32"), Cidr.parse("2405:8100::/32"), Cidr.parse("2a06:98c0::/29"),
            Cidr.parse("2c0f:f248::/32"));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String socket = request.getRemoteAddr();
        if (!isTrusted(socket)) {
            chain.doFilter(request, response);
            return;
        }
        String clientIp = resolve(socket, Collections.list(request.getHeaders("X-Forwarded-For")));
        boolean https = "https".equalsIgnoreCase(firstValue(request.getHeader("X-Forwarded-Proto")));
        chain.doFilter(new ForwardedRequest(request, clientIp, https), response);
    }

    /** IP del client: il primo indirizzo da destra non appartenente all'infrastruttura (o il socket, se non ce ne sono). */
    static String resolve(String socketAddress, List<String> forwardedForHeaders) {
        List<String> chain = new ArrayList<>();
        for (String header : forwardedForHeaders) {
            for (String part : header.split(",")) {
                if (!part.isBlank()) {
                    chain.add(part.trim());
                }
            }
        }
        String candidate = socketAddress;
        for (int i = chain.size() - 1; i >= 0; i--) {
            String address = chain.get(i);
            if (!isTrusted(address)) {
                return isIpLiteral(address) ? address : candidate;
            }
            candidate = address;
        }
        // Tutti gli indirizzi sono dell'infrastruttura: si usa il più a sinistra (il più vicino al client).
        return candidate;
    }

    static boolean isTrusted(String address) {
        InetAddress ip = toInetAddress(address);
        return ip != null && TRUSTED_PROXIES.stream().anyMatch(cidr -> cidr.contains(ip));
    }

    private static boolean isIpLiteral(String address) {
        return toInetAddress(address) != null;
    }

    /** Solo indirizzi IP letterali: un nome host non viene mai risolto (nessuna richiesta DNS). */
    private static InetAddress toInetAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String value = address.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        boolean ipv4 = value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        boolean ipv6 = value.contains(":") && value.matches("[0-9a-fA-F:.%]+");
        if (!ipv4 && !ipv6) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static String firstValue(String header) {
        return header == null ? null : header.split(",")[0].trim().toLowerCase(Locale.ROOT);
    }

    /** Intervallo di indirizzi in notazione CIDR (IPv4 o IPv6). */
    record Cidr(byte[] network, int prefixLength) {

        static Cidr parse(String cidr) {
            String[] parts = cidr.split("/");
            InetAddress address = toInetAddress(parts[0]);
            if (address == null) {
                throw new IllegalArgumentException("CIDR non valido: " + cidr);
            }
            return new Cidr(address.getAddress(), Integer.parseInt(parts[1]));
        }

        boolean contains(InetAddress ip) {
            byte[] bytes = ip.getAddress();
            if (bytes.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (bytes[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (bytes[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    /** Richiesta con IP del client e schema ricavati dagli header dei proxy fidati. */
    private static final class ForwardedRequest extends HttpServletRequestWrapper {

        private final String clientIp;
        private final boolean https;

        private ForwardedRequest(HttpServletRequest request, String clientIp, boolean https) {
            super(request);
            this.clientIp = clientIp;
            this.https = https;
        }

        @Override
        public String getRemoteAddr() {
            return clientIp;
        }

        @Override
        public String getRemoteHost() {
            return clientIp;
        }

        @Override
        public boolean isSecure() {
            return https || super.isSecure();
        }

        @Override
        public String getScheme() {
            return https ? "https" : super.getScheme();
        }
    }
}
