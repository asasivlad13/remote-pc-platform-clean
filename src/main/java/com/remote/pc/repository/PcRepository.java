package com.remote.pc.repository;

import com.remote.pc.model.Pc;
import com.remote.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PcRepository extends JpaRepository<Pc, Long> {
    List<Pc> findByUser(User user);

    Pc findByMacAddress(String macAddress);
}