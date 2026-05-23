package com.remote.repository;

import com.remote.model.SupportChatMessage;
import com.remote.model.SupportSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportChatMessageRepository extends JpaRepository<SupportChatMessage, Long> {

    @EntityGraph(attributePaths = {"sender"})
    List<SupportChatMessage> findBySupportSessionOrderByCreatedAt(SupportSession supportSession);
}
