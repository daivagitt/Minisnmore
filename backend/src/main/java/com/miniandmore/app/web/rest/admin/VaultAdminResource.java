package com.miniandmore.app.web.rest.admin;

import com.miniandmore.app.service.VaultSecretService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/vault")
public class VaultAdminResource {

    private final VaultSecretService vaultSecretService;

    public VaultAdminResource(VaultSecretService vaultSecretService) {
        this.vaultSecretService = vaultSecretService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return vaultSecretService.listAdminView();
    }

    @PutMapping("/{name}")
    public Map<String, Object> put(@PathVariable String name, @RequestBody Map<String, String> body) {
        String value = body != null ? body.get("value") : null;
        vaultSecretService.putSecret(name, value, "admin");
        return Map.of("ok", true);
    }

    @DeleteMapping("/{name}")
    public Map<String, Object> delete(@PathVariable String name) {
        vaultSecretService.deleteSecret(name);
        return Map.of("ok", true);
    }

    @PostMapping("/import")
    public Map<String, Object> importSecrets(@RequestBody Map<String, String> body) {
        int count = vaultSecretService.importSecrets(body, "admin-import");
        return Map.of("ok", true, "imported", count);
    }

    @PostMapping("/rotate-master-key")
    public Map<String, Object> rotateMasterKey(@RequestBody Map<String, String> body) {
        String newKey = body != null ? body.get("newMasterKey") : null;
        return vaultSecretService.rotateMasterKey(newKey);
    }
}
