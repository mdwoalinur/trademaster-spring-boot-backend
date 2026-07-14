package com.trademaster.ims.service;

import com.trademaster.ims.model.Warehouse;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class WarehouseService {

    @Autowired
    private WarehouseRepository warehouseRepository;

    public List<Warehouse> findAll() {
        return warehouseRepository.findAll();
    }

    public Optional<Warehouse> findById(Long id) {
        return warehouseRepository.findById(id);
    }

    public Warehouse getWarehouseById(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + id));
    }

    @Auditable(action = "CREATE", entityType = "Warehouse")
    public Warehouse save(Warehouse warehouse) {
        return warehouseRepository.save(warehouse);
    }

    @Auditable(action = "UPDATE", entityType = "Warehouse")
    public Warehouse update(Long id, Warehouse warehouseDetails) {
        Warehouse existing = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + id));

        existing.setWarehouseCode(warehouseDetails.getWarehouseCode());
        existing.setName(warehouseDetails.getName());
        existing.setLocation(warehouseDetails.getLocation());
        existing.setStatus(warehouseDetails.getStatus());
        existing.setCapacity(warehouseDetails.getCapacity());
        existing.setManagerName(warehouseDetails.getManagerName());
        existing.setContactPhone(warehouseDetails.getContactPhone());
        existing.setContactEmail(warehouseDetails.getContactEmail());

        return warehouseRepository.save(existing);
    }

    @Auditable(action = "DELETE", entityType = "Warehouse")
    public void deleteById(Long id) {
        if (!warehouseRepository.existsById(id)) {
            throw new RuntimeException("Warehouse not found with id: " + id);
        }
        warehouseRepository.deleteById(id);
    }

    public boolean existsById(Long id) {
        return warehouseRepository.existsById(id);
    }
}
