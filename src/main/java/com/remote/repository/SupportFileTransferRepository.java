package com.remote.repository;

import com.remote.model.SupportFileTransfer;
import com.remote.model.SupportSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportFileTransferRepository extends JpaRepository<SupportFileTransfer, Long> {

    @EntityGraph(attributePaths = {"sender", "recipient"})
    List<SupportFileTransfer> findBySupportSessionOrderByCreatedAtDesc(SupportSession supportSession);
}
