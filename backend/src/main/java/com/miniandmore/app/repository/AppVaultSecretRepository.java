package com.miniandmore.app.repository;

import com.miniandmore.app.domain.AppVaultSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppVaultSecretRepository extends JpaRepository<AppVaultSecret, String> {}
