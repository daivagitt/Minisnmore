package com.miniandmore.app.service;

import com.miniandmore.app.domain.PackageBox;
import com.miniandmore.app.repository.PackageBoxRepository;
import com.miniandmore.app.service.dto.PackageBoxDTO;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PackageBoxService {

    private final PackageBoxRepository packageBoxRepository;

    public PackageBoxService(PackageBoxRepository packageBoxRepository) {
        this.packageBoxRepository = packageBoxRepository;
    }

    @Transactional(readOnly = true)
    public List<PackageBoxDTO> findAllByName() {
        return packageBoxRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    public PackageBoxDTO save(PackageBoxDTO dto) {
        dto.setId(null);
        return persist(dto);
    }

    public PackageBoxDTO update(PackageBoxDTO dto) {
        if (dto.getId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package type not found");
        }
        if (!packageBoxRepository.existsById(dto.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package type not found");
        }
        return persist(dto);
    }

    public void delete(Long id) {
        if (id == null || !packageBoxRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package type not found");
        }
        packageBoxRepository.deleteById(id);
    }

    private PackageBoxDTO persist(PackageBoxDTO dto) {
        normalize(dto);
        packageBoxRepository
            .findFirstByNameIgnoreCase(dto.getName())
            .filter(existing -> dto.getId() == null || !existing.getId().equals(dto.getId()))
            .ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A package type with this name already exists");
            });
        PackageBox entity = dto.getId() == null
            ? new PackageBox()
            : packageBoxRepository
                .findById(dto.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Package type not found"));
        entity.setName(dto.getName());
        entity.setLengthCm(dto.getLengthCm());
        entity.setBreadthCm(dto.getBreadthCm());
        entity.setHeightCm(dto.getHeightCm());
        entity.setWeightGrams(dto.getWeightGrams() != null && dto.getWeightGrams() >= 1 ? dto.getWeightGrams() : null);
        return toDto(packageBoxRepository.save(entity));
    }

    private static void normalize(PackageBoxDTO dto) {
        dto.setName(StringUtils.trimToEmpty(dto.getName()));
        if (StringUtils.isBlank(dto.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        requirePositive(dto.getLengthCm(), "Length");
        requirePositive(dto.getBreadthCm(), "Width");
        requirePositive(dto.getHeightCm(), "Height");
    }

    private static void requirePositive(Integer centimetres, String label) {
        if (centimetres == null || centimetres < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be at least 1 cm");
        }
    }

    private PackageBoxDTO toDto(PackageBox entity) {
        PackageBoxDTO dto = new PackageBoxDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setLengthCm(entity.getLengthCm());
        dto.setBreadthCm(entity.getBreadthCm());
        dto.setHeightCm(entity.getHeightCm());
        dto.setWeightGrams(entity.getWeightGrams());
        return dto;
    }
}
