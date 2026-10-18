package it.squadra.gestionale.services;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Utente non trovato."));
    }
}
