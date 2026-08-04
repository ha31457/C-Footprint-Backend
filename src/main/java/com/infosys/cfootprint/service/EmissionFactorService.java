package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.CreateEmissionFactorRequest;
import com.infosys.cfootprint.dto.UpdateEmissionFactorRequest;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.EmissionFactor;
import com.infosys.cfootprint.repository.EmissionFactorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EmissionFactorService {

    @Autowired
    private EmissionFactorRepository emissionFactorRepository;

    public List<EmissionFactor> getAllFactors() {
        return emissionFactorRepository.findAll();
    }

    @Transactional
    public EmissionFactor createFactor(CreateEmissionFactorRequest request) {
        if (emissionFactorRepository.findByCategoryAndActivityType(
                request.getCategory().toLowerCase(),
                request.getActivityType()
        ).isPresent()) {
            throw new BadRequestException("Emission factor already exists for category: " 
                    + request.getCategory() + " and type: " + request.getActivityType());
        }

        EmissionFactor factor = EmissionFactor.builder()
                .category(request.getCategory().toLowerCase())
                .activityType(request.getActivityType())
                .factor(request.getFactor())
                .unit(request.getUnit())
                .build();

        return emissionFactorRepository.save(factor);
    }

    @Transactional
    public EmissionFactor updateFactor(UUID id, UpdateEmissionFactorRequest request) {
        EmissionFactor factor = emissionFactorRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Emission factor not found with ID: " + id));

        factor.setFactor(request.getFactor());
        factor.setUnit(request.getUnit());

        return emissionFactorRepository.save(factor);
    }

    @Transactional
    public void deleteFactor(UUID id) {
        if (!emissionFactorRepository.existsById(id)) {
            throw new BadRequestException("Emission factor not found with ID: " + id);
        }
        emissionFactorRepository.deleteById(id);
    }
}
