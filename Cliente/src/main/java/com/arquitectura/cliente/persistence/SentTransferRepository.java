package com.arquitectura.cliente.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SentTransferRepository extends JpaRepository<SentTransferEntity, Long> {
    List<SentTransferEntity> findByServerHostAndServerPortOrderBySentAtDesc(String host, int port);
    List<SentTransferEntity> findByStatus(String status);
}

