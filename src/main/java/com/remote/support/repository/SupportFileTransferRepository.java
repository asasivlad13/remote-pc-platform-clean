package com.remote.support.repository;

import com.remote.support.model.SupportFileTransfer;
import com.remote.support.model.SupportSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportFileTransferRepository extends JpaRepository<SupportFileTransfer, Long> {

    @EntityGraph(attributePaths = {"sender", "recipient"})
    List<SupportFileTransfer> findBySupportSessionOrderByCreatedAtDesc(SupportSession supportSession);
}
