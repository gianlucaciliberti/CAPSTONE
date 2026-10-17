-- Inizializzazione dei database LOCALI (sviluppo e test). NON usare in produzione.
-- Eseguire come superuser "postgres" (pgAdmin > Query Tool sul database "postgres", oppure psql).
-- Sostituire CAMBIA_QUESTA_PASSWORD con una password a scelta e riportarla in DB_PASSWORD nel file .env.
--
-- Nota: CREATE DATABASE non può stare in una transazione. In pgAdmin eseguire lo script con
-- "Execute script" (F5) e l'auto-commit attivo (impostazione predefinita).

CREATE ROLE squadra WITH LOGIN PASSWORD 'CAMBIA_QUESTA_PASSWORD';

-- Database di sviluppo: usato dall'applicazione avviata in locale (profilo dev).
CREATE DATABASE squadra_dev OWNER squadra ENCODING 'UTF8' TEMPLATE template0;

-- Database dei test di integrazione: separato, i test non toccano mai i dati di sviluppo.
CREATE DATABASE squadra_test OWNER squadra ENCODING 'UTF8' TEMPLATE template0;
