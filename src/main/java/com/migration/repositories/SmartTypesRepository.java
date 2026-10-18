package com.migration.repositories;

import com.migration.entities.SmartTypesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SmartTypesRepository extends JpaRepository<SmartTypesEntity, String> {
    List<SmartTypesEntity> findBySmartTypeIn(Collection<String> smartTypes);
}
