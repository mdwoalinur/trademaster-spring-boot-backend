package com.trademaster.ims.service;

import com.trademaster.ims.model.Unit;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.UnitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UnitService {

    @Autowired
    private UnitRepository unitRepository;

    public List<Unit> getAllUnits() {
        return unitRepository.findAll();
    }

    public Unit getUnitById(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Unit not found with id: " + id));
    }

    public List<Unit> getBaseUnits() {
        return unitRepository.findByIsBase(true);
    }

    public List<Unit> getUnitsByType(Unit.UnitType unitType) {
        return unitRepository.findByUnitType(unitType);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "Unit")
    public Unit createUnit(Unit unit) {
        unit.setUnitId(null); // ensure new record
        return unitRepository.save(unit);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Unit")
    public Unit updateUnit(Long id, Unit unitDetails) {
        Unit existing = getUnitById(id);
        existing.setUnitName(unitDetails.getUnitName());
        existing.setUnitCode(unitDetails.getUnitCode());
        existing.setUnitType(unitDetails.getUnitType());
        existing.setBaseUnitId(unitDetails.getBaseUnitId());
        existing.setConversionFactor(unitDetails.getConversionFactor());
        existing.setIsBase(unitDetails.getIsBase());
        existing.setStatus(unitDetails.getStatus());
        return unitRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "Unit")
    public void deleteUnit(Long id) {
        unitRepository.deleteById(id);
    }
}
