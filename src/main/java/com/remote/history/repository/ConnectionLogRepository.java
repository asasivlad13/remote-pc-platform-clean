package com.remote.history.repository;

import com.remote.history.model.ConnectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectionLogRepository extends JpaRepository<ConnectionLog, Long> {

    List<ConnectionLog> findByUsernameOrderByTimestampDesc(String username);

    Optional<ConnectionLog> findFirstByUsernameAndPcNameAndDisconnectedAtIsNullOrderByTimestampDesc(String username,
                                                                                                    String pcName);

    Optional<ConnectionLog> findFirstByUsernameAndDisconnectedAtIsNullOrderByTimestampDesc(String username);
}