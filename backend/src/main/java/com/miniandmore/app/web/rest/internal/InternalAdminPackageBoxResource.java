package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.PackageBoxService;
import com.miniandmore.app.service.dto.PackageBoxDTO;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/admin/package-boxes")
public class InternalAdminPackageBoxResource {

    private static final String PACKING_READ =
        "hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_OPERATIONS')";
    private static final String CMS_WRITE = "hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')";

    private final PackageBoxService packageBoxService;

    public InternalAdminPackageBoxResource(PackageBoxService packageBoxService) {
        this.packageBoxService = packageBoxService;
    }

    @GetMapping
    @PreAuthorize(PACKING_READ)
    public ApiResponse<List<PackageBoxDTO>> list() {
        return ApiResponse.ok(packageBoxService.findAllByName());
    }

    @PostMapping
    @PreAuthorize(CMS_WRITE)
    public ApiResponse<PackageBoxDTO> create(@Valid @RequestBody PackageBoxDTO dto) {
        return ApiResponse.ok(packageBoxService.save(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CMS_WRITE)
    public ApiResponse<PackageBoxDTO> update(@PathVariable Long id, @Valid @RequestBody PackageBoxDTO dto) {
        dto.setId(id);
        return ApiResponse.ok(packageBoxService.update(dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CMS_WRITE)
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id) {
        packageBoxService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
